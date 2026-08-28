package com.antoniusindra.sipatokkong.ui.feedback

import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackChallenge

/**
 * What one feedback row should say, given that its most obvious field is often meaningless.
 *
 * `isi` was used as the headline directly, but the form's "Catatan Tambahan" is optional and
 * `CreateFeedbackViewModel` backfills an empty one with [CATATAN_RUTIN]. A parent who reports with
 * mood + chips and types nothing -- the quickest way to use the form, and so the common one -- ended
 * up with a list where every row read identically.
 *
 * Pure functions, kept out of the composable so the fallback order can be tested.
 */
object FeedbackSummary {

    /** The most specific thing this note actually says. Never blank. */
    fun headlineOf(fb: Feedback): String {
        val isi = fb.isi.trim()
        if (isi.isNotEmpty() && isi != CATATAN_RUTIN) return isi
        val kendala = fb.kendala?.trim().orEmpty()
        if (kendala.isNotEmpty()) return kendala
        return fb.mood?.trim()?.takeIf { it.isNotEmpty() } ?: "Catatan"
    }

    /**
     * The muted second line, or null when there is nothing left worth adding.
     *
     * Returns null when the chips have already been promoted to the headline; showing them twice
     * on one row is worse than showing them once.
     */
    fun subtitleOf(fb: Feedback): String? {
        val kendala = fb.kendala?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return kendala.takeIf { it != headlineOf(fb) }
    }

    /** Chips as a readable list, for anywhere that wants them broken out rather than joined. */
    fun challengesOf(fb: Feedback): List<String> = FeedbackChallenge.parse(fb.kendala)
}
