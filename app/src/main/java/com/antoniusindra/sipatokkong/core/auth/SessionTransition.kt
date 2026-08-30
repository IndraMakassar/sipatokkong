package com.antoniusindra.sipatokkong.core.auth

/**
 * What an auth signal does to the session state, as a pure rule.
 *
 * Separate from [SessionManager] because the interesting part is not the collecting, it is the
 * asymmetry: the transition *into* `SignedIn` is easy to get right, and every transition *out* of it
 * is a way to throw a signed-in user back to the login screen and wipe their navigation stack. All
 * three of the ones below did exactly that.
 *
 * Free of Supabase types so the rules can be unit-tested, the same way `NotifInbox` is split out of
 * `NotificationViewModel`.
 */
object SessionTransition {

    /**
     * The library's statuses, flattened to what actually matters here.
     *
     * [SIGNED_OUT_EXPLICIT] versus [SIGNED_OUT_IMPLICIT] is `SessionStatus.NotAuthenticated
     * .isSignOut`. True means somebody called `signOut()`. False means "initialisation finished and
     * storage was empty" — the honest answer at cold start, and a lie on any later reload.
     */
    enum class Signal { INITIALIZING, SIGNED_OUT_EXPLICIT, SIGNED_OUT_IMPLICIT, REFRESH_FAILED }

    /**
     * The next state, or **null to leave the current one alone**.
     *
     * The rule in one sentence: **once this process has resolved a profile, only an explicit
     * sign-out may undo it.**
     *
     * Every part of that earns itself:
     *
     * - supabase-kt's Android lifecycle observer pushes `Initializing` on **every** `onStop`, and
     *   re-resolves on `onStart`. Mapping that to `Loading` is what made each app reopen a full
     *   session round trip with a login screen visible in the middle of it — and, because the old
     *   `SipatokkongMain` rendered `Loading` *instead of* the `NavHost`, it also tore down the whole
     *   back stack. Background the app three screens deep and you came back to one.
     * - `RefreshFailure` is the same trap and a likelier one. On the patchy mobile data this app is
     *   explicitly written for, `onStart`'s auto-refresh failing to reach the network says nothing
     *   about whether the stored session is still valid — but it used to mean
     *   `navigate("login") { popUpTo(0) }`.
     * - `NotAuthenticated` with `isSignOut = false` is the third door into the same room.
     *
     * The cost, and it is a real one: a genuinely dead token now surfaces as an error inside
     * whatever screen the user is on, rather than bouncing them to login. On this network that is
     * the better trade — a failed refresh is far more often a tunnel than a revoked session.
     */
    fun next(current: SessionManager.State, signal: Signal): SessionManager.State? = when {
        signal == Signal.SIGNED_OUT_EXPLICIT -> SessionManager.State.SignedOut
        current is SessionManager.State.SignedIn -> null
        signal == Signal.INITIALIZING -> SessionManager.State.Loading
        else -> SessionManager.State.SignedOut
    }

    /**
     * What a failed `profiles` lookup does.
     *
     * Only the **first** one may become [SessionManager.State.Error]. `loadProfile` runs again on
     * every `onStart`, so on a bad connection a reload failure would otherwise replace a working app
     * with the full-screen Retry pane. Once a profile is in hand, the last good one stands and the
     * next reload repairs it.
     */
    fun onProfileLoadFailed(
        current: SessionManager.State,
        message: String,
    ): SessionManager.State? =
        if (current is SessionManager.State.SignedIn) null else SessionManager.State.Error(message)
}
