package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.util.rethrowCancellation
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Letting a cancellation out of a `runCatching`.
 *
 * `runCatching` catches `Throwable`, and `CancellationException` is one — so every
 * `runCatching { someSuspendCall() }` in this app quietly reported "the coroutine was cancelled" as
 * "the request failed". That is what hid the notification sweep never reaching the database: the
 * write was cancelled by the Back press that followed it, the failure branch wrote an error into a
 * ViewModel already being destroyed, and nothing was ever shown.
 */
class ResultExtTest {

    @Test
    fun `a success passes straight through`() {
        assertEquals("ok", runCatching { "ok" }.rethrowCancellation().getOrNull())
    }

    @Test
    fun `an ordinary failure stays a failure`() {
        // The whole point is to separate cancellation from real errors, not to stop catching them.
        val result = runCatching { throw IOException("jaringan putus") }.rethrowCancellation()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `a cancellation is rethrown, not returned`() {
        try {
            runCatching { throw CancellationException("dibatalkan") }.rethrowCancellation()
            fail("cancellation should have escaped instead of becoming a Result")
        } catch (e: CancellationException) {
            assertEquals("dibatalkan", e.message)
        }
    }

    @Test
    fun `the value survives, so the call can be chained`() {
        // It has to be safe to write runCatching { }.rethrowCancellation().onSuccess { } without
        // losing the value along the way.
        //
        // Asserts the value, not object identity: `Result` is a value class, so it is boxed afresh
        // on the way through and `assertSame` fails even though nothing was lost. The first draft
        // of this test made exactly that mistake and the suite caught it.
        val original = runCatching { 42 }
        assertEquals(original.getOrNull(), original.rethrowCancellation().getOrNull())

        var seen: Int? = null
        runCatching { 7 }.rethrowCancellation().onSuccess { seen = it }
        assertEquals(7, seen)
    }
}
