package com.antoniusindra.sipatokkong

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.antoniusindra.sipatokkong.core.util.pollWhileResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundPollingTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun `only polls while resumed and restarts immediately on return`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = Owner()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            var calls = 0
            val job = launch { owner.lifecycle.pollWhileResumed { calls++ } }
            runCurrent()
            advanceTimeBy(30_000)
            assertEquals(0, calls)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            runCurrent()
            assertEquals(1, calls)
            advanceTimeBy(9_999)
            runCurrent()
            assertEquals(1, calls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, calls)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(2, calls)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            runCurrent()
            assertEquals(3, calls)
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(job.isCompleted)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `leaving the screen cancels an in flight request without starting another`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = Owner()
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            var calls = 0
            var cancelled = false
            val job = launch {
                owner.lifecycle.pollWhileResumed {
                    calls++
                    try { awaitCancellation() } finally { cancelled = true }
                }
            }
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(1, calls)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertTrue(cancelled)
            job.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
