-- Disposable local database only; includes auth/storage stand-ins and earlier notification tests.
\ir task_completion_notifications.sql
create role anon nologin;
\ir ../migrations/0003_kemandirian_durasi.sql
\ir ../migrations/0004_alat_bantu.sql
\ir ../migrations/0017_history_program_management.sql

-- Calendar-relative fixtures make this suite work on any run date.
insert into public.tugas(id,anak_id,relawan_id,judul,media_kind,media_path,recurrence,starts_on,ends_on,recurrence_interval)
values ('20000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',
'Interval lama','image','demo.jpg','interval',(now() at time zone 'Asia/Makassar')::date-10,(now() at time zone 'Asia/Makassar')::date+60,3);

create function pg_temp.edit_payload(task_id uuid, title text) returns jsonb language sql as $$
 select to_jsonb(t) || jsonb_build_object('judul',title) from public.tugas t where id=task_id
$$;
create temporary table saved_edit(payload jsonb, result_id uuid);
insert into saved_edit(payload) select pg_temp.edit_payload('20000000-0000-0000-0000-000000000010','Interval baru');
grant all on saved_edit to authenticated;
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
update saved_edit set result_id=public.edit_program('20000000-0000-0000-0000-000000000010',0,'40000000-0000-0000-0000-000000000001',payload);
select pg_temp.check_true(result_id=public.edit_program('20000000-0000-0000-0000-000000000010',0,'40000000-0000-0000-0000-000000000001',payload), 'Retry returns same version') from saved_edit;
reset role;
select pg_temp.check_true((select count(*)=1 from public.tugas where previous_version_id='20000000-0000-0000-0000-000000000010'),'Exactly one replacement');
select pg_temp.check_true((select judul='Interval lama' and ends_on=(now() at time zone 'Asia/Makassar')::date from public.tugas where id='20000000-0000-0000-0000-000000000010'),'Old instructions/title preserved and old schedule ends today');
select pg_temp.check_true((select starts_on=(now() at time zone 'Asia/Makassar')::date+2 from public.tugas where previous_version_id='20000000-0000-0000-0000-000000000010'),'Content-only interval edit preserves cadence');
select pg_temp.check_true((select count(*)=1 from public.notifications where entity_id=(select result_id from saved_edit) and type='tugas_baru'),'One task notification for replacement');

-- Already-saved proof/evaluation remain independent of the current task rule.
update public.tugas set stopped_at='2026-01-01' where id='20000000-0000-0000-0000-000000000001';
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select pg_temp.check_true((select count(*)=2 from public.parent_task_history('2026-01-02','2026-01-02')), 'Inclusive date filter reads stopped-program completions');
select pg_temp.check_true((select count(*)=0 from public.parent_task_history('2026-01-03','2026-01-04')), 'Empty range is empty');
reset role;

-- More than one page with equal dates, sorted by completion id.
insert into public.tugas(id,anak_id,relawan_id,judul,media_kind,media_path,deadline)
select ('50000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','Ancien '||i,'image','demo.jpg','2025-01-01' from generate_series(1,110) i;
insert into public.tugas_penyelesaian(id,tugas_id,orangtua_id,occurrence_date)
select ('60000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,('50000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'00000000-0000-0000-0000-000000000001','2025-01-01' from generate_series(1,60) i;
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select pg_temp.check_true((select count(*)=51 from public.parent_task_history('2025-01-01','2025-01-01')), 'History returns page plus lookahead');
select pg_temp.check_true((select count(*)=10 from public.parent_task_history('2025-01-01','2025-01-01','2025-01-01','60000000-0000-0000-0000-000000000011')), 'History cursor has no overlap or omission');
select pg_temp.check_true((select count(*)=50 from public.parent_old_overdue('2025-02-01') where id::text like '50000000%'), 'All unfinished old one-off tasks are reachable');
reset role;

set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',false);
do $$ begin
  begin
    perform public.cancel_one_off('50000000-0000-0000-0000-000000000061',0);
    raise exception 'Unauthorized cancellation accepted';
  exception when raise_exception then if SQLERRM='Unauthorized cancellation accepted' then raise; end if; end;
end $$;
select pg_temp.check_true((select count(*)=0 from public.parent_task_history()), 'Other volunteer cannot read history');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.cancel_one_off('50000000-0000-0000-0000-000000000061',0);
select public.cancel_one_off('50000000-0000-0000-0000-000000000061',0);
select pg_temp.check_true((select not public.tugas_jatuh_pada(t,'2025-01-01') from public.tugas t where id='50000000-0000-0000-0000-000000000061'),'Cancelled task never generates reminders');
do $$ begin
  begin
    perform public.cancel_one_off('50000000-0000-0000-0000-000000000001',1);
    raise exception 'Completed task cancelled';
  exception when raise_exception then if SQLERRM='Completed task cancelled' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$ begin
  begin
    insert into public.tugas_penyelesaian(tugas_id,orangtua_id,occurrence_date) values('50000000-0000-0000-0000-000000000061',auth.uid(),'2025-01-01');
    raise exception 'Cancelled task accepts completion';
  exception when raise_exception then if SQLERRM='Cancelled task accepts completion' then raise; end if; end;
end $$;
reset role;
select 'History and program management SQL tests passed' as result;

-- Daily/weekly/monthly: a content-only edit partitions the original dates exactly.
do $$
declare
  cadence text; original public.tugas; replacement public.tugas; tid uuid; rid uuid;
  today date := (now() at time zone 'Asia/Makassar')::date;
  changed jsonb;
begin
  perform set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
  foreach cadence in array array['daily','weekly','monthly'] loop
    insert into public.tugas(anak_id,relawan_id,judul,media_kind,media_path,recurrence,starts_on,ends_on,recurrence_days,recurrence_day_of_month)
    values('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','Cadence','image','demo.jpg',cadence::public.recurrence_type,today-100,today+100,array[1,3,5]::smallint[],31)
    returning * into original;
    changed := to_jsonb(original) || '{"instruksi":"Versi baru"}'::jsonb;
    rid := public.edit_program(original.id,0,gen_random_uuid(),changed);
    select * into replacement from public.tugas where id=rid;
    perform pg_temp.check_true(replacement.starts_on=today+1, 'New recurring version begins tomorrow');
    perform pg_temp.check_true(not exists(
      select 1 from generate_series((today-100)::timestamp,(today+100)::timestamp,interval '1 day') d(day)
      where public.tugas_jatuh_pada(original,d.day::date) is distinct from
        (case when d.day::date<=today then public.tugas_jatuh_pada((select t from public.tugas t where id=original.id),d.day::date)
              else public.tugas_jatuh_pada(replacement,d.day::date) end)), 'Version boundary must preserve every old cadence date');
    begin
      perform public.edit_program(original.id,0,gen_random_uuid(),changed);
      raise exception 'Stale version accepted';
    exception when raise_exception then if SQLERRM='Stale version accepted' then raise; end if; end;
  end loop;
  -- Changing cadence explicitly resets its phase tomorrow.
  changed := to_jsonb(replacement) || '{"recurrence":"interval","recurrence_interval":4}'::jsonb;
  -- Future unsubmitted version is edited in place.
  tid := public.edit_program(replacement.id,0,gen_random_uuid(),changed);
  perform pg_temp.check_true(tid=replacement.id,'Unstarted version edits in place');
  perform pg_temp.check_true((select recurrence='interval' and recurrence_interval=4 from public.tugas where id=tid),'Changed schedule saved');
  -- A pending future completion must not disappear during version replacement.
  insert into public.tugas_penyelesaian(tugas_id,orangtua_id,occurrence_date)
  values(tid,'00000000-0000-0000-0000-000000000001',today+1);
  select to_jsonb(t) || '{"instruksi":"Perubahan lain"}'::jsonb into changed from public.tugas t where id=tid;
  begin
    perform public.edit_program(tid,(changed->>'revision')::int,gen_random_uuid(),changed);
    raise exception 'Future completion was displaced';
  exception when raise_exception then if SQLERRM='Future completion was displaced' then raise; end if; end;
end $$;

-- Direct edits of a one-off update the existing identity, but reject the old date on submit.
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.edit_program('50000000-0000-0000-0000-000000000062',0,gen_random_uuid(),
    pg_temp.edit_payload('50000000-0000-0000-0000-000000000062','Tanggal baru') || '{"deadline":"2025-02-02"}'::jsonb);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$ begin
  begin
    insert into public.tugas_penyelesaian(tugas_id,orangtua_id,occurrence_date)
    values('50000000-0000-0000-0000-000000000062',auth.uid(),'2025-01-01');
    raise exception 'Old date accepted after edit';
  exception when raise_exception then if SQLERRM='Old date accepted after edit' then raise; end if; end;
end $$;
insert into public.tugas_penyelesaian(tugas_id,orangtua_id,occurrence_date)
values('50000000-0000-0000-0000-000000000062',auth.uid(),'2025-02-02');
reset role;
select 'Extended cadence and concurrent-edit precondition tests passed' as result;
-- Legacy stop remains supported, but raw PATCH must not rewrite historical content.
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
do $$ begin
  begin
    update public.tugas set instruksi='Menimpa riwayat' where id='20000000-0000-0000-0000-000000000010';
    raise exception 'Raw historical rewrite accepted';
  exception when raise_exception then if SQLERRM='Raw historical rewrite accepted' then raise; end if; end;
end $$;
update public.tugas set stopped_at=now() where id='20000000-0000-0000-0000-000000000010';
reset role;
select 'Legacy stop and protected edit tests passed' as result;
