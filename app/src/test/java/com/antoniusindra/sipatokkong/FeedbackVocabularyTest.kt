package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.data.model.hasTypedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Did the parent actually write something?"
 *
 * Three callers depend on this one predicate and each would be wrong in a different way if it
 * drifted: `FeedbackSummary` would print one identical headline down the whole list,
 * `NotifDisplay.bodyLineOf` would repeat one identical sentence down the notification tray, and
 * `FeedbackTriage` would either flood "Perlu Ditanggapi" with every routine note or go on missing
 * the ones that say a child is ill.
 *
 * It works only because `CreateFeedbackViewModel` backfills an untouched "Catatan Tambahan" with
 * [CATATAN_RUTIN] -- the app records the *act* of typing, so nothing here has to interpret words.
 */
class FeedbackVocabularyTest {

    @Test
    fun `real text counts`() {
        assertTrue(hasTypedNote("anak demam 3 hari"))
        assertTrue(hasTypedNote("  ada kemajuan  "))
    }

    @Test
    fun `nothing at all does not count`() {
        assertFalse(hasTypedNote(null))
        assertFalse(hasTypedNote(""))
        assertFalse(hasTypedNote("     "))
        assertFalse(hasTypedNote("  " + System.lineSeparator() + "\t"))
    }

    @Test
    fun `the form's own backfill does not count`() {
        assertFalse(hasTypedNote(CATATAN_RUTIN))
    }

    @Test
    fun `the backfill does not count even when padded`() {
        // The value arrives from Postgres and, for the tray, through a trigger that slices it.
        // Trimming before comparing is what makes those paths agree with the form's.
        assertFalse(hasTypedNote("  $CATATAN_RUTIN  "))
    }

    @Test
    fun `a parent who types the backfill sentence and adds to it has said something`() {
        // Equality, not startsWith. This is the case that decides between the two, and getting it
        // wrong silently discards the only sentence that mattered.
        assertTrue(hasTypedNote("$CATATAN_RUTIN Tapi hari ini anak demam."))
    }

    @Test
    fun `the backfill is short enough that truncation cannot fake a match`() {
        // `on_feedback_insert` copies left(feedback.isi, 120) into the notification body. If the
        // filler were ever longer than that cut, a truncated real note could equal it and vanish
        // from the tray. 32 vs 120 today; this fails the moment that stops being true.
        assertTrue(CATATAN_RUTIN.length < 120)
        assertEquals(32, CATATAN_RUTIN.length)
    }
}
