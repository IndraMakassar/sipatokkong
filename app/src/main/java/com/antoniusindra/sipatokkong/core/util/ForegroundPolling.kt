package com.antoniusindra.sipatokkong.core.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** One request at a time, immediately on resume, then ten seconds after each completed refresh. */
suspend fun Lifecycle.pollWhileResumed(
    intervalMillis: Long = 10_000L,
    refresh: suspend () -> Unit,
) {
    require(intervalMillis > 0)
    repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (isActive) {
            refresh()
            delay(intervalMillis)
        }
    }
}
