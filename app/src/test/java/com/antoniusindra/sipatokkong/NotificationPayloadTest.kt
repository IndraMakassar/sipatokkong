package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.NotificationReadUpdate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What actually goes on the wire when a notification is marked read.
 *
 * This file exists because "mark as read" silently did nothing for the entire life of the feature,
 * and every layer around it was correct: the optimistic flip, the revert, the process-lifetime
 * scope, both PostgREST filters, and the RLS policy. The failure was one layer none of them
 * touched — the serialised request body.
 *
 * `NotificationReadUpdate`'s only property carried `= true`, and kotlinx.serialization omits a
 * property equal to its default unless `encodeDefaults` is on. supabase-kt serialises with
 * `Json { ignoreUnknownKeys = true }` (`SupabaseClientBuilder.defaultSerializer`), which leaves it
 * off. So the PATCH body was `{}`: no columns to set, no error, nothing written.
 *
 * The `Json` here is configured to match that serializer deliberately. A bare `Json` would also
 * reproduce it today, but only by coincidence — the point is to test what the app actually sends.
 */
class NotificationPayloadTest {

    /** The same configuration supabase-kt builds by default. */
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the read update carries is_read`() {
        assertEquals("""{"is_read":true}""", json.encodeToString(NotificationReadUpdate(isRead = true)))
    }

    @Test
    fun `the read update is never an empty body`() {
        // The invariant that matters, kept separate from the exact string above: renaming the
        // column should break one assertion and be an obvious rename, while a body collapsing back
        // to `{}` is the bug returning and must read as its own failure.
        //
        // PostgREST answers an empty PATCH without complaint, so nothing downstream would notice --
        // not the repository, not the ViewModel, not the user, until they reopened the inbox and
        // found everything unread again.
        assertNotEquals("{}", json.encodeToString(NotificationReadUpdate(isRead = true)))
    }
}
