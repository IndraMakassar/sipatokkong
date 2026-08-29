-- =====================================================================
-- Sipatokkong — Phase 6: reusable program ajar templates
--
-- A volunteer repeats the same exercise for different children, and for
-- the same child weeks later. Today every one of those means refilling
-- the whole form AND re-uploading the demo photo/video — the expensive
-- part, since nothing in the app compresses media and the bucket limit
-- is 50 MB.
--
-- A template therefore stores the EXERCISE and nothing about the
-- delivery: no anak_id, no deadline, no starts_on/ends_on, no status.
-- Those are asked fresh every time, because they are exactly what makes
-- one program different from the next. Everything a template does store
-- stays editable after it is applied — it prefills the existing form,
-- it does not lock anything.
--
-- Templates are shared: every volunteer sees every template, but only
-- the author can change or delete one. Note this is the opposite of the
-- teaching-aid suggestions, which are deliberately private (see the
-- 2026-08-16 Change Log entry) — aids are inferred from a volunteer's
-- own history, whereas a template is something they chose to publish.
-- =====================================================================

-- ------------------------------------------------------------------
-- 1. Table
-- ------------------------------------------------------------------
-- Mirrors the reusable subset of public.tugas. Compare column-for-column
-- with the tugas definition in 0001/0002/0004: what is MISSING here is
-- the point of the feature.
create table public.tugas_template (
    id                      uuid primary key default gen_random_uuid(),
    -- ON DELETE CASCADE, unlike tugas.relawan_id which is ON DELETE RESTRICT.
    -- Deliberate: a tugas is a record of care delivered and must survive its
    -- author; a template is a working convenience and should not block
    -- removing an account.
    relawan_id              uuid not null references public.profiles (id) on delete cascade,
    nama                    text not null,   -- how it reads in the template list
    judul                   text not null,
    instruksi               text,
    media_kind              public.media_kind not null,
    media_path              text not null,   -- in the `tugas-template` bucket, NOT `tugas-demo`
    priority_level          public.task_priority not null default 'medium',
    alat_bantu              text[],
    -- The recurrence PATTERN travels with the template; the dates never do.
    recurrence              public.recurrence_type not null default 'once',
    recurrence_days         smallint[],      -- ISO day-of-week 1=Mon..7=Sun, for 'weekly'
    recurrence_day_of_month smallint,        -- 1..31, for 'monthly'
    recurrence_interval     smallint,        -- every N days, for 'interval'
    created_at              timestamptz not null default now()
);

create index idx_template_relawan on public.tugas_template (relawan_id);

comment on table public.tugas_template is
    'Reusable recipe for a program ajar. Deliberately has no anak_id and no date columns: those
     are asked fresh each time the template is applied.';
comment on column public.tugas_template.nama is
    'Volunteer-facing name of the template, distinct from judul (which becomes the task title).';
comment on column public.tugas_template.media_path is
    'Object in the `tugas-template` bucket, path {relawan_id}/{yyyy}/{mm}/{uuid}.{ext}. Copied
     into `tugas-demo` under the target child on apply — see migration notes in section 3.';

-- ------------------------------------------------------------------
-- 2. RLS: shared to read, owned to change
-- ------------------------------------------------------------------
alter table public.tugas_template enable row level security;

-- Parents are excluded automatically: auth_role() returns 'orangtua' for them.
create policy template_select on public.tugas_template
    for select to authenticated
    using (public.is_admin() or public.auth_role() = 'relawan');
create policy template_insert on public.tugas_template
    for insert to authenticated with check (relawan_id = auth.uid());
create policy template_update on public.tugas_template
    for update to authenticated
    using (relawan_id = auth.uid()) with check (relawan_id = auth.uid());
-- The first DELETE policy in this schema. Every other table deliberately has none, because every
-- other table records care that was actually delivered. A template records nothing that happened,
-- so a volunteer removing one they no longer want loses no history.
create policy template_delete on public.tugas_template
    for delete to authenticated using (relawan_id = auth.uid());

-- ------------------------------------------------------------------
-- 3. Storage: a bucket keyed by volunteer, not by child
-- ------------------------------------------------------------------
-- Why a separate bucket at all: access to `tugas-demo` is decided by the FIRST PATH SEGMENT being
-- a child's UUID (see storage_child_id and the tugasdemo_* policies in 0001). A template belongs
-- to no child, and a shared template must be readable by a volunteer who is not assigned to the
-- child the media originally came from. Keeping template media in `tugas-demo` would make exactly
-- that case fail.
--
-- Media therefore makes two server-side copies and never passes through a phone twice:
--     save    tugas-demo/{anak_A}/…      -> tugas-template/{relawan_A}/…
--     apply   tugas-template/{relawan_A}/… -> tugas-demo/{anak_B}/…
--
-- The second copy is required, not an optimisation: afterwards the path starts with anak_B, which
-- is what lets that child's PARENT read it through is_parent_of. Reusing the original path would
-- leave the parent unable to open the demo at all.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('tugas-template', 'tugas-template', false, 52428800,
        array['image/jpeg','image/webp','video/mp4'])
on conflict (id) do nothing;

-- Same extraction as storage_child_id(), under a truthful name: in THIS bucket the first path
-- segment is the owning volunteer, because a template belongs to no child.
create or replace function public.storage_owner_id(object_name text)
returns uuid language sql immutable set search_path = '' as $$
    select nullif((storage.foldername(object_name))[1], '')::uuid
$$;

-- Readable by every volunteer (templates are shared); writable only under your own id.
create policy tugastemplate_read on storage.objects
    for select to authenticated using (
        bucket_id = 'tugas-template'
        and (public.is_admin() or public.auth_role() = 'relawan')
    );
create policy tugastemplate_write on storage.objects
    for insert to authenticated with check (
        bucket_id = 'tugas-template' and public.storage_owner_id(name) = auth.uid()
    );
