-- =====================================================================
-- Sipatokkong -- 0013: remind before a one-shot task is due, not only on
-- the day
--
-- 0012 reminds on the day an occurrence falls and nothing sooner: its
-- only predicate is tugas_jatuh_pada(t, hari_ini), a same-day equality
-- test. For a task assigned a week ahead that is one notification, on
-- the morning it is already due -- no time left to arrange anything
-- around it, which for these families is the whole difficulty.
--
-- This adds two earlier nudges, H-3 and H-1, for `once` tasks only.
--
-- REQUIRES 0012: tugas_jatuh_pada and the pengingat-tugas cron job are
-- defined there, and this is a create-or-replace of that migration's
-- kirim_pengingat_tugas(). The schedule, the job name and the function
-- name are all unchanged, so nothing needs re-scheduling.
--
-- Why `once` only. On a daily programme "due tomorrow" is true every
-- single day, so an H-1 rule would send two notifications a day per
-- task, forever. Three active tasks would mean six a day. That is how
-- people learn to turn notifications off, and it would cost this app the
-- one channel it has to reach a parent.
--
-- It also happens to be the only place the idea is even defined:
-- `tugas.deadline` is written solely for non-recurring tasks
-- (CreateTaskViewModel nulls it for every recurring type), and for those
-- it IS the occurrence date. So "approaching deadline" and "approaching
-- occurrence" are the same question here, and tugas_jatuh_pada already
-- answers it.
-- =====================================================================


-- ------------------------------------------------------------------
-- The daily sweep, now with two lookahead buckets
-- ------------------------------------------------------------------

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
    with kandidat as (
        -- `sisa` = days remaining until the occurrence being reminded about. Null means this task
        -- has nothing to say today, which is the overwhelmingly common case.
        --
        -- Deliberately reuses tugas_jatuh_pada with a shifted date rather than doing its own "is it
        -- N days away" arithmetic. That predicate already mirrors RecurrenceRule in Recurrence.kt
        -- branch by branch; a second hand-rolled version here would be a third implementation of
        -- one rule, and the one nobody would think to check.
        select
            t.id,
            t.judul,
            a.orangtua_id,
            a.nama as anak_nama,
            case
                when public.tugas_jatuh_pada(t, hari_ini) then 0
                -- Lookahead is `once`-only. See the header for why a recurring programme must not
                -- get one. The order matters only in that today wins: a `once` task cannot be both.
                when t.recurrence = 'once'
                     and public.tugas_jatuh_pada(t, hari_ini + 1) then 1
                when t.recurrence = 'once'
                     and public.tugas_jatuh_pada(t, hari_ini + 3) then 3
            end as sisa
        from public.tugas t
        join public.anak a on a.id = t.anak_id
        where a.is_active
          and a.orangtua_id is not null
    )
    insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
    select
        k.orangtua_id,
        -- Reuses 'tugas_reminder' rather than adding a notif_type value. Safe because the dedupe
        -- key is (task, type, today) and a `once` task has exactly ONE occurrence, so its three
        -- reminders fall on three different days and cannot suppress each other. A new enum value
        -- would cost an `alter type ... add value`, a fourth NotifType, an icon and tests, to say
        -- something the title below already says.
        --
        -- That safety ends the moment the lookahead is extended to recurring tasks: then one task
        -- can occupy two buckets on one day and the per-day dedupe would silently drop one. If that
        -- day comes, the dedupe needs the target occurrence date -- a column `notifications` does
        -- not have.
        'tugas_reminder',
        -- One shape, three timings, so a parent scanning a tray reads the same sentence with the
        -- only varying word first. No ELSE: a future bucket added without extending this yields a
        -- null title, and NotifDisplay.from already falls back to "Pengingat tugas hari ini" --
        -- wrong wording, but it renders instead of breaking.
        case k.sisa
            when 0 then 'Tugas hari ini untuk ' || k.anak_nama
            when 1 then 'Tugas besok untuk ' || k.anak_nama
            when 3 then 'Tugas 3 hari lagi untuk ' || k.anak_nama
        end,
        -- notif_snippet, not the raw column: `judul` is free text and the tray parses no markdown.
        -- The coalesce covers a whitespace-only title, which the not-null constraint permits.
        coalesce(public.notif_snippet(k.judul, 120), 'Ada tugas yang menunggu.'),
        'tugas',
        k.id
    from kandidat k
    where k.sisa is not null

      -- The occurrence being reminded ABOUT, not today's. `hari_ini` here would be wrong in both
      -- directions: it would keep nagging about a task the parent finished early, and it would
      -- suppress a real H-3 reminder whenever some unrelated occurrence happened to be done today.
      and not exists (
          select 1 from public.tugas_penyelesaian p
           where p.tugas_id = k.id
             and p.occurrence_date = hari_ini + k.sisa
      )

      -- Already reminded today. This is what makes the job safe to run twice -- by hand while
      -- testing, or by pg_cron after a failure. Compared in WITA for the same reason hari_ini is.
      and not exists (
          select 1 from public.notifications n
           where n.type = 'tugas_reminder'
             and n.entity_table = 'tugas'
             and n.entity_id = k.id
             and (n.created_at at time zone 'Asia/Makassar')::date = hari_ini
      );

    get diagnostics jumlah = row_count;
    return jumlah;
end $$;

comment on function public.kirim_pengingat_tugas is
    'Writes one tugas_reminder per undone task due today, and for one-shot tasks also at H-1 and H-3. Idempotent within a day. Returns the count.';


-- ---------------------------------------------------------------------
-- Checking it without waiting for tomorrow morning, and without sending
-- anything. This is the query to run FIRST -- it also explains why 0012
-- has returned 0 on every call so far.
--
--   select a.nama as anak, t.judul, t.recurrence, t.deadline,
--          case
--            when public.tugas_jatuh_pada(t, (now() at time zone 'Asia/Makassar')::date)
--                 then 'hari ini'
--            when t.recurrence = 'once'
--                 and public.tugas_jatuh_pada(t, (now() at time zone 'Asia/Makassar')::date + 1)
--                 then 'besok'
--            when t.recurrence = 'once'
--                 and public.tugas_jatuh_pada(t, (now() at time zone 'Asia/Makassar')::date + 3)
--                 then '3 hari lagi'
--          end as akan_dikirim
--     from public.tugas t
--     join public.anak a on a.id = t.anak_id
--    where a.is_active
--    order by akan_dikirim nulls last;
--
--   -- then actually send, and see how many:
--   select public.kirim_pengingat_tugas();
--
--   -- run it a second time; the dedupe means this must return 0:
--   select public.kirim_pengingat_tugas();
--
-- A note for whoever finds "the reminder never arrives": the Deadline
-- field on a one-shot task is labelled "(opsional)" and is not
-- validated. Left empty, toRecurrenceRule falls back to created_at, so
-- the task is due the DAY IT WAS CREATED -- already past, so H-3 and H-1
-- can never fire for it. That is a filled-in field, not a broken job.
-- (The same fallback is why such a task shows "Terlambat" on the
-- parent's home the next morning.)
-- ---------------------------------------------------------------------
