package com.antoniusindra.sipatokkong.ui.notifications

import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.data.model.Notification

/** `notif_type` values, as the strings Postgres actually sends. */
private const val TYPE_TUGAS_BARU = "tugas_baru"
private const val TYPE_FEEDBACK_BARU = "feedback_baru"
private const val TYPE_FEEDBACK_BALASAN = "feedback_balasan"

/** `entity_table` values the triggers write. */
private const val ENTITY_FEEDBACK = "feedback"

/**
 * Notification kinds as an inbox draws them.
 *
 * A deliberately smaller set than the Postgres enum `notif_type`, which declares six values, three
 * of which nothing writes. The three that are written split cleanly by role: a parent receives
 * `tugas_baru` and `feedback_balasan`, a volunteer receives only `feedback_baru` -- the one kind
 * `on_feedback_insert` fans out over `anak_relawan`. No role sees all three.
 */
enum class NotifType {
    TUGAS_BARU,
    FEEDBACK_BARU,
    FEEDBACK_BALASAN,

    /**
     * Everything else, drawn with a plain bell.
     *
     * The catch-all exists so the inbox keeps working when the database starts writing something
     * this build has never heard of -- a `tugas_reminder` cron job, say. Dropping unrecognised rows
     * would mean someone silently never sees a kind added after their APK shipped, which is a
     * worse failure than a generic icon because nothing on screen would hint that it happened.
     */
    LAINNYA,
}

/**
 * Where tapping a notification goes.
 *
 * Derived from `entity_table`/`entity_id` -- the pointer the triggers actually write -- and never
 * from `type`. The two are not interchangeable, and treating them as one is the mistake this type
 * exists to make impossible.
 */
sealed interface NotifTarget {

    /** Opens the feedback thread. `entity_id` on these rows is the thread id, not the reply id. */
    data class FeedbackThread(val feedbackId: String) : NotifTarget

    /** Nothing to open. The row still marks itself read when tapped. */
    data object None : NotifTarget
}

/**
 * One notification as the screen renders it.
 *
 * Two fields are nullable, for different reasons. [title] is not one of them: a card with no title
 * is not a card, so a missing one is backfilled. [body] is an optional *second* line -- null means
 * there is genuinely nothing more to say, and the card draws no line at all (see
 * [NotifDisplay.bodyLineOf]). [createdAt] stays a raw ISO string, formatted at render.
 */
data class NotificationItem(
    val id: String,
    val type: NotifType,
    val title: String,
    val body: String?,
    val createdAt: String?,
    val isRead: Boolean,
    val target: NotifTarget,
)

/**
 * What a notification row shows, and where tapping it goes.
 *
 * This table is written entirely by database triggers the client never sees, which is what makes
 * these rules worth stating in one testable place: the enum declares kinds nothing writes,
 * `entity_table`/`entity_id` are an unconstrained polymorphic pointer with no foreign key behind
 * it, and `on_balasan_insert` stores the feedback **thread** id rather than the reply id. Each of
 * those is a way for the inbox to render an empty card or navigate somewhere that can only fail.
 *
 * Kept free of Compose and Supabase types so the rules can be unit-tested directly, the same way
 * [com.antoniusindra.sipatokkong.ui.feedback.FeedbackTriage] is.
 */
object NotifDisplay {

    /** Fallback titles, used only when a row arrives with nothing usable in `title`. */
    private const val FALLBACK_TITLE_TUGAS = "Tugas rumah baru"

    /**
     * A last resort, and no longer a mirror of what the trigger writes.
     *
     * Until migration `0010` this was byte-identical to `on_feedback_insert`'s constant, and that
     * was the justification for it. `0010` made the trigger write the sender's and child's names
     * from columns instead, so this is now genuinely generic wording that only appears when a row
     * arrives with a blank title — which the triggers coalesce against, and which `profiles
     * .full_name` and `anak.nama` being `not null` makes vanishingly unlikely.
     */
    private const val FALLBACK_TITLE_FEEDBACK_BARU = "Feedback baru dari orang tua"
    private const val FALLBACK_TITLE_BALASAN = "Balasan feedback"
    private const val FALLBACK_TITLE_LAINNYA = "Pemberitahuan"

    /**
     * `notif_type` string to icon kind. Never throws, whatever arrives.
     *
     * This is the whole reason `Notification.type` is a String: an unknown value here costs one
     * generic icon, where an unknown enum constant would have cost the entire list.
     */
    fun kindOf(type: String?): NotifType = when (type) {
        TYPE_TUGAS_BARU -> NotifType.TUGAS_BARU
        TYPE_FEEDBACK_BARU -> NotifType.FEEDBACK_BARU
        TYPE_FEEDBACK_BALASAN -> NotifType.FEEDBACK_BALASAN
        else -> NotifType.LAINNYA
    }

    /**
     * Where a tap goes.
     *
     * `feedback` rows deep-link, because `entity_id` on them is already the thread id and the route
     * `feedback_thread/{feedbackId}` takes exactly that.
     *
     * `tugas` rows deliberately yield [NotifTarget.None]. The task route is
     * `tugas_detail/{tugasId}/{occurrenceDate}` and a notification carries no occurrence date; a
     * recurring program has dozens of occurrences and no single one of them is the right answer.
     * Guessing a date would open a screen the parent did not ask for.
     *
     * A blank or missing `entity_id`, and any `entity_table` this build does not recognise, also
     * yield [NotifTarget.None] -- navigating to `feedback_thread/null` opens a screen that can only
     * fail, which is worse than a tap that does nothing.
     */
    fun targetOf(entityTable: String?, entityId: String?): NotifTarget {
        if (entityTable != ENTITY_FEEDBACK) return NotifTarget.None
        val id = entityId?.trim().orEmpty()
        return if (id.isEmpty()) NotifTarget.None else NotifTarget.FeedbackThread(id)
    }

    /**
     * Database row to UI model. Pure, and deliberately clock-free: it does **not** format the
     * timestamp.
     *
     * [NotificationItem.createdAt] is carried through verbatim and formatted at render by
     * `formatIsoDate`. Formatting here would mean injecting today's date into a mapper, producing a
     * label that goes stale after midnight, and re-testing a helper `FeedbackDisplayTest` already
     * covers -- and it would forfeit `formatIsoDate`'s null return, which is what lets the screen
     * drop the date line entirely rather than print a placeholder.
     */
    fun from(row: Notification): NotificationItem {
        val kind = kindOf(row.type)
        return NotificationItem(
            id = row.id,
            type = kind,
            title = row.title?.trim().orEmpty().ifBlank { fallbackTitleFor(kind) },
            body = bodyLineOf(row.body),
            createdAt = row.createdAt,
            isRead = row.isRead,
            target = targetOf(row.entityTable, row.entityId),
        )
    }

    fun from(rows: List<Notification>): List<NotificationItem> = rows.map(::from)

    /**
     * The number on the bell, or null when there is nothing to say.
     *
     * Null rather than "0" so the badge disappears completely: a zero badge is a permanent mark on
     * the bell that means nothing. Capped at "9+" because the badge is a dot-sized circle, and a
     * reader with forty unread notifications needs to know it is "lots", not exactly how many.
     */
    fun badgeLabel(unread: Int): String? = when {
        unread <= 0 -> null
        unread > 9 -> "9+"
        else -> unread.toString()
    }

    /**
     * The second line, or null when the column holds nothing worth printing.
     *
     * `on_feedback_insert` copies `left(feedback.isi, 120)` straight into `body`. `isi` is `not null`
     * in Postgres while the form's "Catatan Tambahan" is optional, so `CreateFeedbackViewModel`
     * backfills the gap with [CATATAN_RUTIN] -- and mood-plus-chips with no typing is the quickest
     * way to use that form, and therefore the common one. Rendered literally, a volunteer's inbox
     * repeats one identical sentence down its entire length. That is the same defect
     * `FeedbackSummary.headlineOf` exists to fix on the feedback list, and this is the same fix at
     * the same cost: one string comparison, no second query.
     *
     * Equality, not `startsWith`: a parent who types that sentence and then adds to it has said
     * something. Truncation cannot manufacture a false match either -- [CATATAN_RUTIN] is 32
     * characters and the trigger cuts at 120.
     *
     * There is no "no body" fallback string, because there is no row that needs one: all three
     * triggers source `body` from a `not null` column (`tugas.judul`, `feedback.isi`), so a blank
     * body cannot occur. Inventing a line for a row that said nothing would also invite a tap on a
     * card whose ripple is deliberately disabled.
     */
    private fun bodyLineOf(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() && it != CATATAN_RUTIN }

    private fun fallbackTitleFor(kind: NotifType): String = when (kind) {
        NotifType.TUGAS_BARU -> FALLBACK_TITLE_TUGAS
        NotifType.FEEDBACK_BARU -> FALLBACK_TITLE_FEEDBACK_BARU
        NotifType.FEEDBACK_BALASAN -> FALLBACK_TITLE_BALASAN
        NotifType.LAINNYA -> FALLBACK_TITLE_LAINNYA
    }
}
