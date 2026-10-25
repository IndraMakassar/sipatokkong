package com.antoniusindra.sipatokkong.ui.notifications

import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.data.model.hasTypedNote
import com.antoniusindra.sipatokkong.data.model.Notification

/** `notif_type` values, as the strings Postgres actually sends. */
private const val TYPE_TUGAS_BARU = "tugas_baru"
private const val TYPE_TUGAS_REMINDER = "tugas_reminder"
private const val TYPE_FEEDBACK_BARU = "feedback_baru"
private const val TYPE_FEEDBACK_BALASAN = "feedback_balasan"

/** `entity_table` values the triggers write. */
private const val ENTITY_FEEDBACK = "feedback"
private const val ENTITY_TUGAS = "tugas"

/** Notification kinds drawn by the inbox. Unknown database values keep the generic fallback. */
enum class NotifType {
    TUGAS_BARU,

    /**
     * The morning nudge for a task due today and not yet done, written by the `pengingat-tugas`
     * cron job (migration `0012`).
     *
     * A separate kind from [TUGAS_BARU] because they answer different questions -- "something
     * arrived" versus "something is due now" -- and a parent scanning a list needs to tell a task
     * they have already seen from one they have not.
     */
    TUGAS_REMINDER,

    TUGAS_SETORAN,
    TUGAS_EVALUASI,
    FEEDBACK_BARU,
    FEEDBACK_BALASAN,

    /**
     * Everything else, drawn with a plain bell.
     *
     * The catch-all exists so the inbox keeps working when the database starts writing something
     * this build has never heard of. It was written with `tugas_reminder` as the example; that one
     * now has its own kind above, and `tugas_missed` and `presensi` remain declared and unwritten.
     * Dropping unrecognised rows
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

    /**
     * Opens the task. Carries no date, and cannot: see [NotifDisplay.targetOf].
     *
     * The destination declares `occurrenceDate` optional and resolves one from the task's own
     * recurrence rule when it is absent.
     */
    data class TaskDetail(val tugasId: String) : NotifTarget

    /** The exact completion; the destination uses the signed-in role to choose its screen. */
    data class SubmissionDetail(val submissionId: String) : NotifTarget

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
    private const val FALLBACK_TITLE_REMINDER = "Pengingat tugas hari ini"

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
        "tugas_setoran" -> NotifType.TUGAS_SETORAN
        "tugas_evaluasi" -> NotifType.TUGAS_EVALUASI
        TYPE_TUGAS_BARU -> NotifType.TUGAS_BARU
        TYPE_TUGAS_REMINDER -> NotifType.TUGAS_REMINDER
        TYPE_FEEDBACK_BARU -> NotifType.FEEDBACK_BARU
        TYPE_FEEDBACK_BALASAN -> NotifType.FEEDBACK_BALASAN
        else -> NotifType.LAINNYA
    }

    /**
     * Where a tap goes.
     *
     * Both known tables deep-link, from the same pointer: `entity_id` is the thread id on a
     * `feedback` row and the task id on a `tugas` row, and each route takes exactly that.
     *
     * `tugas` rows used to yield [NotifTarget.None], on the argument that the route was
     * `tugas_detail/{tugasId}/{occurrenceDate}` and a notification carries no date. The premise is
     * still true -- `notifications` has no date column, and a `tugas_baru` row is written when the
     * programme is created, rarely a day any occurrence falls on. What changed is the conclusion:
     * the destination now declares that argument optional and resolves a date through
     * `RecurrenceRule.occurrenceNearest`, so the missing date is answered where the task has
     * already been fetched instead of being a reason to go nowhere.
     *
     * The blank-id guard runs **before** the table dispatch, so it protects both tables rather
     * than only the one it was written for. A blank or missing `entity_id`, and any `entity_table`
     * this build does not recognise, still yield [NotifTarget.None] -- navigating to
     * `feedback_thread/null` opens a screen that can only fail, which is worse than a tap that
     * does nothing.
     */
    fun targetOf(entityTable: String?, entityId: String?): NotifTarget {
        val id = entityId?.trim().orEmpty()
        if (id.isEmpty()) return NotifTarget.None
        return when (entityTable) {
            ENTITY_FEEDBACK -> NotifTarget.FeedbackThread(id)
            "tugas_penyelesaian" -> NotifTarget.SubmissionDetail(id)
            ENTITY_TUGAS -> NotifTarget.TaskDetail(id)
            else -> NotifTarget.None
        }
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
            // snippet() returns null for a blank or marker-only title, which is exactly when the
            // fallback should take over -- so this replaces the old trim/ifBlank pair rather than
            // sitting in front of it.
            title = snippet(row.title, MAX_TITLE_CHARS) ?: fallbackTitleFor(kind),
            body = bodyLineOf(row.body),
            createdAt = row.createdAt,
            isRead = row.isRead,
            target = targetOf(row.entityTable, row.entityId),
        )
    }

    fun from(rows: List<Notification>): List<NotificationItem> = rows.map(::from)

    /**
     * The person half of a "<pengirim> · <anak>" notification title.
     *
     * Migration `0010` builds every feedback title that way, and the shape is deliberate: it made
     * the title person-shaped so the tray could use it as a `Person` name. A conversation card has
     * room for exactly one name beside the avatar, and the one that belongs there is the human who
     * wrote the message -- not the human plus the child they wrote about.
     *
     * The child is not lost, only kept off that one surface. The in-app inbox renders the full
     * title straight from the row, and the thread screen carries its own "<anak> · <ortu>" header.
     *
     * A title with no separator comes back whole, which is exactly right for the fallback wordings
     * (`"Balasan feedback"`): there the entire title already is the best name available.
     *
     * Note that the avatar is deliberately NOT derived from this. `AvatarNaming.colorFor` hashes
     * the whole string, so feeding it the split name would repaint every existing conversation;
     * `initialOf` returns the same letter either way, because the person's name comes first.
     */
    fun senderOf(title: String): String =
        title.substringBefore(" · ").trim().ifEmpty { title.trim() }

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
        snippet(raw, MAX_BODY_CHARS)?.takeIf { hasTypedNote(it) }

    /**
     * One line, marker-free, bounded -- the client's mirror of `notif_snippet` from migration 0010.
     *
     * A deliberate second implementation of a server-side rule, which is normally a mistake. Three
     * things make it the right call here, and all three are about rows this client did not write:
     *
     *  1. `0010` has not been applied yet, so nothing caps anything today.
     *  2. Rows written *before* `0010` keep their raw bodies forever -- that migration's own footer
     *     says they are deliberately not backfilled. Only the client can clean those.
     *  3. `on_tugas_insert` before `0010` writes `body = new.judul` with no cap at all, and
     *     `tugas.instruksi` is plain text edited through a formatting toolbar that stores literal
     *     `###`, `**` and `- ` markers (see CreateTaskScreen). The system tray parses no markdown,
     *     so without this a parent's phone shows `### Langkah 1 - **pegang** sendok` verbatim.
     *
     * This is the single place both surfaces read: the inbox card goes through [from], and so does
     * the push tray via `PushPayload.parse`. Capping here is what stops `BigTextStyle.bigText`
     * expanding a 500-character instruction into a wall of text in the notification shade.
     *
     * Kept byte-for-byte equivalent to the SQL: line-leading block markers, then inline emphasis,
     * then every whitespace run collapsed, then trim; empty becomes null so the caller can drop the
     * line rather than print an empty one.
     */
    fun snippet(src: String?, maxLen: Int = MAX_BODY_CHARS): String? {
        val cleaned = (src ?: "")
            .replace(BLOCK_MARKER, "")
            .replace(INLINE_MARKER, "")
            .replace(WHITESPACE_RUN, " ")
            .trim()
        return when {
            cleaned.isEmpty() -> null
            // maxLen - 1 leaves room for the ellipsis, so the result never exceeds maxLen. The SQL
            // does the same arithmetic for the same reason.
            cleaned.length > maxLen -> cleaned.take(maxLen - 1) + "…"
            else -> cleaned
        }
    }

    /** `### `, `- `, `* `, `+ `, `1. ` at the start of any line. MULTILINE is what makes `^` mean
     *  "line start" rather than "string start", which is the only place these markers appear. */
    private val BLOCK_MARKER = Regex("""^[ \t]*(#{1,6}|[-*+]|\d+\.)[ \t]+""", RegexOption.MULTILINE)

    /** Inline emphasis, which the tray renders literally. */
    private val INLINE_MARKER = Regex("""\*\*|__|`""")

    /** Newlines included: a notification body is one line, and a parent's note is not. */
    private val WHITESPACE_RUN = Regex("""\s+""")

    /**
     * 120 characters, the same default `notif_snippet` carries, so a row written by the trigger and
     * a row cleaned here come out identical rather than subtly different lengths.
     */
    const val MAX_BODY_CHARS = 120

    /**
     * 80, and a guard rather than the main event.
     *
     * After `0010` a title is two names plus about 24 characters of fixed phrase
     * ("X memberi tugas untuk Y"), so a real one lands near 45. This only bites on data that is
     * already wrong -- a name field holding a paragraph -- and stops it from pushing the tray
     * title into a state the system truncates without an ellipsis of its own.
     */
    const val MAX_TITLE_CHARS = 80

    private fun fallbackTitleFor(kind: NotifType): String = when (kind) {
        NotifType.TUGAS_SETORAN -> "Setoran tugas baru"
        NotifType.TUGAS_EVALUASI -> "Evaluasi tugas baru"
        NotifType.TUGAS_BARU -> FALLBACK_TITLE_TUGAS
        NotifType.TUGAS_REMINDER -> FALLBACK_TITLE_REMINDER
        NotifType.FEEDBACK_BARU -> FALLBACK_TITLE_FEEDBACK_BARU
        NotifType.FEEDBACK_BALASAN -> FALLBACK_TITLE_BALASAN
        NotifType.LAINNYA -> FALLBACK_TITLE_LAINNYA
    }
}
