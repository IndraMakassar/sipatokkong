package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Notification
import com.antoniusindra.sipatokkong.data.model.NotificationReadUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order

/**
 * The per-user notification inbox.
 *
 * Read-and-mark-read only: `notifications` has no client INSERT policy and no DELETE policy, so
 * every row here was written by a database trigger and none of them can ever be removed.
 */
class NotificationRepository(private val client: SupabaseClient) {

    /**
     * The signed-in user's own inbox, newest first.
     *
     * **No `eq("recipient_id", ...)` filter**, the same call `PresensiRepository
     * .listForCurrentVolunteer()` makes for the same reason. `notif_select` is exactly
     * `recipient_id = auth.uid()` -- and unlike `presensi_select` it carries no `is_admin()`
     * override -- so a client-side filter could only ever narrow a set that is already one user's
     * rows. Leaving it out also removes a failure mode: a stale profile id held in `SessionManager`
     * disagreeing with the JWT would silently return an empty inbox, whereas RLS always agrees with
     * the token actually being sent. The index `idx_notif_recipient (recipient_id, is_read,
     * created_at desc)` serves the RLS predicate and this ordering directly.
     *
     * One page, [PAGE_SIZE] rows, because this table only grows -- one row per program and per
     * volunteer reply, forever, with no DELETE policy to prune it. A page is roughly a year for an
     * active parent but under three weeks for a volunteer assigned to a handful of children, since
     * `on_feedback_insert` fans a row out to every one of them per parent note. So the screen offers
     * "Muat lebih banyak" and passes an [offset]: the common case stays one cheap query, and only
     * the person who actually needs the tail pays for it.
     *
     * **The `id` ordering is a tiebreaker, and it is load-bearing now that pages exist.**
     * `created_at` alone is not a total order and Postgres breaks ties arbitrarily, so a tied row
     * can be returned on two pages or on neither. Ties are rare today -- each trigger writes one row
     * per recipient per transaction -- but a `tugas_reminder` cron inserting a batch would stamp
     * every row with the same `now()`.
     *
     * `range(from, to)` is how this version of postgrest-kt expresses offset+limit; `range(0, 49)`
     * is exactly the query this used to make, so there is one code path and no first-page branch.
     */
    suspend fun listForCurrentUser(
        limit: Long = PAGE_SIZE,
        offset: Long = 0,
    ): List<Notification> =
        client.postgrest.from("notifications")
            .select {
                order("created_at", Order.DESCENDING)
                order("id", Order.DESCENDING)
                range(offset, offset + limit - 1)
            }
            .decodeList()

    /**
     * How many unread rows the signed-in user has -- the number on the bell.
     *
     * `head = true` with `Count.EXACT` means PostgREST returns **no body at all** -- only a
     * `Content-Range` header carrying the count. That is why the home screen can afford to ask for
     * this on every entry, and why it does not fetch [listForCurrentUser] just to count the result.
     *
     * The `is_read` filter is the one filter that genuinely is needed here: RLS narrows the rows to
     * this user, but says nothing about whether they have been read. It lands on the second column
     * of `idx_notif_recipient`.
     */
    suspend fun unreadCountForCurrentUser(): Int =
        client.postgrest.from("notifications")
            .select(columns = Columns.list("id")) {
                head = true
                count(Count.EXACT)
                filter { eq("is_read", false) }
            }
            .countOrNull()?.toInt() ?: 0

    /**
     * Marks one notification read. Permitted by `notif_update` (`recipient_id = auth.uid()`).
     *
     * The `eq("id", ...)` filter is **required, not decorative**: an UPDATE with no filter still
     * satisfies RLS on every one of the user's own rows, so leaving it off would mark the entire
     * inbox read in a single request.
     *
     * The payload is [NotificationReadUpdate] rather than the whole row for the reason given on
     * that class -- the policy gates rows, not columns.
     */
    suspend fun markRead(id: String) {
        client.postgrest.from("notifications").update(NotificationReadUpdate()) {
            filter { eq("id", id) }
        }
    }

    /**
     * Marks every unread notification this user has, in one request.
     *
     * This is the call [markRead]'s KDoc warns about, made on purpose. An UPDATE bounded only by RLS
     * reaches every row `notif_update` admits -- and here that blast radius **is** the feature:
     * "Tandai semua dibaca" means all of them, including rows older than the page the screen has
     * loaded. The difference from [markRead] is intent rather than mechanism, which is exactly why
     * it is written down instead of left to be re-derived from a missing filter.
     *
     * The filter is `is_read = false` rather than nothing at all, for three reasons:
     *  - it writes only rows that actually change. An inbox of 300 with 4 unread costs 4 new tuples,
     *    not 300 -- and every no-op rewrite still costs a row version on a table with no DELETE
     *    policy and no pruning;
     *  - `idx_notif_recipient (recipient_id, is_read, created_at desc)` is an exact leading-column
     *    match for `recipient_id = auth.uid() and is_read = false`;
     *  - a PATCH with no filter at all is textually indistinguishable from the mistake [markRead]
     *    guards against. This one says what it means.
     *
     * With nothing unread it matches no rows and PostgREST answers 204 -- harmless, though
     * `NotificationViewModel.markAllRead` returns before reaching it anyway.
     *
     * Sends no push: `trg_notification_push` (migration `0009`) fires `after insert`, so an UPDATE
     * is invisible to it.
     *
     * **Asks for no count and no representation, deliberately.** `select()` would set
     * `Prefer: return=representation` and send back every updated row — 300 JSON notifications for a
     * busy parent, over the connection this whole file exists to conserve. `count(Count.EXACT)`
     * would be cheap, but there is nothing correct to do with a zero: the caller only reaches here
     * when the *local* unread count is positive, so a server-side zero means the local list
     * disagreed with the database — another device already swept, or a refresh landed mid-flight —
     * not that anything failed. Reverting on it would un-read rows that really are read. The honest
     * detector is the next `refresh()`.
     */
    suspend fun markAllRead() {
        client.postgrest.from("notifications").update(NotificationReadUpdate()) {
            filter { eq("is_read", false) }
        }
    }

    companion object {
        /** One page of the inbox. "Muat lebih banyak" asks for exactly this many more. */
        const val PAGE_SIZE = 50L
    }
}
