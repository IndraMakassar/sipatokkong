-- =====================================================================
-- Sipatokkong -- 0011: a parent's reply reopens the thread
--
-- One defect, found by using the app: a volunteer answers a note, the
-- parent replies, and the volunteer is never shown that it happened.
-- The thread sits under "Sudah Beres" while a parent waits.
--
-- `on_balasan_insert` already notifies the assigned volunteers on a
-- parent's reply (added in 0010). What it does not do is move the row,
-- so the notification points at a thread the volunteer's own list still
-- files as finished.
--
-- The fix is one UPDATE. No new column, no enum change: 'in_process'
-- has been declared in `feedback_status` since 0001 and has never been
-- written or read by anything.
--
-- REQUIRES 0010 -- this is a `create or replace` of the function 0010
-- last defined, so running this against a database that never ran 0010
-- would silently revert the naming work in it.
--
-- `create or replace function` only. The `create trigger` statement in
-- 0001 already points at this name and must NOT be re-run.
--
-- The client half of this change ships independently and is already
-- safe: FeedbackTriage treats 'in_process' as needing attention, and
-- until this migration runs no row ever carries that value.
-- =====================================================================

create or replace function public.on_balasan_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    author_role public.user_role;
    author_nama text;
    fb          public.feedback;
    anak_nama   text;
    judul       text;
    ringkas     text;
begin
    -- The role lookup was already here; full_name rides along for free.
    select p.role, p.full_name into author_role, author_nama
      from public.profiles p where p.id = new.author_id;

    select * into fb from public.feedback f where f.id = new.feedback_id;
    if not found then
        -- New guard. Without it a reply on a vanished thread reaches the INSERT with a null
        -- recipient_id, and the not-null constraint rolls back the reply itself.
        return new;
    end if;

    select a.nama into anak_nama from public.anak a where a.id = fb.anak_id;

    -- Person-shaped, for the same reason as on_feedback_insert: this becomes the Person name in
    -- the tray. "Who, about which child."
    judul := coalesce(
                 nullif(btrim(author_nama), ''),
                 case when author_role = 'relawan' then 'Relawan' else 'Orang tua' end)
             || ' · ' || coalesce(anak_nama, 'anak');
    ringkas := public.notif_snippet(new.isi, 120);

    if author_role = 'relawan' then
        update public.feedback set status = 'answered', answered_at = now()
         where id = new.feedback_id;

        insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
        values (fb.orangtua_id, 'feedback_balasan', judul, ringkas, 'feedback', fb.id);

    elsif author_role = 'orangtua' then
        -- CHANGED IN 0011. The comment here used to read "deliberately does NOT touch `status`:
        -- a parent answering does not make the thread answered, and moving it would empty the
        -- volunteer's Perlu Ditanggapi list -- the opposite of the point."
        --
        -- That reasoning was right about the case it considered and blind to the other one. It
        -- pictured a thread still waiting, where leaving `status` alone keeps it in the work
        -- list -- correct. It never pictured a thread already `answered`, where leaving `status`
        -- alone keeps it OUT of the work list forever. A parent could reply to a volunteer's
        -- answer and no volunteer would ever be shown that it had happened.
        --
        -- 'in_process' has been in the feedback_status enum since 0001 and has never once been
        -- written or read. It is exactly this state -- the thread is live again, but not because
        -- the volunteer did anything -- so no enum change was needed, only a trigger that finally
        -- uses the name the schema already had.
        --
        -- Unconditional, and that is the simpler correct rule: from 'answered' it reopens the
        -- thread, and from 'open' it is a no-op in every way the client can observe, because
        -- FeedbackTriage folds 'in_process' and 'open'-that-needs-attention into the same
        -- "Perlu Ditanggapi" bucket. A conditional update would add a branch that changes nothing.
        --
        -- `answered_at` is deliberately left set. It records when the volunteer answered, which
        -- remains true; clearing it would destroy history in order to express something `status`
        -- already expresses.
        update public.feedback set status = 'in_process'
         where id = new.feedback_id;

        -- A parent's reply used to be silent, so a volunteer only learned of it by opening the
        -- thread. Fans out over anak_relawan exactly like on_feedback_insert.
        insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
        select ar.relawan_id, 'feedback_balasan', judul, ringkas, 'feedback', fb.id
          from public.anak_relawan ar
         where ar.anak_id = fb.anak_id;
    end if;
    -- An admin reply still notifies nobody. That was true before and stays true; it is called out
    -- because with two live branches above, a silent third one now looks like an oversight.

    return new;
end $$;

