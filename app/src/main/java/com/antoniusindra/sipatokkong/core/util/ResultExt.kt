package com.antoniusindra.sipatokkong.core.util

import kotlinx.coroutines.CancellationException

/**
 * Lets a coroutine cancellation escape a `runCatching` instead of being reported as a failure.
 *
 * `runCatching` catches `Throwable`, and `CancellationException` is one. So every
 * `runCatching { someSuspendCall() }` in this repo quietly turns "this coroutine was cancelled" into
 * "the request failed", and the `onFailure` branch then writes an error into a StateFlow belonging
 * to a ViewModel that is in the middle of being destroyed. Usually harmless, because nobody is
 * watching — which is exactly why it hid `NotificationViewModel.markAllRead` never persisting: a
 * cancelled write looked like a handled network error, so no banner appeared and the rows simply
 * came back unread.
 *
 * `onFailure` on a success does nothing, so this costs nothing where cancellation cannot happen.
 * Not `inline`: the compiler rightly points out there is no function parameter to inline, and a
 * warning here would be noise on a repo that keeps the build clean.
 */
fun <T> Result<T>.rethrowCancellation(): Result<T> =
    onFailure { if (it is CancellationException) throw it }
