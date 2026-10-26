-- Run only in a fresh, disposable local database:
-- createdb sipatokkong_flow_test
-- psql -X -v ON_ERROR_STOP=1 -d sipatokkong_flow_test -f supabase/tests/task_completion_notifications.sql
-- Auth and Storage platform objects below are local stand-ins; production is never contacted.
\set ON_ERROR_STOP on
do $$ begin
    if current_database() <> 'sipatokkong_flow_test' then
        raise exception 'Use a disposable database named sipatokkong_flow_test';
    end if;
end $$;
create schema auth;
create schema storage;
create role authenticated nologin;
create table auth.users (id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create table storage.buckets (id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects (id uuid primary key, bucket_id text, name text);
create function storage.foldername(text) returns text[] language sql immutable as $$ select string_to_array($1, '/') $$;

\ir ../migrations/0001_initial_schema.sql
\ir ../migrations/0002_recurring_tugas.sql
\ir ../migrations/0010_notifikasi_menyebut_nama.sql
\ir ../migrations/0014_tugas_penyelesaian_feedback.sql
\ir ../migrations/0015_task_notification_types.sql

create function pg_temp.check_true(ok boolean, message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception '%', message; end if; end $$;

insert into auth.users values
('00000000-0000-0000-0000-000000000001'),
('00000000-0000-0000-0000-000000000002'),
('00000000-0000-0000-0000-000000000003');
insert into public.profiles(id, role, full_name) values
('00000000-0000-0000-0000-000000000001','orangtua','Ibu Uji'),
('00000000-0000-0000-0000-000000000002','relawan','Relawan Pembuat'),
('00000000-0000-0000-0000-000000000003','relawan','Relawan Lain');
insert into public.anak(id,nama,orangtua_id) values
('10000000-0000-0000-0000-000000000001','Anak Uji','00000000-0000-0000-0000-000000000001');
insert into public.anak_relawan(anak_id,relawan_id) values
('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002'),
('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003');
insert into public.tugas(id,anak_id,relawan_id,judul,media_kind,media_path,recurrence,starts_on) values
('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','Latihan harian','image','demo.jpg','daily','2026-01-01'),
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','Latihan sekali','image','demo.jpg','once',null);
insert into public.tugas_penyelesaian(id,tugas_id,orangtua_id,occurrence_date) values
('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','2026-01-01');
truncate public.notifications;

\ir ../migrations/0016_task_completion_notifications.sql
select pg_temp.check_true((select count(*) = 0 from public.notifications), 'Migration must not backfill notifications');

grant usage on schema public, auth to authenticated;
grant select, insert, update on all tables in schema public to authenticated;
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
insert into public.tugas_penyelesaian(id,tugas_id,orangtua_id,occurrence_date) values
('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','2026-01-02'),
('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','2026-01-02');
reset role;
select pg_temp.check_true((select count(*) = 2 from public.notifications where type='tugas_setoran' and recipient_id='00000000-0000-0000-0000-000000000002' and entity_table='tugas_penyelesaian'), 'Submission notifies only the task author');
select pg_temp.check_true((select count(*)=0 from public.notifications where recipient_id='00000000-0000-0000-0000-000000000003'), 'Another assigned volunteer must not get inaccessible submission');
select pg_temp.check_true((select status='assigned' from public.tugas where id='20000000-0000-0000-0000-000000000001'), 'Recurring task must stay active');
select pg_temp.check_true((select status='done' from public.tugas where id='20000000-0000-0000-0000-000000000002'), 'Existing one-off completion trigger must still run');
do $$ begin
    begin
        insert into public.tugas_penyelesaian(tugas_id,orangtua_id,occurrence_date) values
        ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','2026-01-02');
        raise exception 'Duplicate completion should fail';
    exception when unique_violation then null;
    end;
end $$;
select pg_temp.check_true((select count(*)=2 from public.notifications), 'Duplicate attempt must not notify again');

set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
update public.tugas_penyelesaian set feedback_relawan='Bagus, lanjutkan.' where id='30000000-0000-0000-0000-000000000002';
reset role;
select pg_temp.check_true((select count(*)=1 from public.notifications where type='tugas_evaluasi' and recipient_id='00000000-0000-0000-0000-000000000001' and entity_id='30000000-0000-0000-0000-000000000002'), 'Evaluation must point to exact completion and submitting parent');
update public.tugas_penyelesaian set feedback_relawan=' Bagus, lanjutkan. ' where id='30000000-0000-0000-0000-000000000002';
update public.tugas_penyelesaian set catatan='Catatan berbeda' where id='30000000-0000-0000-0000-000000000002';
update public.tugas_penyelesaian set feedback_relawan='  ' where id='30000000-0000-0000-0000-000000000002';
select pg_temp.check_true((select count(*)=3 from public.notifications), 'No-op, unrelated update and clearing must not notify');
update public.tugas_penyelesaian set feedback_relawan='Arahan baru' where id='30000000-0000-0000-0000-000000000002';
select pg_temp.check_true((select count(*)=2 from public.notifications where type='tugas_evaluasi'), 'Changed nonempty evaluation must notify');

set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',false);
with changed as (
    update public.tugas_penyelesaian set feedback_relawan='Tidak boleh' where id='30000000-0000-0000-0000-000000000002' returning id
) select pg_temp.check_true((select count(*)=0 from changed), 'Non-author volunteer must not evaluate');
select pg_temp.check_true((select count(*)=0 from public.notifications), 'Unrelated user must not read notifications');
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select pg_temp.check_true((select count(*)=2 from public.notifications where type='tugas_evaluasi'), 'Parent can read their evaluations');
select pg_temp.check_true((select occurrence_date='2026-01-02' from public.tugas_penyelesaian where id='30000000-0000-0000-0000-000000000002'), 'Parent deep link can read the historical completion');
reset role;
select 'Task notification SQL tests passed' as result;
