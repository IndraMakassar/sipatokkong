package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackChallenge
import com.antoniusindra.sipatokkong.data.model.FeedbackMood
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

    private fun note(
        mood: String? = FeedbackMood.SENANG,
        kendala: String? = null,
        status: String = "open",
    ) = Feedback(
        id = "fb-1",
        anakId = "anak-1",
        orangtuaId = "ortu-1",
        kendala = kendala,
        mood = mood,
        isi = "Catatan rutin perkembangan anak.",
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
}
