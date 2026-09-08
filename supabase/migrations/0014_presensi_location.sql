-- Add location tracking to volunteer visits.
-- Latitude and Longitude are stored as float8 (double precision).
-- Both are nullable to avoid breaking any rows written during the deployment gap
-- or in cases where location cannot be resolved.

alter table public.presensi
    add column latitude float8,
    add column longitude float8;

comment on column public.presensi.latitude is 'Latitude of the volunteer when logging the visit.';
comment on column public.presensi.longitude is 'Longitude of the volunteer when logging the visit.';
