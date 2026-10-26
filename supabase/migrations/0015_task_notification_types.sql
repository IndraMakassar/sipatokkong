-- Commit this migration before 0016: PostgreSQL cannot use new enum values
-- in the transaction that adds them. No data changes or notification backfill.
alter type public.notif_type add value if not exists 'tugas_setoran';
alter type public.notif_type add value if not exists 'tugas_evaluasi';
