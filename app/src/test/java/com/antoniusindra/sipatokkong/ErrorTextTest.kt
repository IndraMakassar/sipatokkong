package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.util.ErrorText
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What a person is shown when something fails.
 *
 * The fixtures below are the **real** strings, copied from what supabase-kt actually produces —
 * that is the point of the file. Every screen used to write `e.message ?: "Gagal memuat data"`, and
 * because `message` is non-null for essentially every exception, what reached the user was the left
 * side: a five-line stack blob with a URL and request headers in it, on an app built for people who
 * do not read English.
 */
class ErrorTextTest {

    private val fallback = "Gagal memuat data"

    /** Exactly what `AuthRestException` builds for a wrong password. */
    private val wrongPin = """
        invalid_credentials
        Invalid login credentials: invalid_credentials
        URL: https://abcdefgh.supabase.co/auth/v1/token?grant_type=password
        Headers: [Authorization=Bearer ###, apikey=###, Content-Type=application/json]
        Http Method: POST
    """.trimIndent()

    /** Exactly what `HttpRequestException` builds when DNS cannot resolve. */
    private val noNetwork =
        "HTTP request to https://abcdefgh.supabase.co/auth/v1/token?grant_type=password (POST) " +
            "failed with message: Unable to resolve host \"abcdefgh.supabase.co\": " +
            "No address associated with hostname"

    // ---- the two the user actually reported ----

    @Test
    fun `a wrong PIN says so, and says nothing else`() {
        assertEquals(ErrorText.WRONG_LOGIN, ErrorText.forMessage(wrongPin, fallback))
    }

    @Test
    fun `no network is named as no network, not as a hostname`() {
        assertEquals(ErrorText.OFFLINE, ErrorText.forMessage(noNetwork, fallback))
    }

    @Test
    fun `neither one leaks a URL or a header`() {
        // The property that matters more than any individual mapping: whatever comes back, it is
        // not the thing that came in.
        for (raw in listOf(wrongPin, noNetwork)) {
            val shown = ErrorText.forMessage(raw, fallback)
            assertFalse(shown, shown.contains("http"))
            assertFalse(shown, shown.contains("Headers"))
            assertFalse(shown, shown.contains("supabase"))
        }
    }

    // ---- the rule everything else rests on ----

    @Test
    fun `an unrecognised error yields the screen's own sentence, never the raw text`() {
        val raw = "org.postgresql.util.PSQLException: relation \"anak\" does not exist"
        assertEquals(fallback, ErrorText.forMessage(raw, fallback))
    }

    @Test
    fun `a null message yields the fallback rather than nothing`() {
        // One site used to be `error = e.message` with no fallback at all, so a null message
        // silently cleared the error and the screen showed a failure as success.
        assertEquals(fallback, ErrorText.forMessage(null, fallback))
        assertEquals(fallback, ErrorText.of(RuntimeException(), fallback))
    }

    // ---- classification by type, which a string cannot express ----

    @Test
    fun `the exception type is read when the message says nothing`() {
        assertEquals(ErrorText.OFFLINE, ErrorText.of(UnknownHostException(), fallback))
        assertEquals(ErrorText.SLOW, ErrorText.of(SocketTimeoutException(), fallback))
        // Any other socket-level failure is the server being unreachable rather than the phone
        // being offline -- different things for a user to do about it.
        assertEquals(ErrorText.SERVER, ErrorText.of(IOException(), fallback))
    }

    @Test
    fun `the message is read before the type`() {
        // HttpRequestException is an IOException whose message names the real cause. Reading the
        // type first would call every one of them "server unreachable" and lose the distinction.
        assertEquals(ErrorText.OFFLINE, ErrorText.of(IOException(noNetwork), fallback))
    }

    @Test
    fun `a non-IO exception with an unknown message falls back`() {
        assertEquals(fallback, ErrorText.of(IllegalStateException("nonsense"), fallback))
    }

    // ---- the rest of the table ----

    @Test
    fun `a timeout is described as slow, not as broken`() {
        assertEquals(ErrorText.SLOW, ErrorText.forMessage("Request timeout has expired", fallback))
        assertEquals(ErrorText.SLOW, ErrorText.forMessage("connect timed out", fallback))
    }

    @Test
    fun `a dead session asks the user to sign in again`() {
        assertEquals(ErrorText.SESSION_EXPIRED, ErrorText.forMessage("JWT expired", fallback))
        assertEquals(ErrorText.SESSION_EXPIRED, ErrorText.forMessage("Unauthorized", fallback))
    }

    @Test
    fun `a duplicate visit keeps the wording it had before the move`() {
        // This sentence used to live in CreatePresensiViewModel.messageFor. It is the one mapping
        // this app already had, and moving it must not change what a volunteer reads.
        assertEquals(
            ErrorText.ALREADY_RECORDED,
            ErrorText.forMessage("duplicate key value violates unique constraint \"presensi_unik_harian\"", fallback),
        )
        assertEquals(ErrorText.ALREADY_RECORDED, ErrorText.forMessage("SQLSTATE 23505", fallback))
    }

    @Test
    fun `three digits inside an id do not become a dead session`() {
        // Why the mapper deliberately does not match bare "401"/"403": they appear in uuids, paths
        // and counts, and a false positive would tell someone to sign in again for no reason.
        val raw = "row 401f2a3b-0000-4000-8000-000000000403 not found"
        assertEquals(fallback, ErrorText.forMessage(raw, fallback))
    }

    @Test
    fun `matching ignores case, because these strings come from three different sources`() {
        assertEquals(ErrorText.OFFLINE, ErrorText.forMessage("UNABLE TO RESOLVE HOST", fallback))
        assertEquals(ErrorText.WRONG_LOGIN, ErrorText.forMessage("Invalid Login Credentials", fallback))
    }
}
