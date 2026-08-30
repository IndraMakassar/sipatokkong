-- =====================================================================
-- Sipatokkong — Phase 5: one presensi per volunteer, per child, per day
--
-- `presensi` shipped in 0001 with no uniqueness at all, so a double tap on
-- the Simpan button writes two rows for the same visit. That silently
-- inflates every number on the admin's attendance recap, and the damage is
-- permanent: `presensi` has SELECT/INSERT/UPDATE policies but deliberately
-- no DELETE policy, so a duplicate row cannot be removed from the app.
--
-- Preventing it in the database is therefore the only safe place. A
-- volunteer who logged the wrong thing can still correct the existing row
-- through `presensi_update` (own rows only) — nothing is lost by refusing
-- the second insert.
--
-- Mirrors the constraint `tugas_penyelesaian` already carries on
-- (tugas_id, occurrence_date) for exactly the same reason.
-- =====================================================================

create unique index presensi_unik_harian
    on public.presensi (relawan_id, anak_id, tanggal);

comment on index public.presensi_unik_harian is
    'One attendance row per volunteer, per child, per day. A second insert raises 23505; the app
     maps that to "Kunjungan untuk anak ini pada tanggal tersebut sudah dicatat." rather than
     surfacing the raw Postgres error.';
