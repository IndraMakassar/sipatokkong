-- =====================================================================
-- Sipatokkong -- 0012: the morning reminder for a task due today
--
-- `notif_type` has declared 'tugas_reminder' since 0001 and nothing has
-- ever written it. A parent is told once, when the volunteer creates a
-- programme, and then never again -- so a task assigned on Monday for
-- every weekday is announced once and silently expected six more times.
--
-- This adds one daily job: for every task with an occurrence today that
-- has not been completed, write one notification to the child's parent.
-- The existing `on_notification_push` trigger (0009) turns that row into
-- an FCM push with no extra work, and `NotifDisplay.targetOf` already
-- deep-links it, because the row points at 'tugas' like any other.
--
-- REQUIRES 0010: `notif_snippet` is defined there.
--
-- Deliberately NOT included: 'tugas_missed'. A second job telling a
-- parent they failed yesterday is a different product decision from a
-- reminder, and it is the kind that makes people turn notifications off.
-- =====================================================================


-- ------------------------------------------------------------------
-- 1. Does this task fall on this day?
-- ------------------------------------------------------------------

-- The one piece of duplicated logic in this migration, and worth naming as such:
-- `RecurrenceRule` in `core/recurrence/Recurrence.kt` already answers this, in Kotlin, with 16
-- unit tests behind it. This is a second implementation and the two can drift.
--
-- It is still the right trade, because the two are not the same size. The Kotlin has to ENUMERATE
-- a range -- walk every day, clamp short months, anchor an interval so the cadence never slips --
-- and that is where its complexity lives. A cron job only ever asks about ONE day, which collapses
-- all of that into a per-type predicate. Fifteen lines against a hundred.
--
-- Every branch below is a line-for-line mirror of a specific piece of `Recurrence.kt`, named in the
-- comments so the two can be compared without guessing. If you change one, change both.
create or replace function public.tugas_jatuh_pada(t public.tugas, hari date)
returns boolean
language sql
immutable
set search_path = ''
as $$
    with batas as (
        select
            -- Mirrors Task.toRecurrenceRule(): a ONCE task anchors on its deadline, everything
            -- else on starts_on, and both fall back through the other two columns. The row may
            -- predate 0002, which is why created_at is the last resort rather than an error.
            case
                when t.recurrence = 'once'
                    then coalesce(t.deadline, t.starts_on, t.created_at::date)
                else coalesce(t.starts_on, t.deadline, t.created_at::date)
            end as mulai,
            -- Mirrors RecurrenceRule.effectiveEnd: ends_on is inclusive, stopped_at is EXCLUSIVE,
            -- so the last day a stopped programme can still produce is the day before it stopped.
            -- least() ignores nulls in Postgres, which is exactly the "no limit from that side"
            -- behaviour the Kotlin gets from its when/else chain.
            least(t.ends_on, (t.stopped_at::date - 1)) as akhir
    )
    select
        hari >= b.mulai
        and (b.akhir is null or hari <= b.akhir)
        and case t.recurrence
            when 'once'  then hari = b.mulai
            when 'daily' then true

            -- ISO day-of-week, 1 = Monday, matching the smallint[] the form writes.
            when 'weekly' then
                coalesce(array_length(t.recurrence_days, 1), 0) > 0
                and extract(isodow from hari)::smallint = any (t.recurrence_days)

            -- Mirrors monthlyOccurrences: the 31st in a 30-day month CLAMPS to the last day
            -- rather than skipping, because a therapy programme must not lose a whole month.
            when 'monthly' then
                t.recurrence_day_of_month between 1 and 31
                and extract(day from hari)::int = least(
                    t.recurrence_day_of_month::int,
                    extract(day from (date_trunc('month', hari::timestamp)
                                      + interval '1 month - 1 day'))::int
                )

            -- Counted from `mulai` so the cadence never drifts, exactly as the Kotlin does.
            -- Written as CASE rather than `interval >= 1 and ... % interval = 0` because Postgres
            -- does not promise to evaluate AND left to right, and the second half divides by it.
            when 'interval' then
                case
                    when coalesce(t.recurrence_interval, 0) < 1 then false
                    else (hari - b.mulai) % t.recurrence_interval = 0
                end
        end
    from batas b;
$$;

comment on function public.tugas_jatuh_pada is
    'True when the task has an occurrence on the given day. Mirrors RecurrenceRule in Recurrence.kt.';


-- ------------------------------------------------------------------
-- 2. The daily sweep
-- ------------------------------------------------------------------

-- security definer, like every trigger in this schema: `notifications` has no INSERT policy at
-- all, by design -- only the database writes notifications, never a client.
create or replace function public.kirim_pengingat_tugas()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    -- WITA, not UTC. "Today" has to mean today in Makassar or a job running at 23:00 UTC would
    -- ask about tomorrow's tasks for half the users it is meant to remind.
    hari_ini date := (now() at time zone 'Asia/Makassar')::date;
    jumlah   integer;
begin
    insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
    select
        a.orangtua_id,
        'tugas_reminder',
        'Tugas hari ini untuk ' || a.nama,
        -- notif_snippet, not the raw column: `judul` is free text and the tray parses no markdown.
        -- The coalesce covers a whitespace-only title, which the not-null constraint permits.
        coalesce(public.notif_snippet(t.judul, 120), 'Ada tugas yang menunggu hari ini.'),
        'tugas',
        t.id
    from public.tugas t
    join public.anak a on a.id = t.anak_id
    where a.is_active
      and a.orangtua_id is not null
      and public.tugas_jatuh_pada(t, hari_ini)

      -- Already done today. Per occurrence, not per task: 0002 replaced the unique constraint on
      -- tugas_id with (tugas_id, occurrence_date) precisely so a recurring programme can be
      -- completed on Monday and still be open on Tuesday.
      and not exists (
          select 1 from public.tugas_penyelesaian p
           where p.tugas_id = t.id
             and p.occurrence_date = hari_ini
      )

      -- Already reminded today. This is what makes the job safe to run twice -- by hand while
      -- testing, or by pg_cron after a failure -- rather than sending a parent the same nudge
      -- again. Compared in WITA for the same reason `hari_ini` is.
      and not exists (
          select 1 from public.notifications n
           where n.type = 'tugas_reminder'
             and n.entity_table = 'tugas'
             and n.entity_id = t.id
             and (n.created_at at time zone 'Asia/Makassar')::date = hari_ini
      );

    get diagnostics jumlah = row_count;
    return jumlah;
end $$;

comment on function public.kirim_pengingat_tugas is
    'Writes one tugas_reminder per undone task due today. Idempotent within a day. Returns the count.';


-- ------------------------------------------------------------------
-- 3. Schedule it
-- ------------------------------------------------------------------

-- pg_cron must be enabled first. On Supabase the reliable way is the Dashboard:
--   Database -> Extensions -> search "pg_cron" -> enable.
-- This line is here so a fresh environment gets it too, and is a no-op once it exists.
create extension if not exists pg_cron;

-- 23:00 UTC = 07:00 WITA. pg_cron schedules in UTC and does not know about Makassar; the function
-- does, which is why the offset lives here and the date arithmetic lives there.
--
-- Seven in the morning because the reminder has to arrive before the day starts, not during it:
-- these are families fitting therapy around work, and a nudge at noon is a nudge about something
-- half-missed already.
--
-- unschedule-then-schedule rather than a bare schedule, so re-running this migration replaces the
-- job instead of stacking a second one. The exception block is not laziness -- cron.unschedule
-- raises rather than returning quietly when the job does not exist, which is the normal case on a
-- first run.
do $do$
begin
    perform cron.unschedule('pengingat-tugas');
exception
    when others then null;
end
$do$;

select cron.schedule(
    'pengingat-tugas',
    '0 23 * * *',
    $cron$select public.kirim_pengingat_tugas()$cron$
);


-- ---------------------------------------------------------------------
-- Checking it afterwards, without waiting for tomorrow morning:
--
--   -- what would go out right now, without sending anything:
--   select count(*) from public.tugas t join public.anak a on a.id = t.anak_id
--    where a.is_active
--      and public.tugas_jatuh_pada(t, (now() at time zone 'Asia/Makassar')::date);
--
--   -- actually send, and see how many:
--   select public.kirim_pengingat_tugas();
--
--   -- run it a second time; the dedupe means this must return 0:
--   select public.kirim_pengingat_tugas();
--
--   -- the job itself:
--   select jobname, schedule, active from cron.job where jobname = 'pengingat-tugas';
--   select status, return_message, start_time from cron.job_run_details
--    where jobid = (select jobid from cron.job where jobname = 'pengingat-tugas')
--    order by start_time desc limit 5;
-- ---------------------------------------------------------------------
