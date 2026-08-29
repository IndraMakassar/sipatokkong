-- =====================================================================
-- Sipatokkong — Phase 6: device registry for push notifications
--
-- `notifications` already holds every notification this app produces; the
-- only thing missing is somewhere to send them. One row per app
-- installation, so an Edge Function can look up "which devices belong to
-- this recipient" when a notification row is written.
--
-- Push is a delivery channel for rows that already exist, not a second
-- notification system. If nothing in here works, the in-app inbox is
-- exactly as correct as it is today.
-- =====================================================================

-- ------------------------------------------------------------------
-- 1. Table
-- ------------------------------------------------------------------

-- One row per app installation that has agreed to receive notifications.
create table public.push_tokens (
    token      text primary key,
    user_id    uuid not null references public.profiles (id) on delete cascade,
    platform   text not null default 'android',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- The primary key is `token` ALONE, and that is the load-bearing decision here.
--
-- An FCM registration token identifies an app *installation*, not a person, so two users can never
-- legitimately hold the same token at once. The foundation's shared phone is the case that settles
-- it: with `token` as the key, the next person to sign in STEALS the row (see
-- `register_push_token` below), and it stops mattering whether the previous user's sign-out ran at
-- all -- offline, force-killed, crashed, it self-heals.
--
-- Keyed on (user_id, token) instead, a failed sign-out leaves both rows live and the next
-- notification for the previous user lands on the phone the new user is now holding. For this app
-- that is another family's child's therapy notes on a stranger's screen. A leak, not an annoyance.
--
-- `updated_at` is here for a future staleness sweep: FCM retires tokens after roughly 270 days of
-- app inactivity. Nothing sweeps yet.

-- Postgres does NOT auto-index FKs, and `where user_id = $1` is the only query the sender makes.
-- The primary-key index on `token` cannot serve it.
create index idx_push_tokens_user on public.push_tokens (user_id);

-- ------------------------------------------------------------------
-- 2. Write path (SECURITY DEFINER — see why below)
-- ------------------------------------------------------------------

-- Claims this device for the signed-in user, taking it from whoever held it before.
--
-- This CANNOT be expressed as an RLS policy, which is why it is a function. PostgREST's
-- `resolution=merge-duplicates` upsert needs the UPDATE policy's `using (...)` to admit the row
-- that already EXISTS, and `user_id = auth.uid()` is false for precisely the row being taken over.
-- The only policy that would permit the steal is `using (true)` -- which would let any authenticated
-- user reassign any token they could guess, and tokens are guessable in the sense that they leak
-- through any shared logcat.
--
-- `user_id` is `auth.uid()` and never a parameter, so a caller cannot register a device to someone
-- else even by trying.
create or replace function public.register_push_token(p_token text)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
    if auth.uid() is null or coalesce(trim(p_token), '') = '' then
        return;
    end if;
    insert into public.push_tokens (token, user_id)
    values (p_token, auth.uid())
    on conflict (token) do update
        set user_id    = excluded.user_id,
            updated_at = now();
end $$;

-- Releases this device on sign-out. The `user_id = auth.uid()` clause keeps it to your own
-- registration; the steal above is what covers the case where this never got to run.
create or replace function public.unregister_push_token(p_token text)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
    delete from public.push_tokens
    where token = p_token and user_id = auth.uid();
end $$;

-- Both functions WRITE, unlike the read-only helpers in 0001, so they are locked to signed-in
-- callers explicitly rather than relying on the default grant to public.
revoke execute on function public.register_push_token(text) from public;
revoke execute on function public.unregister_push_token(text) from public;
grant execute on function public.register_push_token(text) to authenticated;
grant execute on function public.unregister_push_token(text) to authenticated;

-- ------------------------------------------------------------------
-- 3. RLS
-- ------------------------------------------------------------------
alter table public.push_tokens enable row level security;

-- ---- push_tokens ---- (no client INSERT/UPDATE/DELETE policies; the two SECURITY DEFINER
-- functions above are the write path, because the upsert-steal cannot be written as a policy)
--
-- SELECT exists only so the device can confirm its own registration -- it returns a token the
-- device already holds, so it discloses nothing. The sender is an Edge Function using the service
-- role, which bypasses this entirely.
create policy push_token_select on public.push_tokens
    for select to authenticated
    using (user_id = auth.uid());
