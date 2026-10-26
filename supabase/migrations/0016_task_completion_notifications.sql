-- Requires 0010 (notif_snippet), 0014 (feedback_relawan), and committed 0015.
-- Install the APK with completion notification routing before enabling these
-- producers. Existing rows are not backfilled: every notification INSERT also
-- queues FCM through the existing trg_notification_push / Edge Function.

create or replace function public.notify_task_submission()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    task public.tugas;
    child_name text;
    parent_name text;
begin
    select * into task from public.tugas where id = new.tugas_id;
    if not found then return new; end if;
    select nama into child_name from public.anak where id = task.anak_id;
    select full_name into parent_name from public.profiles where id = new.orangtua_id;

    -- Only the task's author can evaluate it under penyelesaian_update.
    -- Notifying every assigned volunteer would send some to an inaccessible row.
    insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
    values (
        task.relawan_id, 'tugas_setoran',
        coalesce(nullif(btrim(parent_name), ''), 'Orang tua') || ' mengirim tugas ' ||
            coalesce(child_name, 'anak'),
        public.notif_snippet(task.judul, 90) || ' · ' || to_char(new.occurrence_date, 'DD Mon YYYY'),
        'tugas_penyelesaian', new.id
    );
    return new;
end $$;

-- Keep the existing completion/status trigger unchanged.
create trigger trg_task_submission_notification
    after insert on public.tugas_penyelesaian
    for each row execute function public.notify_task_submission();

create or replace function public.notify_task_evaluation()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    task public.tugas;
    child_name text;
    volunteer_name text;
    evaluation text := nullif(btrim(new.feedback_relawan), '');
begin
    -- Clearing a draft or saving identical text must not send another push.
    if evaluation is null or evaluation is not distinct from nullif(btrim(old.feedback_relawan), '') then
        return new;
    end if;
    select * into task from public.tugas where id = new.tugas_id;
    if not found then return new; end if;
    select nama into child_name from public.anak where id = task.anak_id;
    select full_name into volunteer_name from public.profiles where id = task.relawan_id;

    insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
    values (
        new.orangtua_id, 'tugas_evaluasi',
        coalesce(nullif(btrim(volunteer_name), ''), 'Relawan') || ' mengevaluasi tugas ' ||
            coalesce(child_name, 'anak'),
        public.notif_snippet(to_char(new.occurrence_date, 'DD Mon YYYY') || ' · ' || evaluation, 120),
        'tugas_penyelesaian', new.id
    );
    return new;
end $$;

create trigger trg_task_evaluation_notification
    after update of feedback_relawan on public.tugas_penyelesaian
    for each row execute function public.notify_task_evaluation();

revoke all on function public.notify_task_submission() from public;
revoke all on function public.notify_task_evaluation() from public;
