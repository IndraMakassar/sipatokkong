package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackChallenge
import com.antoniusindra.sipatokkong.data.model.FeedbackMood
import com.antoniusindra.sipatokkong.ui.feedback.FEEDBACK_STATUS_ANSWERED
import com.antoniusindra.sipatokkong.ui.feedback.FEEDBACK_STATUS_IN_PROCESS
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackState
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackTriage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which behavior notes land in the volunteer's "Perlu Ditanggapi" list.
 *
 * Worth pinning down because the obvious rule is wrong: the column is called `kendala`, but two of
 * the five chips that write it ("Lancar", "Banyak Kemajuan") report that things went *well*.
 * Treating a non-empty `kendala` as trouble would put almost every note in the urgent list and
 * defeat the split entirely.
 */
class FeedbackTriageTest {

    // `isi` defaults to the value the form backfills when the parent types nothing, because that
    // is the common note and the one every pre-existing test in this file assumes. It is a
    // parameter now: since the free-text rule landed, `isi` decides triage outcomes.
    private fun note(
        mood: String? = FeedbackMood.SENANG,
        kendala: String? = null,
        status: String = "open",
        isi: String = CATATAN_RUTIN,
    ) = Feedback(
        id = "fb-1",
        anakId = "anak-1",
        orangtuaId = "ortu-1",
        kendala = kendala,
        mood = mood,
        isi = isi,
        status = status,
    )

    private fun joined(vararg chips: String) = chips.joinToString(FeedbackChallenge.SEPARATOR)

    @Test
    fun `a distressed or sick child needs attention`() {
        assertTrue(FeedbackTriage.needsAttention(note(mood = FeedbackMood.REWEL)))
        assertTrue(FeedbackTriage.needsAttention(note(mood = FeedbackMood.SAKIT)))
    }

    @Test
    fun `a happy child with only positive chips does not`() {
        val fb = note(
            mood = FeedbackMood.SENANG,
            kendala = joined(FeedbackChallenge.LANCAR, FeedbackChallenge.BANYAK_KEMAJUAN),
        )
        assertFalse(FeedbackTriage.needsAttention(fb))
    }

    @Test
    fun `a difficulty chip counts even in the middle of the joined string`() {
        val fb = note(
            mood = FeedbackMood.BIASA,
            kendala = joined(
                FeedbackChallenge.LANCAR,
                FeedbackChallenge.MUDAH_LELAH,
                FeedbackChallenge.BANYAK_KEMAJUAN,
            ),
        )
        assertTrue(FeedbackTriage.needsAttention(fb))
    }

    @Test
    fun `each difficulty chip on its own is enough`() {
        FeedbackChallenge.CONCERNING.forEach { chip ->
            assertTrue(chip, FeedbackTriage.needsAttention(note(mood = FeedbackMood.BIASA, kendala = chip)))
        }
    }

    @Test
    fun `an answered note leaves the work list however bad it was`() {
        val fb = note(
            mood = FeedbackMood.SAKIT,
            kendala = joined(FeedbackChallenge.MENOLAK_LATIHAN),
            status = "answered",
        )
        assertFalse(FeedbackTriage.needsAttention(fb))
    }

    @Test
    fun `a note with no mood and no chips is good news, not work`() {
        // Legacy rows predate the mood field; they must not all pile into the urgent tab.
        assertFalse(FeedbackTriage.needsAttention(note(mood = null, kendala = null)))
        assertFalse(FeedbackTriage.needsAttention(note(mood = null, kendala = "")))
    }

    @Test
    fun `the two buckets partition the list with nothing lost or duplicated`() {
        val items = listOf(
            note(mood = FeedbackMood.SAKIT),
            note(mood = FeedbackMood.SENANG, kendala = FeedbackChallenge.LANCAR),
            note(mood = FeedbackMood.SAKIT, status = "answered"),
            note(mood = FeedbackMood.BIASA, kendala = FeedbackChallenge.SULIT_FOKUS),
        )
        val urgent = FeedbackTriage.needsAttention(items)
        val calm = FeedbackTriage.settled(items)

        assertEquals(2, urgent.size)
        assertEquals(2, calm.size)
        assertEquals(items.size, urgent.size + calm.size)
        assertTrue(urgent.none { it in calm })
    }

    /**
     * The chips are stored as text, so a rename here without a data migration would orphan every
     * row already saved. Pinning the exact strings makes that break the build instead.
     */
    @Test
    fun `stored chip and mood values are the ones the form has always written`() {
        assertEquals(
            listOf("Sulit Fokus", "Mudah Lelah", "Menolak Latihan", "Lancar", "Banyak Kemajuan"),
            FeedbackChallenge.ALL,
        )
        assertEquals(listOf("Senang", "Biasa", "Rewel", "Sakit"), FeedbackMood.ALL)
    }

    /** Matches the two hand-copied `getMoodEmoji` helpers this replaced, fallback included. */
    @Test
    fun `mood emoji match the helpers they replaced`() {
        assertEquals("\uD83D\uDE0A", FeedbackMood.emojiOf("Senang"))
        assertEquals("\uD83D\uDE10", FeedbackMood.emojiOf("Biasa"))
        assertEquals("\uD83D\uDE22", FeedbackMood.emojiOf("Rewel"))
        assertEquals("\uD83E\uDD12", FeedbackMood.emojiOf("Sakit"))
        assertEquals("\uD83D\uDCDD", FeedbackMood.emojiOf(null))
        assertEquals("\uD83D\uDCDD", FeedbackMood.emojiOf("Marah"))
    }

    // ---- a parent replying after an answer (migration 0011) ----

    @Test
    fun `a thread the parent replied to needs attention again`() {
        // The reported defect, at its smallest: a happy note with no chips, already answered, that
        // the parent then replied to. Everything except `status` says "nothing to see here".
        val fb = note(mood = FeedbackMood.SENANG, status = FEEDBACK_STATUS_IN_PROCESS)
        assertTrue(FeedbackTriage.needsAttention(fb))
    }

    @Test
    fun `in_process reads as work and answered reads as done`() {
        // Not a test of branch order. `status` holds one value, so the two checks in needsAttention
        // are mutually exclusive and swapping them changes nothing -- verified by doing it. What
        // this pins is that the two states map to opposite chips, which is the visible behaviour.
        assertEquals(FeedbackState.PERLU_DIBALAS, FeedbackTriage.stateOf(note(status = FEEDBACK_STATUS_IN_PROCESS)))
        assertEquals(FeedbackState.DIJAWAB, FeedbackTriage.stateOf(note(status = FEEDBACK_STATUS_ANSWERED)))
    }

    @Test
    fun `a reopened thread moves back out of the settled bucket`() {
        val reopened = note(status = FEEDBACK_STATUS_IN_PROCESS)
        val answered = note(status = FEEDBACK_STATUS_ANSWERED)
        assertEquals(listOf(reopened), FeedbackTriage.needsAttention(listOf(reopened, answered)))
        assertEquals(listOf(answered), FeedbackTriage.settled(listOf(reopened, answered)))
    }

    @Test
    fun `the in_process storage string is frozen`() {
        // Same contract as the mood/chip strings below: this value is compared verbatim against
        // what the Postgres trigger writes, so renaming it here silently stops reopening threads.
        assertEquals("in_process", FEEDBACK_STATUS_IN_PROCESS)
    }

    // ---- free text as a signal ----

    @Test
    fun `a happy note the parent typed into needs attention`() {
        // The second reported defect. Mood says Senang, no chips at all -- and the parent typed
        // that the child had a fever. Before this rule the note read "Kabar Baik".
        val fb = note(mood = FeedbackMood.SENANG, isi = "anak demam 3 hari, terapi dilewat")
        assertTrue(FeedbackTriage.needsAttention(fb))
        assertEquals(FeedbackState.PERLU_DIBALAS, FeedbackTriage.stateOf(fb))
    }

    @Test
    fun `the form's own backfill is not typing`() {
        // If this ever fails, EVERY note in the pilot lands in "Perlu Ditanggapi" -- mood-plus-chips
        // with no typing is the quickest way to use the form and therefore the common one.
        assertFalse(FeedbackTriage.needsAttention(note(isi = CATATAN_RUTIN)))
    }

    @Test
    fun `an empty or whitespace note is not typing`() {
        assertFalse(FeedbackTriage.needsAttention(note(isi = "")))
        assertFalse(FeedbackTriage.needsAttention(note(isi = "    ")))
        // Whitespace that is not a space, built without an escape so the fixture reads as
        // exactly what it is.
        assertFalse(FeedbackTriage.needsAttention(note(isi = "  " + System.lineSeparator())))
    }

    @Test
    fun `an answered thread stays settled however much the parent wrote`() {
        // Free text is a signal, not an override: the volunteer has already responded to this one,
        // and only a *reply* (in_process) brings it back. Otherwise the list would never empty,
        // which is the original problem the triage split exists to solve.
        val fb = note(status = FEEDBACK_STATUS_ANSWERED, isi = "anak demam 3 hari")
        assertFalse(FeedbackTriage.needsAttention(fb))
    }
}
