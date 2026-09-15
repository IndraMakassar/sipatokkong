-- =====================================================================
-- Sipatokkong — Phase 14: volunteer feedback on task completions
--
-- Adds feedback_relawan column to public.tugas_penyelesaian, so volunteers
-- can leave technical guidance or encouraging comments directly on the
-- parent's submitted task execution proof.
-- =====================================================================

alter table public.tugas_penyelesaian add column feedback_relawan text;

comment on column public.tugas_penyelesaian.feedback_relawan is
    'Feedback or text notes from the volunteer therapist regarding this task completion.';

create policy penyelesaian_update on public.tugas_penyelesaian
    for update to authenticated using (
        public.is_admin()
        or exists (select 1 from public.tugas t where t.id = tugas_id and t.relawan_id = auth.uid())
    ) with check (
        public.is_admin()
        or exists (select 1 from public.tugas t where t.id = tugas_id and t.relawan_id = auth.uid())
    );
