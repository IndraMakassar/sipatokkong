package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.util.formatIsoDate
import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackChallenge
import com.antoniusindra.sipatokkong.data.model.FeedbackMood
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackListUiState
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackState
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackSummary
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackTriage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * What a feedback row says, and whether it can contradict the tab above it.
 *
 * The contradiction is the reason this file exists: the chip came from `feedback.status` while the
 * tabs came from `needsAttention`, so a positive unanswered note sat under "Sudah Beres" wearing an
 * orange "Menunggu". Both now come from `stateOf`, and the first test below is what holds them
 * together.
 */
class FeedbackDisplayTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 29)

    private fun note(
        isi: String = "Anak lebih tenang minggu ini",
        kendala: String? = null,
        mood: String? = FeedbackMood.SENANG,
        status: String = "open",
        createdAt: String? = "2026-08-29T08:00:00Z",
    ) = Feedback(
        id = "fb-1",
        anakId = "anak-1",
        orangtuaId = "ortu-1",
        kendala = kendala,
        mood = mood,
        isi = isi,
        status = status,
        createdAt = createdAt,
    )

    // ---------------------------------------------------------------- chip vs tab

    @Test
    fun `the chip and the tab can never disagree`() {
        // PERLU_DIBALAS if and only if needsAttention. This is the invariant the old code broke.
        val cases = listOf(
            note(mood = FeedbackMood.SENANG),
            note(mood = FeedbackMood.SAKIT),
            note(mood = FeedbackMood.BIASA, kendala = FeedbackChallenge.SULIT_FOKUS),
            note(mood = FeedbackMood.BIASA, kendala = FeedbackChallenge.LANCAR),
            note(mood = FeedbackMood.SAKIT, status = "answered"),
            note(mood = FeedbackMood.SENANG, status = "answered"),
        )
        cases.forEach { fb ->
            assertEquals(
                "state and triage disagree for mood=${fb.mood} status=${fb.status}",
                FeedbackTriage.needsAttention(fb),
                FeedbackTriage.stateOf(fb) == FeedbackState.PERLU_DIBALAS,
            )
        }
    }

    @Test
    fun `a positive unanswered note is good news, not something waiting`() {
        // The exact row that used to read "Sudah Beres" and "Menunggu" at the same time.
        //
        // `isi` is pinned to the form's backfill rather than left on this file's default, which is
        // typed text. Since free text became a triage signal, a note the parent wrote into needs
        // attention whatever its mood -- so the default fixture would make this test pass or fail
        // for a reason that has nothing to do with chip-versus-tab agreement, which is its subject.
        val fb = note(
            mood = FeedbackMood.SENANG,
            kendala = "${FeedbackChallenge.LANCAR}, ${FeedbackChallenge.BANYAK_KEMAJUAN}",
            status = "open",
            isi = CATATAN_RUTIN,
        )
        assertEquals(FeedbackState.KABAR_BAIK, FeedbackTriage.stateOf(fb))
    }

    @Test
    fun `an answered note about a sick child is Dijawab, not Kabar Baik`() {
        // Branch order: needsAttention already returns false once answered, so testing it first
        // would file this under KABAR_BAIK.
        val fb = note(mood = FeedbackMood.SAKIT, status = "answered")
        assertEquals(FeedbackState.DIJAWAB, FeedbackTriage.stateOf(fb))
    }

    @Test
    fun `an unanswered note that needs attention asks to be replied to`() {
        assertEquals(FeedbackState.PERLU_DIBALAS, FeedbackTriage.stateOf(note(mood = FeedbackMood.REWEL)))
    }

    // ---------------------------------------------------------------- headline

    @Test
    fun `real free text wins as the headline`() {
        assertEquals("Anak lebih tenang minggu ini", FeedbackSummary.headlineOf(note()))
    }

    @Test
    fun `the auto-filled note falls through to the chips`() {
        // "Catatan Tambahan" is optional and gets backfilled, so without this every chip-only note
        // in the list would read identically.
        val fb = note(isi = CATATAN_RUTIN, kendala = "Sulit Fokus, Mudah Lelah")
        assertEquals("Sulit Fokus, Mudah Lelah", FeedbackSummary.headlineOf(fb))
    }

    @Test
    fun `with neither text nor chips it falls through to the mood`() {
        val fb = note(isi = CATATAN_RUTIN, kendala = null, mood = FeedbackMood.REWEL)
        assertEquals("Rewel", FeedbackSummary.headlineOf(fb))
    }

    @Test
    fun `a note with nothing at all still says something`() {
        val fb = note(isi = "   ", kendala = "  ", mood = null)
        assertEquals("Catatan", FeedbackSummary.headlineOf(fb))
    }

    @Test
    fun `the subtitle never repeats the headline`() {
        // Chips promoted to the headline must not also appear as the muted second line.
        val promoted = note(isi = CATATAN_RUTIN, kendala = "Sulit Fokus")
        assertNull(FeedbackSummary.subtitleOf(promoted))

        val kept = note(isi = "Anak menolak makan", kendala = "Sulit Fokus")
        assertEquals("Sulit Fokus", FeedbackSummary.subtitleOf(kept))

        assertNull(FeedbackSummary.subtitleOf(note(kendala = null)))
    }

    // ---------------------------------------------------------------- dates

    @Test
    fun `recent days are named rather than dated`() {
        assertEquals("Hari Ini", formatIsoDate("2026-08-29T08:00:00Z", today))
        assertEquals("Kemarin", formatIsoDate("2026-08-28T23:59:00Z", today))
    }

    @Test
    fun `older dates are formatted, not left as raw ISO`() {
        // The bug being fixed: the list used createdAt.take(10) and printed "2026-08-01".
        val shown = formatIsoDate("2026-08-01T08:00:00Z", today)
        assertEquals("01 Agu 2026", shown)
    }

    @Test
    fun `a missing or unusable timestamp yields no line at all`() {
        assertNull(formatIsoDate(null, today))
        assertNull(formatIsoDate("", today))
        assertNull(formatIsoDate("bukan tanggal", today))
    }

    // ---------------------------------------------------------------- who a note is about

    @Test
    fun `a volunteer sees the child first and then the parent`() {
        // Child first, and deliberately the reverse of migration 0010's notification title
        // ("Ibu Sari · Andi"). A tray entry answers "who is speaking"; this list answers
        // "which child", which is what a volunteer holding four of them is scanning for.
        assertEquals("Andi · Ibu Sari", FeedbackSummary.whoLine("Andi", "Ibu Sari"))
    }

    @Test
    fun `a half-known pair prints the half it knows`() {
        // A parent viewing their own note gets no parent name on purpose -- the ViewModel skips
        // that round trip -- so this is the ordinary parent-side case, not an error path.
        assertEquals("Andi", FeedbackSummary.whoLine("Andi", null))
        assertEquals("Ibu Sari", FeedbackSummary.whoLine(null, "Ibu Sari"))
    }

    @Test
    fun `nothing known prints no line at all`() {
        // Null, not "Anak tidak dikenal": a placeholder tells a volunteer nothing they can act on
        // and costs a row of height on a screen already dense with text.
        assertNull(FeedbackSummary.whoLine(null, null))
        assertNull(FeedbackSummary.whoLine("", "   "))
    }

    @Test
    fun `stored padding does not become visible padding`() {
        assertEquals("Andi · Ibu Sari", FeedbackSummary.whoLine("  Andi ", " Ibu Sari  "))
    }

    @Test
    fun `the list resolves a row through its own id maps`() {
        val fb = note()
        val ui = FeedbackListUiState(
            items = listOf(fb),
            childNames = mapOf(fb.anakId to "Andi"),
            parentNames = mapOf(fb.orangtuaId to "Ibu Sari"),
        )
        assertEquals("Andi · Ibu Sari", ui.whoOf(fb))
    }

    @Test
    fun `a row whose child was deactivated before the maps loaded shows nothing`() {
        // The parent view, where both maps are empty by design, and equally the volunteer view
        // mid-load. Neither may render a broken half-line.
        assertNull(FeedbackListUiState(items = listOf(note())).whoOf(note()))
    }
}
