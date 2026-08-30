package com.antoniusindra.sipatokkong.ui.feedback

import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackChallenge
import com.antoniusindra.sipatokkong.data.model.FeedbackMood
import com.antoniusindra.sipatokkong.data.model.hasTypedNote

/** Status value written by the `on_balasan_insert` trigger once a volunteer has replied. */
const val FEEDBACK_STATUS_ANSWERED = "answered"

/**
 * Status written by `on_balasan_insert` (migration `0011`) when the **parent** replies.
 *
 * The `feedback_status` enum has declared `'in_process'` since `0001` and nothing has ever written
 * or read it, so this needed no schema change -- only a trigger that finally uses the state the
 * schema already had a name for.
 *
 * It exists because a thread the volunteer answered could be answered *back* and nothing said so.
 * The volunteer's "Perlu Ditanggapi" list emptied and stayed empty while a parent waited.
 */
const val FEEDBACK_STATUS_IN_PROCESS = "in_process"

/**
 * What a feedback row says about itself.
 *
 * Three states, not two. The old pair ("Menunggu" / "Dijawab") could not express "positive, and
 * nobody needs to do anything about it", which is why such a note wore a nagging orange chip.
 */
enum class FeedbackState { PERLU_DIBALAS, DIJAWAB, KABAR_BAIK }

/**
 * Splits a volunteer's feedback inbox into work and good news.
 *
 * Without this the inbox is one time-ordered list, so "anak demam, terapi dilewat" sits between ten
 * notes saying everything went fine. Both still need closing — that is what the quick-reply chips
 * are for — but only one of them needs *reading first*.
 *
 * Every input is already on the feedback row, so this is a pure filter over the list the screen has
 * in hand. No extra column, no second query: this app has a hard data-frugality requirement, and
 * `FeedbackRepository.list()` already returns everything RLS permits.
 *
 * Kept free of Compose and Supabase types so the rule can be unit-tested directly.
 */
object FeedbackTriage {

    /**
     * True when a note is still open **and** reports something a volunteer should look at.
     *
     * Answered notes always drop out, whatever their mood: once the volunteer has replied, the
     * thread has had its attention, and leaving it in the work list would mean the list never
     * empties — the exact problem this split exists to fix.
     */
    fun needsAttention(fb: Feedback): Boolean {
        // One column holds both values, so these two branches are mutually exclusive and their
        // order is NOT load-bearing -- the same property `stateOf` documents about its own. Written
        // first because it reads in the order a thread travels: answered, then answered back.
        //
        // What matters is that this branch exists at all. Without it a reply from the parent falls
        // through to the `answered` check below and is filed as settled, which is the defect.
        if (fb.status == FEEDBACK_STATUS_IN_PROCESS) return true
        if (fb.status == FEEDBACK_STATUS_ANSWERED) return false
        if (fb.mood in FeedbackMood.CONCERNING) return true
        // A parent who typed is a parent who had something to say. This rule reads no words: the
        // form backfills an untouched "Catatan Tambahan" with CATATAN_RUTIN, so the *act* of typing
        // is already recorded in the data and needs no keyword list to detect -- which matters,
        // because a keyword list for Indonesian free text written by low-literacy users would be
        // wrong in both directions and impossible to maintain.
        //
        // It over-triages on purpose. "Alhamdulillah anak senang sekali" lands in Perlu Ditanggapi
        // and costs a volunteer one read. The opposite error -- "anak demam 3 hari" filed under
        // Kabar Baik because the mood chip said Senang -- costs a child. These are not symmetric.
        if (hasTypedNote(fb.isi)) return true
        // Split rather than substring-match: `kendala` is a joined list, and a naive `contains`
        // would also fire on a future chip that merely embeds one of these names.
        return FeedbackChallenge.parse(fb.kendala).any { it in FeedbackChallenge.CONCERNING }
    }

    /**
     * The one state a note is in, for the chip on its row.
     *
     * Derived from [needsAttention] rather than from `status` alone, and that is the whole point:
     * the tabs and the chip used to come from two different notions, so a positive unanswered note
     * sat under "Sudah Beres" wearing an orange "Menunggu" chip -- the row contradicting the tab
     * above it. Sharing this one function makes that disagreement impossible to reintroduce.
     *
     * The two conditions cannot both hold: [needsAttention] already returns false for anything
     * answered, so the branches are independent and their order is not load-bearing. The [DIJAWAB]
     * check stays first only because it reads in the order a note actually travels.
     */
    fun stateOf(fb: Feedback): FeedbackState = when {
        fb.status == FEEDBACK_STATUS_ANSWERED -> FeedbackState.DIJAWAB
        needsAttention(fb) -> FeedbackState.PERLU_DIBALAS
        else -> FeedbackState.KABAR_BAIK
    }

    /** The notes a volunteer should work through first. */
    fun needsAttention(items: List<Feedback>): List<Feedback> = items.filter(::needsAttention)

    /**
      * Everything else: positive notes, and anything already replied to.
      *
      * Named for what the bucket *is* -- settled -- rather than "good news", which it was called
      * first and which was wrong: a note about a sick child lands here the moment it is answered.
      */
    fun settled(items: List<Feedback>): List<Feedback> = items.filterNot(::needsAttention)
}
