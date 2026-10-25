-- Requires 0001..0016, notably tugas_jatuh_pada from 0012.
-- Additive migration. Does not rewrite completions or send historical notifications.
begin;
alter table public.tugas
    add column cancelled_at timestamptz,
    add column previous_version_id uuid references public.tugas(id),
    add column revision integer not null default 0;
create unique index tugas_one_successor on public.tugas(previous_version_id) where previous_version_id is not null;
create index penyelesaian_history_page on public.tugas_penyelesaian(occurrence_date desc, id desc);

-- Idempotency survives a committed save whose HTTP response was lost.
create table public.program_edit_requests (
    request_id uuid primary key,
    actor_id uuid not null references public.profiles(id),
    task_id uuid not null references public.tugas(id),
    result_id uuid not null references public.tugas(id),
    changes jsonb not null
);
alter table public.program_edit_requests enable row level security;
revoke all on public.program_edit_requests from anon, authenticated;

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
        t.cancelled_at is null
        and hari >= b.mulai
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


create function public.parent_task_history(
    p_from date default null, p_to date default null,
    p_cursor_date date default null, p_cursor_id uuid default null
) returns table(id uuid, occurrence_date date, task jsonb)
language sql stable security invoker set search_path = '' as $$
    select c.id, c.occurrence_date, to_jsonb(t)
    from public.tugas_penyelesaian c join public.tugas t on t.id=c.tugas_id
    where (p_from is null or c.occurrence_date >= p_from)
      and (p_to is null or c.occurrence_date <= p_to)
      and (p_cursor_date is null or (c.occurrence_date,c.id) < (p_cursor_date,p_cursor_id))
    order by c.occurrence_date desc, c.id desc limit 51;
$$;

create function public.parent_old_overdue(
    p_before date, p_cursor_date date default null, p_cursor_id uuid default null
) returns table(id uuid, occurrence_date date, task jsonb)
language sql stable security invoker set search_path = '' as $$
    select t.id, d.day::date, to_jsonb(t)
    from public.tugas t
    cross join lateral generate_series(
        greatest(coalesce(case when t.recurrence='once' then t.deadline else t.starts_on end,
                 t.starts_on, t.deadline, t.created_at::date), p_cursor_date)::timestamp,
        least(p_before - 1, case when t.recurrence='once' then coalesce(t.deadline,t.starts_on,t.created_at::date) else p_before-1 end,
              coalesce(t.ends_on, p_before - 1),
              coalesce(t.stopped_at::date - 1, p_before - 1))::timestamp,
        interval '1 day') d(day)
    where t.cancelled_at is null and public.tugas_jatuh_pada(t,d.day::date)
      and (p_cursor_date is null or (d.day::date,t.id) > (p_cursor_date,p_cursor_id))
      and not exists(select 1 from public.tugas_penyelesaian c
                     where c.tugas_id=t.id and c.occurrence_date=d.day::date)
    order by d.day::date, t.id limit 51;
$$;

-- Existing APKs only update stopped_at directly. Protect version metadata/content from
-- bypassing the transaction RPC while preserving that stop action and normal inserts.
drop policy tugas_insert on public.tugas;
create policy tugas_insert on public.tugas for insert to authenticated with check (
    relawan_id=auth.uid() and public.is_relawan_of(anak_id) and media_path is not null
    and previous_version_id is null and cancelled_at is null and revision=0
);

create function public.bump_task_revision() returns trigger
language plpgsql set search_path = '' as $$
begin
    if current_user='authenticated'
       and (to_jsonb(new)-'stopped_at'-'revision') is distinct from (to_jsonb(old)-'stopped_at'-'revision') then
        raise exception 'Gunakan aksi Edit atau Batalkan agar riwayat tetap tersimpan';
    end if;
    new.revision := old.revision + 1;
    return new;
end $$;
create trigger trg_task_revision before update on public.tugas
for each row execute function public.bump_task_revision();

-- Every completion locks the same task as editing/cancellation. Existing rows stay untouched.
create function public.guard_task_completion() returns trigger
language plpgsql security definer set search_path = '' as $$
declare t public.tugas;
begin
    select * into t from public.tugas where id=new.tugas_id for update;
    if not found or t.cancelled_at is not null then
        raise exception 'Tugas sudah dibatalkan atau tidak ditemukan';
    end if;
    -- Restrict changed/versioned tasks; preserve historical legacy submission semantics.
    if (t.revision > 0 or t.previous_version_id is not null)
        and not public.tugas_jatuh_pada(t,new.occurrence_date) then
        raise exception 'Jadwal tugas sudah berubah. Muat ulang tugas sebelum mengirim.';
    end if;
    return new;
end $$;
create trigger trg_guard_task_completion before insert on public.tugas_penyelesaian
for each row execute function public.guard_task_completion();

create function public.cancel_one_off(p_task_id uuid, p_revision integer) returns uuid
language plpgsql security definer set search_path = '' as $$
declare t public.tugas;
begin
    if p_revision is null then raise exception 'Versi tugas wajib diisi'; end if;
    select * into t from public.tugas where id=p_task_id for update;
    if not found or auth.uid() is null or t.relawan_id <> auth.uid() then
        raise exception 'Anda tidak dapat membatalkan tugas ini';
    end if;
    if t.cancelled_at is not null then return t.id; end if;
    if t.recurrence <> 'once' or t.status='done' or t.revision <> p_revision
       or exists(select 1 from public.tugas_penyelesaian where tugas_id=t.id) then
        raise exception 'Tugas sudah berubah atau memiliki setoran. Muat ulang daftar.';
    end if;
    update public.tugas set cancelled_at=now(),
        -- Also hides cancelled tasks from older APKs and their recurrence calculations.
        ends_on=coalesce(t.deadline,t.starts_on,t.created_at::date)-1
    where id=t.id;
    return t.id;
end $$;

create function public.edit_program(p_task_id uuid, p_revision integer, p_request_id uuid, p_changes jsonb)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    t public.tugas; n public.tugas; r public.program_edit_requests;
    today date := (now() at time zone 'Asia/Makassar')::date;
    boundary date := today+1;
    result_id uuid;
    schedule_changed boolean;
    anchor date;
begin
    if auth.uid() is null then raise exception 'Silakan login ulang'; end if;
    if p_revision is null or p_request_id is null then raise exception 'Versi dan identitas permintaan wajib diisi'; end if;
    -- Serializes retries even before the request row exists.
    perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,0));
    select * into r from public.program_edit_requests where request_id=p_request_id;
    if found then
        if r.actor_id <> auth.uid() or r.task_id <> p_task_id then raise exception 'Permintaan tidak valid'; end if;
        if r.changes is distinct from p_changes then
            raise exception 'Perubahan sebelumnya sudah tersimpan. Kembali ke daftar dan buka Edit lagi.';
        end if;
        return r.result_id;
    end if;
    select * into t from public.tugas where id=p_task_id for update;
    if not found or t.relawan_id <> auth.uid() or not public.is_relawan_of(t.anak_id) then
        raise exception 'Anda tidak dapat mengedit program ini';
    end if;
    if t.revision <> p_revision or t.status='done' or t.cancelled_at is not null or t.stopped_at is not null
       or exists(select 1 from public.tugas where previous_version_id=t.id)
       or (t.recurrence<>'once' and t.ends_on < today) then
        raise exception 'Program sudah berubah atau tidak aktif. Muat ulang daftar.';
    end if;
    if t.recurrence='once' and exists(select 1 from public.tugas_penyelesaian where tugas_id=t.id) then
        raise exception 'Tugas yang sudah disetor tidak dapat diedit';
    end if;
    -- Whitelist editable fields; never accept an identity/owner/status from the client.
    n := t;
    n.judul := btrim(p_changes->>'judul');
    n.instruksi := p_changes->>'instruksi';
    n.media_kind := (p_changes->>'media_kind')::public.media_kind;
    n.media_path := p_changes->>'media_path';
    n.priority_level := (p_changes->>'priority_level')::public.task_priority;
    n.alat_bantu := case when jsonb_typeof(p_changes->'alat_bantu')='array'
        then array(select jsonb_array_elements_text(p_changes->'alat_bantu')) else null end;
    n.deadline := (p_changes->>'deadline')::date;
    n.recurrence := (p_changes->>'recurrence')::public.recurrence_type;
    n.recurrence_days := case when jsonb_typeof(p_changes->'recurrence_days')='array'
        then array(select jsonb_array_elements_text(p_changes->'recurrence_days'))::smallint[] else null end;
    n.recurrence_day_of_month := (p_changes->>'recurrence_day_of_month')::smallint;
    n.recurrence_interval := (p_changes->>'recurrence_interval')::smallint;
    n.starts_on := (p_changes->>'starts_on')::date;
    n.ends_on := (p_changes->>'ends_on')::date;
    if coalesce(n.judul,'')='' or coalesce(n.media_path,'')='' or n.media_kind is null or n.priority_level is null
       or n.recurrence is null or (t.recurrence='once') <> (n.recurrence='once') then
        raise exception 'Isi program belum lengkap atau jenis program tidak sesuai';
    end if;
    if n.media_path <> t.media_path and split_part(n.media_path,'/',1) <> t.anak_id::text then
        raise exception 'Media tidak sesuai dengan anak';
    end if;
    if n.recurrence<>'once' and (n.starts_on is null or n.ends_on is null or n.ends_on<n.starts_on
        or (n.recurrence='weekly' and (coalesce(cardinality(n.recurrence_days),0)=0 or not n.recurrence_days <@ array[1,2,3,4,5,6,7]::smallint[]))
        or (n.recurrence='monthly' and coalesce(n.recurrence_day_of_month,0) not between 1 and 31)
        or (n.recurrence='interval' and coalesce(n.recurrence_interval,0)<1)) then
        raise exception 'Jadwal program tidak valid';
    end if;
    if n is not distinct from t then
        insert into public.program_edit_requests values(p_request_id,auth.uid(),t.id,t.id,p_changes);
        return t.id;
    end if;
    anchor := coalesce(t.starts_on,t.deadline,t.created_at::date);
    if t.recurrence<>'once' and (anchor<=today or exists(select 1 from public.tugas_penyelesaian where tugas_id=t.id)) then
        if exists(select 1 from public.tugas_penyelesaian where tugas_id=t.id and occurrence_date>=boundary) then
            raise exception 'Sudah ada setoran untuk jadwal mendatang; program tidak dapat diganti';
        end if;
        schedule_changed := (n.recurrence,n.recurrence_days,n.recurrence_day_of_month,n.recurrence_interval,n.starts_on)
            is distinct from (t.recurrence,t.recurrence_days,t.recurrence_day_of_month,t.recurrence_interval,t.starts_on);
        n.starts_on := boundary;
        if not schedule_changed and n.recurrence='interval' then
            n.starts_on := boundary + ((n.recurrence_interval - ((boundary-anchor) % n.recurrence_interval)) % n.recurrence_interval);
        end if;
        if not exists(select 1 from generate_series(n.starts_on::timestamp,n.ends_on::timestamp,interval '1 day') d(day)
                      where public.tugas_jatuh_pada(n,d.day::date)) then
            raise exception 'Tidak ada jadwal baru mulai besok. Perpanjang tanggal selesai.';
        end if;
        update public.tugas set ends_on=least(t.ends_on,today) where id=t.id;
        insert into public.tugas(anak_id,relawan_id,judul,instruksi,media_kind,media_path,priority_level,alat_bantu,
            recurrence,recurrence_days,recurrence_day_of_month,recurrence_interval,starts_on,ends_on,previous_version_id)
        values(t.anak_id,t.relawan_id,n.judul,n.instruksi,n.media_kind,n.media_path,n.priority_level,n.alat_bantu,
            n.recurrence,n.recurrence_days,n.recurrence_day_of_month,n.recurrence_interval,n.starts_on,n.ends_on,t.id)
        returning id into result_id;
    else
        update public.tugas set judul=n.judul,instruksi=n.instruksi,media_kind=n.media_kind,media_path=n.media_path,
            priority_level=n.priority_level,alat_bantu=n.alat_bantu,deadline=n.deadline,recurrence=n.recurrence,
            recurrence_days=n.recurrence_days,recurrence_day_of_month=n.recurrence_day_of_month,
            recurrence_interval=n.recurrence_interval,starts_on=n.starts_on,ends_on=n.ends_on
        where id=t.id returning id into result_id;
    end if;
    insert into public.program_edit_requests values(p_request_id,auth.uid(),t.id,result_id,p_changes);
    return result_id;
end $$;

revoke all on function public.parent_task_history(date,date,date,uuid), public.parent_old_overdue(date,date,uuid),
    public.edit_program(uuid,integer,uuid,jsonb), public.cancel_one_off(uuid,integer),
    public.guard_task_completion(), public.bump_task_revision() from public;
grant execute on function public.parent_task_history(date,date,date,uuid), public.parent_old_overdue(date,date,uuid),
    public.edit_program(uuid,integer,uuid,jsonb), public.cancel_one_off(uuid,integer) to authenticated;
commit;
