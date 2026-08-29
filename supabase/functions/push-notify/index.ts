// =====================================================================
// push-notify — turns one `public.notifications` row into FCM messages
//
// Called only by the `trg_notification_push` trigger (see migration 0009).
// Never by the app: the anon key ships inside the APK and every parent
// holds a valid user JWT, so a function that accepted those would be an
// open push relay any user could aim at any other user with arbitrary
// text. The content here is always trigger-authored.
// =====================================================================

import { createClient } from 'jsr:@supabase/supabase-js@2'

const FCM_PROJECT_ID = 'sipatokkong-885ed'
const FCM_ENDPOINT = `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token'
const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging'

interface NotificationRow {
  notification_id?: string
  recipient_id?: string
  type?: string
  title?: string | null
  body?: string | null
  entity_table?: string | null
  entity_id?: string | null
}

// ---- auth -----------------------------------------------------------

/** Constant-time compare, so a wrong secret cannot be discovered a byte at a time by timing. */
function secretsMatch(a: string, b: string): boolean {
  if (a.length !== b.length) return false
  let diff = 0
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i)
  return diff === 0
}

/**
 * Two independent gates, because either one alone is not enough.
 *
 * The platform has already verified the JWT signature (`verify_jwt` is left on), so decoding the
 * payload here is only about *which* key was used — a user JWT must not be able to drive this.
 * The `x-push-secret` header is the belt: it survives someone later redeploying with
 * `--no-verify-jwt`, which would otherwise silently open the function to the world.
 */
function authorize(req: Request): string | null {
  const auth = req.headers.get('Authorization') ?? ''
  const jwt = auth.startsWith('Bearer ') ? auth.slice(7) : ''
  if (!jwt) return 'missing bearer'

  try {
    const payload = JSON.parse(atob(jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    if (payload.role !== 'service_role') return 'not service_role'
  } catch {
    return 'unparseable jwt'
  }

  const expected = Deno.env.get('PUSH_HOOK_SECRET') ?? ''
  if (!expected) return 'PUSH_HOOK_SECRET not set'
  if (!secretsMatch(req.headers.get('x-push-secret') ?? '', expected)) return 'bad hook secret'

  return null
}

// ---- OAuth2 ---------------------------------------------------------

/**
 * base64url. Only ever applied to the JWT header and claims, both pure ASCII.
 *
 * `btoa` throws on any code point above 0xFF, so this must never touch the message body — a
 * parent's note is Indonesian free text and will eventually contain something outside Latin-1.
 */
function b64url(s: string): string {
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

let cachedToken: { value: string; expiresAt: number } | null = null

/**
 * Mints a Google access token from the service account.
 *
 * Cached in module scope: Edge Function isolates survive between invocations, so a fan-out to
 * several volunteers mints one token rather than one per send. Sixty seconds of slack so a token
 * cannot expire mid-flight.
 */
async function accessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value

  const raw = Deno.env.get('FCM_SERVICE_ACCOUNT')
  if (!raw) throw new Error('FCM_SERVICE_ACCOUNT not set')
  const sa = JSON.parse(raw)

  // The PEM is PKCS#8, which is what importKey('pkcs8') wants. Strip the armour, then strip BOTH
  // real newlines and literal backslash-n — a secret set through a shell often carries the latter.
  const der = Uint8Array.from(
    atob(
      sa.private_key
        .replace(/-----[^-]+-----/g, '')
        .replace(/\\n/g, '')
        .replace(/\s+/g, ''),
    ),
    (c: string) => c.charCodeAt(0),
  )
  const key = await crypto.subtle.importKey(
    'pkcs8',
    der,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  )

  const claims = {
    iss: sa.client_email,
    scope: SCOPE,
    aud: TOKEN_ENDPOINT,
    iat: now,
    exp: now + 3600,
  }
  const unsigned = `${b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${b64url(JSON.stringify(claims))}`
  const sig = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(unsigned),
  )
  const assertion = `${unsigned}.${b64url(String.fromCharCode(...new Uint8Array(sig)))}`

  const res = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  })
  if (!res.ok) throw new Error(`token endpoint ${res.status}`)

  const json = await res.json()
  cachedToken = { value: json.access_token, expiresAt: now + (json.expires_in ?? 3600) }
  return cachedToken.value
}

// ---- sending --------------------------------------------------------

/** FCM rejects the whole message if any `data` value is null or not a string, so absent keys go. */
function dataPayload(row: NotificationRow): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(row)) {
    if (v !== null && v !== undefined && v !== '') out[k] = String(v)
  }
  return out
}

type SendOutcome = { ok: boolean; prune: boolean }

/**
 * Which failures mean "this token is dead" and which mean "try again later".
 *
 * The INVALID_ARGUMENT rule is the important one and it is deliberately narrow. FCM returns that
 * code for a malformed *message* too, so a single coding mistake on our side would return it for
 * EVERY token — and a naive prune would empty the table in one request, silently disabling push
 * for everyone, with nothing anywhere that looks like an error. Only a field violation naming
 * `message.token` is about the token.
 */
function shouldPrune(status: number, body: unknown): boolean {
  const err = (body as { error?: { status?: string; details?: unknown[] } })?.error
  const code = err?.status ?? ''
  if (status === 404 || code === 'NOT_FOUND' || code === 'UNREGISTERED') return true
  if (status === 403 || code === 'SENDER_ID_MISMATCH') return true
  if (status === 400 || code === 'INVALID_ARGUMENT') {
    const details = (err?.details ?? []) as Array<{ fieldViolations?: Array<{ field?: string }> }>
    return details.some((d) => d.fieldViolations?.some((v) => v.field === 'message.token'))
  }
  return false
}

async function sendTo(token: string, data: Record<string, string>, bearer: string): Promise<SendOutcome> {
  const res = await fetch(FCM_ENDPOINT, {
    method: 'POST',
    headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: {
        token,
        data,
        // Mandatory, not tuning. A normal-priority data message is held by Doze until the next
        // maintenance window, which can be hours — a therapy reminder arriving tomorrow is the
        // failure this feature exists to prevent.
        android: { priority: 'HIGH' },
      },
    }),
  })
  if (res.ok) return { ok: true, prune: false }

  const body = await res.json().catch(() => ({}))
  return { ok: false, prune: shouldPrune(res.status, body) }
}

// ---- entry point ----------------------------------------------------

Deno.serve(async (req: Request) => {
  const denied = authorize(req)
  if (denied) {
    console.error(`push-notify: rejected (${denied})`)
    return new Response('unauthorized', { status: 401 })
  }

  try {
    const row = (await req.json()) as NotificationRow
    if (!row.recipient_id) return Response.json({ sent: 0, failed: 0, pruned: 0 })

    const admin = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
    )
    const { data: rows, error } = await admin
      .from('push_tokens')
      .select('token')
      .eq('user_id', row.recipient_id)
    if (error) throw new Error(`token lookup: ${error.message}`)

    const tokens = (rows ?? []).map((r: { token: string }) => r.token)
    if (tokens.length === 0) return Response.json({ sent: 0, failed: 0, pruned: 0 })

    const bearer = await accessToken()
    const data = dataPayload(row)
    const results = await Promise.allSettled(tokens.map((t) => sendTo(t, data, bearer)))

    const dead: string[] = []
    let sent = 0
    results.forEach((r, i) => {
      if (r.status !== 'fulfilled') return
      if (r.value.ok) sent++
      else if (r.value.prune) dead.push(tokens[i])
    })

    // Second belt on the same footgun: if a bug ever made every token look dead, refuse to act on
    // it. Losing one stale row is routine; losing the whole registry is unrecoverable and silent.
    let pruned = 0
    if (dead.length > 0 && dead.length <= Math.max(1, Math.floor(tokens.length / 2))) {
      await admin.from('push_tokens').delete().in('token', dead)
      pruned = dead.length
    } else if (dead.length > 0) {
      console.error(
        `push-notify: refusing to prune ${dead.length}/${tokens.length} tokens in one call — ` +
          'this looks like a malformed message, not dead devices',
      )
    }

    return Response.json({ sent, failed: tokens.length - sent, pruned })
  } catch (e) {
    // Message only. Never the service account, the minted token, or full request headers.
    console.error(`push-notify: ${(e as Error).message}`)
    // Deliberately 200: pg_net is fire-and-forget and nothing retries usefully here, so a non-200
    // would only add noise to net._http_response.
    return Response.json({ sent: 0, failed: 0, pruned: 0, error: (e as Error).message })
  }
})
