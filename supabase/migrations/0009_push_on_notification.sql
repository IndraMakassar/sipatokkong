-- =====================================================================
-- Sipatokkong — Phase 6: hand every new notification row to the push sender
--
-- Postgres cannot call HTTPS by itself, so this is the hop out of the
-- database: an AFTER INSERT trigger on `notifications` posts the row to
-- the `push-notify` Edge Function, which talks to FCM.
--
-- ONE trigger on `notifications`, deliberately, rather than edits to the
-- three fan-out triggers in 0001. It fires once per row -- including each
-- of the N rows `on_feedback_insert` writes for N assigned volunteers,
-- which is exactly the right granularity -- and any future producer (a
-- `tugas_reminder` cron, say) gets push for free without touching this
-- file.
-- =====================================================================

create extension if not exists pg_net with schema extensions;

-- Posts one notification row to the push sender.
--
-- `net.http_post` is ASYNCHRONOUS: it queues into `net.http_request_queue` and returns immediately,
-- and a background worker does the sending. That is the property that makes calling out to the
-- network from a trigger acceptable at all -- a dead Edge Function, an expired key or a slow FCM
-- cannot make creating a program ajar fail, and the parent's INSERT never waits on a round trip.
--
-- The flip side: failures are invisible from the app. Responses land in `net._http_response`
-- (retained ~6 hours) and that table is the place to start when a push never arrives.
--
-- WARNING for whoever adds a backfill later: this fires on INSERT, so a cron or migration that
-- inserts historical notification rows will send one push per row. Deleting rows is safe; inserting
-- them is a push storm.
create or replace function public.on_notification_push()
returns trigger
language plpgsql security definer set search_path = ''
as $$
declare
    auth_key    text;
    hook_secret text;
begin
    select decrypted_secret into auth_key
      from vault.decrypted_secrets where name = 'push_hook_auth';
    select decrypted_secret into hook_secret
      from vault.decrypted_secrets where name = 'push_hook_secret';

    -- Push not configured yet, or the secrets are unreadable. The inbox row has already been
    -- written and still stands, so the app is unaffected -- which is what lets this migration be
    -- applied before the Edge Function is deployed.
    --
    -- If pushes never fire, check this first: `vault.decrypted_secrets` is readable only by
    -- `postgres`/`supabase_admin`. Owned by a lesser role, the selects above return nothing and
    -- this guard swallows every notification in silence. `\df+ public.on_notification_push`.
    if auth_key is null then
        return new;
    end if;

    perform net.http_post(
        url     := 'https://pqtqyafuredxenvyxypb.supabase.co/functions/v1/push-notify',
        headers := jsonb_build_object(
            'Content-Type',  'application/json',
            'Authorization', 'Bearer ' || auth_key,
            'x-push-secret', hook_secret
        ),
        body    := jsonb_build_object(
            'notification_id', new.id,
            'recipient_id',    new.recipient_id,
            'type',            new.type,
            'title',           new.title,
            'body',            new.body,
            'entity_table',    new.entity_table,
            'entity_id',       new.entity_id
        ),
        timeout_milliseconds := 5000
    );
    return new;
end $$;

create trigger trg_notification_push
    after insert on public.notifications
    for each row execute function public.on_notification_push();

comment on function public.on_notification_push is
    'Queues one FCM push per notification row via pg_net. Async and best-effort: the in-app inbox
     remains the source of truth, and a failure here is invisible to the client by design.';

-- ---------------------------------------------------------------------
-- Run ONCE, by hand, and never in this file:
--
--   select vault.create_secret('<service_role JWT>',        'push_hook_auth');
--   select vault.create_secret('<32 random bytes, base64>', 'push_hook_secret');
--
-- `push_hook_auth` is the service-role key and bypasses RLS on the entire database. Keeping both
-- in Vault rather than in migration text is the whole reason a trigger is an acceptable place to
-- put this. `push_hook_secret` must match the `PUSH_HOOK_SECRET` function secret.
-- ---------------------------------------------------------------------
