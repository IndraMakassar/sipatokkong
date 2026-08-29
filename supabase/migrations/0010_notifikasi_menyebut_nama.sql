-- =====================================================================
-- Sipatokkong — Phase 7: notifications that say who is talking
--
-- Until now all three triggers wrote constant titles, so a parent was
-- told *that* someone replied but never *who*, and a volunteer got a row
-- that could have been about any of their assigned children.
--
-- Every name added here is already readable by the recipient through
-- their own RLS policies -- `can_view_profile` admits a parent to their
-- child's volunteers and a volunteer to their children's parents, and
-- `anak_select` admits both to the child. So this discloses nothing new;
-- it only stops the app pretending it does not know.
--
-- Also new: a parent's reply finally notifies the assigned volunteers.
-- Until now `on_balasan_insert` did nothing at all unless the author was
-- a relawan, so a volunteer started a conversation and never learned it
-- had been answered.
--
-- `create or replace function` only. The `create trigger` statements in
-- 0001 already point at these names and must NOT be re-run.
-- =====================================================================

-- ------------------------------------------------------------------
-- 1. Snippet helper
-- ------------------------------------------------------------------

-- One line, no markup, for a notification body.
--
-- `tugas.instruksi` is a plain-text column edited through a formatting toolbar that writes literal
-- `###`, `**` and `- ` markers (see CreateTaskScreen), which `RichTextUtil.parseMarkdown` renders
-- on the device. A notification body is drawn by the system, which parses nothing -- so a raw slice
-- would ship `### Langkah 1 - Ajak anak **memegang** sendok` straight to a parent's tray.
--
-- Also used on free-text notes, where the marker stripping is a no-op and the whitespace collapse
-- is the point: a notification body is one line, and a parent's note is not.
--
-- Returns null rather than an empty string, so callers can drop the line entirely -- the same
-- contract `NotifDisplay.bodyLineOf` has on the client.
create or replace function public.notif_snippet(src text, max_len int default 120)
returns text
language sql
immutable
set search_path = ''
as $$
    with cleaned as (
        select btrim(
            regexp_replace(
                regexp_replace(
                    regexp_replace(
                        coalesce(src, ''),
                        -- Line-leading block markers. The `n` flag is load-bearing: it makes `^`
                        -- match at every line start, which is the only place these appear.
                        '^[ \t]*(#{1,6}|[-*+]|[0-9]+\.)[ \t]+', '', 'gn'),
                    '\*\*|__|`', '', 'g'),      -- inline emphasis
                '[[:space:]]+', ' ', 'g')       -- every run of whitespace, newlines included
        ) as t
    )
    select case
        when t = ''              then null
        when length(t) > max_len then left(t, max_len - 1) || '…'
        else t
    end
    from cleaned;
$$;

comment on function public.notif_snippet is
    'One-line, marker-free excerpt for a notification body. Null when there is nothing worth saying.';

-- ------------------------------------------------------------------
-- 2. A new program ajar -> the child's parent
-- ------------------------------------------------------------------

-- "Kak Dewi memberi tugas untuk Andi" / "Latihan Sensori — Ajak anak menyentuh tekstur kain."
--
-- This one keeps a sentence rather than the bare "name · child" the feedback kinds use, because a
-- task is not a message from a person -- the parent needs to know what arrived, not just who sent
-- it. Judul and a stripped slice of the instructions, as asked.
create or replace function public.on_tugas_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    parent_id    uuid;
    anak_nama    text;
    relawan_nama text;
    deskripsi    text;
begin
    -- One row, two columns: the parent lookup was already here, so the child's name costs nothing.
    select a.orangtua_id, a.nama into parent_id, anak_nama
      from public.anak a where a.id = new.anak_id;
    if parent_id is null then
        return new;
    end if;

    select p.full_name into relawan_nama
      from public.profiles p where p.id = new.relawan_id;

    deskripsi := public.notif_snippet(new.instruksi, 100);

    insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
    values (
        parent_id,
        'tugas_baru',
        case
            when coalesce(btrim(relawan_nama), '') = ''
                then 'Tugas rumah baru untuk ' || anak_nama
            else btrim(relawan_nama) || ' memberi tugas untuk ' || anak_nama
        end,
        case when deskripsi is null then new.judul else new.judul || ' — ' || deskripsi end,
        'tugas',
        new.id
    );
    return new;
end $$;

-- ------------------------------------------------------------------
-- 3. A parent's note -> every assigned volunteer
-- ------------------------------------------------------------------

-- "Sari Wulandari · Andi" / the note.
--
-- The title is deliberately person-shaped: it becomes the `Person` name in the phone's notification
-- tray, which is what gives it the messaging layout and the round avatar. The cost is that the word
-- "feedback" leaves the title -- acceptable here because the inbox card still carries the kind in
-- its icon, and a volunteer opening a notification already knows what this app is for.
create or replace function public.on_feedback_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    anak_nama text;
    ortu_nama text;
    ringkas   text;
begin
    select a.nama      into anak_nama from public.anak a     where a.id = new.anak_id;
    select p.full_name into ortu_nama from public.profiles p where p.id = new.orangtua_id;

    -- `isi` is `not null` while the form's "Catatan Tambahan" is optional, so the app backfills it
    -- with a filler sentence -- and mood-plus-chips with no typing is the quickest way to file a
    -- note, therefore the common one. Copied literally, a volunteer's tray repeats one identical
    -- sentence down its whole length. Mood, kendala and catatan_perilaku are already on this row
    -- and actually say something.
    --
    -- The literal below is FeedbackVocabulary.CATATAN_RUTIN, which now exists in two languages.
    -- Renaming it in Kotlin without changing it here silently reverts these bodies to the filler.
    -- `NotifDisplay.bodyLineOf` is the client-side backstop that would still drop it.
    ringkas := public.notif_snippet(
        case when btrim(new.isi) = 'Catatan rutin perkembangan anak.'
             then nullif(btrim(concat_ws(' · ',
                      nullif(btrim(coalesce(new.mood, '')), ''),
                      nullif(btrim(coalesce(new.kendala, '')), ''),
                      nullif(btrim(coalesce(new.catatan_perilaku, '')), ''))), '')
             else new.isi
        end, 120);

    insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
    select ar.relawan_id,
           'feedback_baru',
           coalesce(nullif(btrim(ortu_nama), ''), 'Orang tua') || ' · ' || anak_nama,
           ringkas,
           'feedback',
           new.id
      from public.anak_relawan ar
     where ar.anak_id = new.anak_id;
    return new;
end $$;

-- ------------------------------------------------------------------
-- 4. A reply -> the other side, in both directions
-- ------------------------------------------------------------------

-- Relawan replies  -> the parent, and the thread becomes 'answered'.
-- Orang tua replies -> every assigned volunteer. NEW; this produced nothing at all before.
-- Admin replies    -> nobody, exactly as before.
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
        -- A parent's reply used to be silent, so a volunteer only learned of it by opening the
        -- thread. Fans out over anak_relawan exactly like on_feedback_insert.
        --
        -- Deliberately does NOT touch `status`: a parent answering does not make the thread
        -- answered, and moving it would empty the volunteer's "Perlu Ditanggapi" list -- the
        -- opposite of the point.
        insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
        select ar.relawan_id, 'feedback_balasan', judul, ringkas, 'feedback', fb.id
          from public.anak_relawan ar
         where ar.anak_id = fb.anak_id;
    end if;
    -- An admin reply still notifies nobody. That was true before and stays true; it is called out
    -- because with two live branches above, a silent third one now looks like an oversight.

    return new;
end $$;

-- ---------------------------------------------------------------------
-- Rows written before this migration keep their old constant wording
-- ('Tugas rumah baru', 'Feedback baru dari orang tua', 'Balasan
-- feedback'). Deliberately not backfilled: it shows only as a seam in the
-- in-app inbox, those pushes fired long ago, and rewriting them would mean
-- joining through entity_id into rows that may since have been deleted.
-- ---------------------------------------------------------------------
