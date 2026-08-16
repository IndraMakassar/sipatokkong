-- =====================================================================
-- Sipatokkong — Phase 4: teaching aids ("alat bantu") on a program ajar
--
-- A task may involve several household aids. The volunteer picks them when
-- creating the program; the parent only reads them.
--
-- Stored as text[] rather than a Postgres enum on purpose: the option list
-- lives as a constant in the app, so an enum would make adding one item
-- require BOTH an APK release and a migration. With text[] the release
-- alone is enough. Array columns are already proven here — see
-- `tugas.recurrence_days smallint[]` from migration 0002.
-- =====================================================================

alter table public.tugas add column alat_bantu text[];

comment on column public.tugas.alat_bantu is
    'Teaching aids used for this task. Values mirror TeachingAid.dbValue in the app. Nullable:
     optional in the form, and absent on tasks created before this migration.';
