-- =====================================================================
-- Sipatokkong — Phase 1: schema + RLS + storage + triggers
-- Backend: Supabase (Postgres 17). All authorization is enforced HERE via
-- RLS because the Android client uses only the public anon key.
-- =====================================================================

-- ------------------------------------------------------------------
-- 1. Enums
-- ------------------------------------------------------------------
create type public.user_role        as enum ('admin', 'relawan', 'orangtua');
create type public.task_status      as enum ('assigned', 'submitted', 'done', 'overdue');
create type public.feedback_status  as enum ('open', 'in_process', 'answered');
create type public.task_priority     as enum ('low', 'medium', 'high');
create type public.media_kind       as enum ('image', 'video');
create type public.notif_type       as enum (
    'feedback_baru', 'feedback_balasan', 'tugas_baru',
    'tugas_reminder', 'tugas_missed', 'presensi'
);

-- ------------------------------------------------------------------
-- 2. Tables
-- ------------------------------------------------------------------

-- One row per auth user, all roles.
create table public.profiles (
    id             uuid primary key references auth.users (id) on delete cascade,
    role           public.user_role not null,
    full_name      text not null,
    phone          text,
    -- parent-specific (nullable; relevant when role = 'orangtua')
    family_status  text,
    address        text,
    quota_notes    text,
    -- volunteer-specific (nullable; relevant when role = 'relawan')
    specialization text,
    created_by     uuid references public.profiles (id),
    is_active      boolean not null default true,   -- doubles as volunteer active_status
    created_at     timestamptz not null default now()
);

-- A child belongs to exactly one parent account.
create table public.anak (
    id              uuid primary key default gen_random_uuid(),
    nama            text not null,
    tanggal_lahir   date,
    orangtua_id     uuid not null references public.profiles (id) on delete restrict,
    catatan_kondisi text,
    is_active       boolean not null default true,
    created_by      uuid references public.profiles (id),
    created_at      timestamptz not null default now()
);

-- Many-to-many: a child may be seen by several volunteers over time.
create table public.anak_relawan (
    anak_id     uuid not null references public.anak (id) on delete cascade,
    relawan_id  uuid not null references public.profiles (id) on delete cascade,
    assigned_at timestamptz not null default now(),
    primary key (anak_id, relawan_id)
);

-- Volunteer attendance / therapy session (proof photo).
create table public.presensi (
    id              uuid primary key default gen_random_uuid(),
    anak_id         uuid not null references public.anak (id) on delete cascade,
    relawan_id      uuid not null references public.profiles (id) on delete restrict,
    tanggal         date not null,
    catatan         text,
    foto_path       text,
    foto_thumb_path text,
    created_at      timestamptz not null default now()
);

-- Homework / weekly task. media_path NOT NULL enforces the "mandatory demo media" rule.
create table public.tugas (
    id               uuid primary key default gen_random_uuid(),
    anak_id          uuid not null references public.anak (id) on delete cascade,
    relawan_id       uuid not null references public.profiles (id) on delete restrict,
    judul            text not null,
    instruksi        text,
    media_kind       public.media_kind not null,
    media_path       text not null,
    media_thumb_path text,
    priority_level   public.task_priority not null default 'medium',
    deadline         date,
    week_of          date,
    status           public.task_status not null default 'assigned',
    created_at       timestamptz not null default now()
);

-- Parent proof of child doing task at home.
create table public.dokumentasi (
    id               uuid primary key default gen_random_uuid(),
    anak_id          uuid not null references public.anak (id) on delete cascade,
    orangtua_id      uuid not null references public.profiles (id) on delete restrict,
    tugas_id         uuid references public.tugas (id) on delete set null,
    media_kind       public.media_kind not null,
    media_path       text not null,
    media_thumb_path text,
    keterangan       text,
    created_at       timestamptz not null default now()
);

-- Parent marks a task done (one completion per task).
create table public.tugas_penyelesaian (
    id             uuid primary key default gen_random_uuid(),
    tugas_id       uuid not null unique references public.tugas (id) on delete cascade,
    orangtua_id    uuid not null references public.profiles (id) on delete restrict,
    dokumentasi_id uuid references public.dokumentasi (id) on delete set null,
    completed_at   timestamptz not null default now(),
    catatan        text
);

-- Behavior notes + feedback thread root.
create table public.feedback (
    id               uuid primary key default gen_random_uuid(),
    anak_id          uuid not null references public.anak (id) on delete cascade,
    orangtua_id      uuid not null references public.profiles (id) on delete restrict,
    catatan_perilaku text,             -- behavior_observation: kondisi anak setelah terapi
    kendala          text,             -- challenges_faced: kendala orang tua saat terapi di rumah
    isi              text not null,    -- feedback_text utama
    status           public.feedback_status not null default 'open',
    answered_at      timestamptz,
    created_at       timestamptz not null default now()
);

-- Reply thread on a feedback.
create table public.feedback_balasan (
    id          uuid primary key default gen_random_uuid(),
    feedback_id uuid not null references public.feedback (id) on delete cascade,
    author_id   uuid not null references public.profiles (id) on delete restrict,
    isi         text not null,
    created_at  timestamptz not null default now()
);

-- Per-user notification inbox (written by triggers / pg_cron only).
create table public.notifications (
    id           uuid primary key default gen_random_uuid(),
    recipient_id uuid not null references public.profiles (id) on delete cascade,
    type         public.notif_type not null,
    title        text,
    body         text,
    entity_table text,
    entity_id    uuid,
    is_read      boolean not null default false,
    created_at   timestamptz not null default now()
);

-- ------------------------------------------------------------------
-- 3. Indexes (Postgres does NOT auto-index FKs)
-- ------------------------------------------------------------------
create index idx_profiles_created_by       on public.profiles (created_by);
create index idx_anak_orangtua             on public.anak (orangtua_id);
create index idx_anak_relawan_relawan      on public.anak_relawan (relawan_id);
create index idx_presensi_relawan_tanggal  on public.presensi (relawan_id, tanggal);
create index idx_presensi_anak             on public.presensi (anak_id);
create index idx_tugas_anak_week           on public.tugas (anak_id, week_of);
create index idx_tugas_relawan_status      on public.tugas (relawan_id, status);
create index idx_dokumentasi_anak          on public.dokumentasi (anak_id);
create index idx_dokumentasi_orangtua      on public.dokumentasi (orangtua_id);
create index idx_penyelesaian_orangtua     on public.tugas_penyelesaian (orangtua_id);
create index idx_feedback_anak_status      on public.feedback (anak_id, status);
create index idx_feedback_orangtua         on public.feedback (orangtua_id);
create index idx_balasan_feedback          on public.feedback_balasan (feedback_id);
create index idx_notif_recipient           on public.notifications (recipient_id, is_read, created_at desc);

-- ------------------------------------------------------------------
-- 4. Helper functions (SECURITY DEFINER — bypass RLS to avoid recursion)
-- ------------------------------------------------------------------
create or replace function public.auth_role()
returns public.user_role
language sql stable security definer set search_path = ''
as $$ select role from public.profiles where id = auth.uid() $$;

create or replace function public.is_admin()
returns boolean
language sql stable security definer set search_path = ''
as $$ select exists (
    select 1 from public.profiles where id = auth.uid() and role = 'admin'
) $$;

create or replace function public.is_parent_of(child uuid)
returns boolean
language sql stable security definer set search_path = ''
as $$ select exists (
    select 1 from public.anak where id = child and orangtua_id = auth.uid()
) $$;

create or replace function public.is_relawan_of(child uuid)
returns boolean
language sql stable security definer set search_path = ''
as $$ select exists (
    select 1 from public.anak_relawan where anak_id = child and relawan_id = auth.uid()
) $$;

-- Profile visibility: self, admin, or a user linked to me through a shared child.
create or replace function public.can_view_profile(target uuid)
returns boolean
language sql stable security definer set search_path = ''
as $$
    select
        target = auth.uid()
        or public.is_admin()
        -- I am a parent; target is a volunteer assigned to my child
        or exists (
            select 1 from public.anak a
            join public.anak_relawan ar on ar.anak_id = a.id
            where a.orangtua_id = auth.uid() and ar.relawan_id = target
        )
        -- I am a volunteer; target is the parent of a child assigned to me
        or exists (
            select 1 from public.anak_relawan ar
            join public.anak a on a.id = ar.anak_id
            where ar.relawan_id = auth.uid() and a.orangtua_id = target
        )
$$;

-- ------------------------------------------------------------------
-- 5. Enable RLS on every table (default-deny)
-- ------------------------------------------------------------------
alter table public.profiles           enable row level security;
alter table public.anak               enable row level security;
alter table public.anak_relawan       enable row level security;
alter table public.presensi           enable row level security;
alter table public.tugas              enable row level security;
alter table public.dokumentasi        enable row level security;
alter table public.tugas_penyelesaian enable row level security;
alter table public.feedback           enable row level security;
alter table public.feedback_balasan   enable row level security;
alter table public.notifications      enable row level security;

-- ---- profiles ----
create policy profiles_select on public.profiles
    for select to authenticated using (public.can_view_profile(id));
create policy profiles_insert on public.profiles
    for insert to authenticated with check (public.is_admin());
create policy profiles_update on public.profiles
    for update to authenticated
    using (id = auth.uid() or public.is_admin())
    with check (id = auth.uid() or public.is_admin());

-- ---- anak ----
create policy anak_select on public.anak
    for select to authenticated
    using (public.is_admin() or public.is_parent_of(id) or public.is_relawan_of(id));
create policy anak_insert on public.anak
    for insert to authenticated with check (public.is_admin());
create policy anak_update on public.anak
    for update to authenticated using (public.is_admin()) with check (public.is_admin());

-- ---- anak_relawan ----
create policy anak_relawan_select on public.anak_relawan
    for select to authenticated
    using (public.is_admin() or relawan_id = auth.uid() or public.is_parent_of(anak_id));
create policy anak_relawan_insert on public.anak_relawan
    for insert to authenticated with check (public.is_admin());
create policy anak_relawan_delete on public.anak_relawan
    for delete to authenticated using (public.is_admin());

-- ---- presensi ----
create policy presensi_select on public.presensi
    for select to authenticated
    using (public.is_admin() or relawan_id = auth.uid() or public.is_parent_of(anak_id));
create policy presensi_insert on public.presensi
    for insert to authenticated
    with check (relawan_id = auth.uid() and public.is_relawan_of(anak_id));
create policy presensi_update on public.presensi
    for update to authenticated
    using (relawan_id = auth.uid()) with check (relawan_id = auth.uid());

-- ---- tugas ---- (INSERT requires media_path — reinforces mandatory media)
create policy tugas_select on public.tugas
    for select to authenticated
    using (public.is_admin() or relawan_id = auth.uid() or public.is_parent_of(anak_id));
create policy tugas_insert on public.tugas
    for insert to authenticated
    with check (relawan_id = auth.uid() and public.is_relawan_of(anak_id) and media_path is not null);
create policy tugas_update on public.tugas
    for update to authenticated
    using (relawan_id = auth.uid()) with check (relawan_id = auth.uid());

-- ---- dokumentasi ----
create policy dokumentasi_select on public.dokumentasi
    for select to authenticated
    using (public.is_admin() or orangtua_id = auth.uid() or public.is_relawan_of(anak_id));
create policy dokumentasi_insert on public.dokumentasi
    for insert to authenticated
    with check (orangtua_id = auth.uid() and public.is_parent_of(anak_id));
create policy dokumentasi_update on public.dokumentasi
    for update to authenticated
    using (orangtua_id = auth.uid()) with check (orangtua_id = auth.uid());

-- ---- tugas_penyelesaian ----
create policy penyelesaian_select on public.tugas_penyelesaian
    for select to authenticated using (
        public.is_admin()
        or orangtua_id = auth.uid()
        or exists (select 1 from public.tugas t where t.id = tugas_id and t.relawan_id = auth.uid())
    );
create policy penyelesaian_insert on public.tugas_penyelesaian
    for insert to authenticated with check (
        orangtua_id = auth.uid()
        and exists (select 1 from public.tugas t where t.id = tugas_id and public.is_parent_of(t.anak_id))
    );

-- ---- feedback ----
create policy feedback_select on public.feedback
    for select to authenticated
    using (public.is_admin() or orangtua_id = auth.uid() or public.is_relawan_of(anak_id));
create policy feedback_insert on public.feedback
    for insert to authenticated
    with check (orangtua_id = auth.uid() and public.is_parent_of(anak_id));
create policy feedback_update on public.feedback
    for update to authenticated
    using (orangtua_id = auth.uid() or public.is_relawan_of(anak_id))
    with check (orangtua_id = auth.uid() or public.is_relawan_of(anak_id));

-- ---- feedback_balasan ---- (visible to anyone who can see the parent feedback row)
create policy balasan_select on public.feedback_balasan
    for select to authenticated using (
        exists (
            select 1 from public.feedback f
            where f.id = feedback_id
              and (public.is_admin() or f.orangtua_id = auth.uid() or public.is_relawan_of(f.anak_id))
        )
    );
create policy balasan_insert on public.feedback_balasan
    for insert to authenticated with check (
        author_id = auth.uid()
        and exists (
            select 1 from public.feedback f
            where f.id = feedback_id
              and (f.orangtua_id = auth.uid() or public.is_relawan_of(f.anak_id))
        )
    );

-- ---- notifications ---- (no client INSERT policy; written by SECURITY DEFINER triggers)
create policy notif_select on public.notifications
    for select to authenticated using (recipient_id = auth.uid());
create policy notif_update on public.notifications
    for update to authenticated
    using (recipient_id = auth.uid()) with check (recipient_id = auth.uid());

-- ------------------------------------------------------------------
-- 6. Triggers: status transitions + notification fan-out
-- ------------------------------------------------------------------

-- New tugas -> notify the child's parent + notification row.
create or replace function public.on_tugas_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
declare parent_id uuid;
begin
    select orangtua_id into parent_id from public.anak where id = new.anak_id;
    if parent_id is not null then
        insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
        values (parent_id, 'tugas_baru', 'Tugas rumah baru', new.judul, 'tugas', new.id);
    end if;
    return new;
end $$;
create trigger trg_tugas_insert after insert on public.tugas
    for each row execute function public.on_tugas_insert();

-- Parent completes a task -> mark tugas done.
create or replace function public.on_penyelesaian_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    update public.tugas set status = 'done' where id = new.tugas_id;
    return new;
end $$;
create trigger trg_penyelesaian_insert after insert on public.tugas_penyelesaian
    for each row execute function public.on_penyelesaian_insert();

-- New feedback -> notify all volunteers assigned to the child.
create or replace function public.on_feedback_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
    select ar.relawan_id, 'feedback_baru', 'Feedback baru dari orang tua',
           left(new.isi, 120), 'feedback', new.id
    from public.anak_relawan ar
    where ar.anak_id = new.anak_id;
    return new;
end $$;
create trigger trg_feedback_insert after insert on public.feedback
    for each row execute function public.on_feedback_insert();

-- Volunteer replies -> advance feedback status + notify the parent.
create or replace function public.on_balasan_insert()
returns trigger language plpgsql security definer set search_path = '' as $$
declare author_role public.user_role; fb public.feedback;
begin
    select role into author_role from public.profiles where id = new.author_id;
    select * into fb from public.feedback where id = new.feedback_id;
    if author_role = 'relawan' then
        update public.feedback set status = 'answered', answered_at = now() where id = new.feedback_id;
        insert into public.notifications (recipient_id, type, title, body, entity_table, entity_id)
        values (fb.orangtua_id, 'feedback_balasan', 'Balasan feedback',
                left(new.isi, 120), 'feedback', fb.id);
    end if;
    return new;
end $$;
create trigger trg_balasan_insert after insert on public.feedback_balasan
    for each row execute function public.on_balasan_insert();

-- ------------------------------------------------------------------
-- 7. Storage buckets (private) + storage RLS
--    Path convention: {anak_id}/{yyyy}/{mm}/{uuid}.{ext}  (child id is folder[1])
-- ------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('presensi',   'presensi',   false, 6291456,  array['image/jpeg','image/webp']),
    ('tugas-demo', 'tugas-demo', false, 52428800, array['image/jpeg','image/webp','video/mp4']),
    ('dokumentasi','dokumentasi',false, 52428800, array['image/jpeg','image/webp','video/mp4'])
on conflict (id) do nothing;

-- Helper: extract child id (first path segment) from a storage object name.
create or replace function public.storage_child_id(object_name text)
returns uuid language sql immutable set search_path = '' as $$
    select nullif((storage.foldername(object_name))[1], '')::uuid
$$;

-- presensi bucket: volunteers write for their children; parent + admin can read.
create policy presensi_read on storage.objects
    for select to authenticated using (
        bucket_id = 'presensi' and (
            public.is_admin()
            or public.is_relawan_of(public.storage_child_id(name))
            or public.is_parent_of(public.storage_child_id(name))
        )
    );
create policy presensi_write on storage.objects
    for insert to authenticated with check (
        bucket_id = 'presensi' and public.is_relawan_of(public.storage_child_id(name))
    );

-- tugas-demo bucket: volunteers upload demo; parent + admin can read.
create policy tugasdemo_read on storage.objects
    for select to authenticated using (
        bucket_id = 'tugas-demo' and (
            public.is_admin()
            or public.is_relawan_of(public.storage_child_id(name))
            or public.is_parent_of(public.storage_child_id(name))
        )
    );
create policy tugasdemo_write on storage.objects
    for insert to authenticated with check (
        bucket_id = 'tugas-demo' and public.is_relawan_of(public.storage_child_id(name))
    );

-- dokumentasi bucket: parents upload; linked volunteers + admin can read.
create policy dokumentasi_read on storage.objects
    for select to authenticated using (
        bucket_id = 'dokumentasi' and (
            public.is_admin()
            or public.is_parent_of(public.storage_child_id(name))
            or public.is_relawan_of(public.storage_child_id(name))
        )
    );
create policy dokumentasi_write on storage.objects
    for insert to authenticated with check (
        bucket_id = 'dokumentasi' and public.is_parent_of(public.storage_child_id(name))
    );
