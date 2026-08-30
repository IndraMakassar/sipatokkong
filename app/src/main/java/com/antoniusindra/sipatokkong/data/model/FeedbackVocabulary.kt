package com.antoniusindra.sipatokkong.data.model

/**
 * The four moods a parent can pick on the behavior-note form.
 *
 * Stored as plain text in `feedback.mood` (see migration `0005`), not a Postgres enum, so the list
 * can grow with an APK release alone — the same trade-off [TeachingAid] makes.
 *
 * These strings are the storage format, not just labels: they are written to the database verbatim
 * and compared verbatim by [FeedbackTriage]. Renaming one here without a data migration would
 * orphan every row already saved under the old spelling.
 */
object FeedbackMood {
    const val SENANG = "Senang"
    const val BIASA = "Biasa"
    const val REWEL = "Rewel"
    const val SAKIT = "Sakit"

    /** Form order, left to right: best to worst. */
    val ALL = listOf(SENANG, BIASA, REWEL, SAKIT)

    /**
     * Moods that mean a volunteer should look at the note rather than just acknowledge it.
     *
     * A sick or distressed child is the one case where the note is news the volunteer has to act
     * on, so these are what pull a note into the "Perlu Ditanggapi" list.
     */
    val CONCERNING = setOf(REWEL, SAKIT)

    private val EMOJI = mapOf(
        SENANG to "😊",
        BIASA to "😐",
        REWEL to "😢",
        SAKIT to "🤒",
    )

    /**
     * The emoji shown for a stored mood.
     *
     * Falls back to "📝" for null or unrecognised values, which covers legacy rows saved before
     * mood was collected as well as any value from a newer release.
     */
    fun emojiOf(mood: String?): String = EMOJI[mood] ?: "📝"
}

/**
 * What `feedback.isi` is filled with when the parent types no free text.
 *
 * It exists because `isi` is `not null` in Postgres while the form's "Catatan Tambahan" is
 * optional. Named here rather than left as a literal because the display side has to recognise it:
 * used as a row headline it makes every chip-only note look identical.
 */
const val CATATAN_RUTIN = "Catatan rutin perkembangan anak."

/**
 * True when the parent actually typed something into "Catatan Tambahan".
 *
 * The form's free-text field is optional while `feedback.isi` is `not null`, so
 * `CreateFeedbackViewModel` backfills an empty one with [CATATAN_RUTIN]. That backfill is what
 * makes this knowable: the app can tell "typed nothing" from "typed something" without reading a
 * single word, so nothing here has to guess at meaning or match Indonesian keywords.
 *
 * Equality rather than `startsWith`: a parent who types that exact sentence and then adds to it
 * has said something. [CATATAN_RUTIN] is 32 characters and the notification trigger truncates at
 * 120, so truncation cannot manufacture a false match either.
 *
 * Extracted because three callers had grown their own copy of this test -- `FeedbackSummary`,
 * `NotifDisplay.bodyLineOf`, and now `FeedbackTriage`, where it decides whether a note needs a
 * volunteer's attention. Three copies of a predicate that answers "did this parent say anything"
 * is how they drift apart.
 */
fun hasTypedNote(isi: String?): Boolean =
    isi?.trim()?.let { it.isNotEmpty() && it != CATATAN_RUTIN } == true

/**
 * The quick-pick chips on the behavior-note form's "Apakah ada kendala saat terapi?" section.
 *
 * Note that the section is a mixed bag despite its name: two of the five options ([LANCAR],
 * [BANYAK_KEMAJUAN]) report that things went *well*. So a non-empty `feedback.kendala` is not on
 * its own a sign of trouble — which is exactly why [CONCERNING] has to be spelled out separately.
 *
 * Selected chips are stored joined with ", " into the single `feedback.kendala` text column
 * (see `CreateFeedbackViewModel.submit`), so any code reading them back must split on [SEPARATOR]
 * rather than substring-match the whole string.
 */
object FeedbackChallenge {
    const val SULIT_FOKUS = "Sulit Fokus"
    const val MUDAH_LELAH = "Mudah Lelah"
    const val MENOLAK_LATIHAN = "Menolak Latihan"
    const val LANCAR = "Lancar"
    const val BANYAK_KEMAJUAN = "Banyak Kemajuan"

    /** Form order: the three difficulties first, then the two good outcomes. */
    val ALL = listOf(SULIT_FOKUS, MUDAH_LELAH, MENOLAK_LATIHAN, LANCAR, BANYAK_KEMAJUAN)

    /** The chips that actually report a difficulty. [LANCAR] and [BANYAK_KEMAJUAN] do not. */
    val CONCERNING = setOf(SULIT_FOKUS, MUDAH_LELAH, MENOLAK_LATIHAN)

    /** How selected chips are joined into `feedback.kendala`. */
    const val SEPARATOR = ", "

    /** The chips stored in a `kendala` string, unpacked. Blank entries are dropped. */
    fun parse(kendala: String?): List<String> =
        kendala.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
