-- =====================================================================
-- Sipatokkong — Phase 3: independence level + working duration
--
-- Until now the parent's answer about how the child responded was pushed
-- into `tugas_penyelesaian.catatan` as free text ("Respon anak: ..."),
-- which means it could never be counted, charted, or reported on.
--
-- The new scale measures INDEPENDENCE, not mood, so old values cannot be
-- mapped onto it. There is deliberately no backfill: legacy rows keep
-- their text in `catatan` and leave both new columns NULL. Forcing a
-- mapping would manufacture data that was never collected.
-- =====================================================================

create type public.tingkat_kemandirian as enum (
    'mandiri',          -- does it alone
    'dengan_bantuan',   -- needs some help
    'bantuan_penuh',    -- needs full hand-over-hand help
    'belum_bisa'        -- not yet able
);

create type public.durasi_pengerjaan as enum (
    'kurang_5',   -- under 5 minutes
    '5_15',
    '15_30',
    'lebih_30'    -- over 30 minutes
);

alter table public.tugas_penyelesaian
    add column kemandirian public.tingkat_kemandirian,
    add column durasi      public.durasi_pengerjaan;

comment on column public.tugas_penyelesaian.kemandirian is
    'How independently the child completed this occurrence. Nullable: optional in the form, and
     absent on rows recorded before this migration.';
comment on column public.tugas_penyelesaian.durasi is
    'Rough time bucket the child spent. Ranges rather than minutes, so low-literacy parents answer
     with one tap instead of estimating a number.';
