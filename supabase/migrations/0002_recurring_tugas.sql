-- =====================================================================
-- Sipatokkong — Phase 2: recurring program ajar
--
-- One `tugas` row = one PROGRAM. The recurrence rule lives on the row;
-- individual occurrence dates are computed by the client, never stored.
-- Completions are keyed per occurrence date instead of per task.
--
-- Existing rows are untouched: they default to recurrence = 'once' and
-- keep behaving exactly as before.
-- =====================================================================

-- ------------------------------------------------------------------
-- 1. Recurrence rule on tugas
-- ------------------------------------------------------------------
create type public.recurrence_type as enum ('once', 'daily', 'weekly', 'monthly', 'interval');

alter table public.tugas
    add column recurrence              public.recurrence_type not null default 'once',
    add column recurrence_days         smallint[],   -- ISO day-of-week 1=Mon..7=Sun, for 'weekly'
    add column recurrence_day_of_month smallint,     -- 1..31, for 'monthly'
    add column recurrence_interval     smallint,     -- every N days, for 'interval'
    add column starts_on               date,
    add column ends_on                 date,
    add column stopped_at              timestamptz;  -- set when the volunteer stops the program

comment on column public.tugas.recurrence_days is
    'ISO day-of-week numbers (1=Monday .. 7=Sunday) for weekly recurrence.';
comment on column public.tugas.stopped_at is
    'When set, no occurrences are generated on or after this date.';

-- ------------------------------------------------------------------
-- 2. Completions become per-occurrence
-- ------------------------------------------------------------------
alter table public.tugas_penyelesaian add column occurrence_date date;

-- Backfill: an existing completion belongs to the day it was recorded.
update public.tugas_penyelesaian
    set occurrence_date = completed_at::date
    where occurrence_date is null;

alter table public.tugas_penyelesaian alter column occurrence_date set not null;

-- Replace "one completion per task" with "one completion per task per date".
alter table public.tugas_penyelesaian
    drop constraint if exists tugas_penyelesaian_tugas_id_key;
alter table public.tugas_penyelesaian
    add constraint tugas_penyelesaian_tugas_occurrence_key unique (tugas_id, occurrence_date);

create index if not exists idx_penyelesaian_tugas_date
    on public.tugas_penyelesaian (tugas_id, occurrence_date);

-- ------------------------------------------------------------------
-- 3. A recurring program is never "done" just because one day was done
-- ------------------------------------------------------------------
create or replace function public.on_penyelesaian_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    update public.tugas
        set status = 'done'
        where id = new.tugas_id and recurrence = 'once';
    return new;
end $$;

-- Note: on_tugas_insert is intentionally left alone. One program still inserts
-- exactly one row, so the parent gets exactly one 'tugas_baru' notification.
