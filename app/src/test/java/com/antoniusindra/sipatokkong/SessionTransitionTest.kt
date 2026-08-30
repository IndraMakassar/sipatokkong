package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.auth.SessionTransition
import com.antoniusindra.sipatokkong.core.auth.SessionTransition.Signal
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a session may be taken away from someone who has one.
 *
 * These twelve encode the rule that was wrong, so they are worth more than their number suggests.
 * The old mapping treated three different signals as "signed out", and each of them fires during
 * ordinary use: supabase-kt pushes `Initializing` on **every** app backgrounding, `RefreshFailure`
 * arrives whenever an auto-refresh cannot reach the network, and `NotAuthenticated` carries an
 * `isSignOut` flag that was being discarded. On the patchy mobile data this app is written for, all
 * three meant "throw the user back to the login screen and wipe their navigation stack".
 *
 * The rule in one sentence, and the thing every test below is really checking: **once this process
 * has resolved a profile, only an explicit sign-out may undo it.**
 */
class SessionTransitionTest {

    private val signedIn = SessionManager.State.SignedIn(
        Profile(id = "p-1", role = UserRole.ORANGTUA, fullName = "Sari Wulandari"),
    )

    // ---- an explicit sign-out always wins ----

    @Test
    fun `signing out from a live session signs you out`() {
        assertEquals(
            SessionManager.State.SignedOut,
            SessionTransition.next(signedIn, Signal.SIGNED_OUT_EXPLICIT),
        )
    }

    @Test
    fun `signing out wins from every other state too`() {
        // Not a formality: this is the one signal that must never be swallowed, so it is checked
        // from each state rather than assumed to fall through.
        for (state in listOf(
            SessionManager.State.Loading,
            SessionManager.State.SignedOut,
            SessionManager.State.Error("apa pun"),
        )) {
            assertEquals(
                "$state",
                SessionManager.State.SignedOut,
                SessionTransition.next(state, Signal.SIGNED_OUT_EXPLICIT),
            )
        }
    }

    // ---- nothing else may unseat a live session ----

    @Test
    fun `backgrounding the app does not sign you out`() {
        // supabase-kt sets Initializing on every onStop. Mapping that to Loading is what made each
        // reopen a full session round trip with a login screen in the middle of it.
        assertNull(SessionTransition.next(signedIn, Signal.INITIALIZING))
    }

    @Test
    fun `a failed token refresh does not sign you out`() {
        // The likeliest of the three. An auto-refresh that cannot reach the network says nothing
        // about whether the stored session is valid, and this app is built for bad connections.
        assertNull(SessionTransition.next(signedIn, Signal.REFRESH_FAILED))
    }

    @Test
    fun `an implicit not-authenticated does not sign you out`() {
        // isSignOut = false means "initialisation finished and storage was empty" -- honest at cold
        // start, a lie on any later reload.
        assertNull(SessionTransition.next(signedIn, Signal.SIGNED_OUT_IMPLICIT))
    }

    // ---- before a session exists, every signal is taken at face value ----

    @Test
    fun `initializing from cold is loading`() {
        assertEquals(
            SessionManager.State.Loading,
            SessionTransition.next(SessionManager.State.Loading, Signal.INITIALIZING),
        )
    }

    @Test
    fun `an implicit not-authenticated from cold is signed out`() {
        assertEquals(
            SessionManager.State.SignedOut,
            SessionTransition.next(SessionManager.State.Loading, Signal.SIGNED_OUT_IMPLICIT),
        )
    }

    @Test
    fun `a failed refresh from cold is signed out`() {
        // No profile to protect, so there is nothing to be careful about.
        assertEquals(
            SessionManager.State.SignedOut,
            SessionTransition.next(SessionManager.State.Loading, Signal.REFRESH_FAILED),
        )
    }

    @Test
    fun `an error state is not a session and can still resolve`() {
        assertEquals(
            SessionManager.State.Loading,
            SessionTransition.next(SessionManager.State.Error("gagal"), Signal.INITIALIZING),
        )
    }

    // ---- a failed profile lookup ----

    @Test
    fun `the first profile failure becomes an error`() {
        val next = SessionTransition.onProfileLoadFailed(SessionManager.State.Loading, "putus")
        assertEquals(SessionManager.State.Error("putus"), next)
    }

    @Test
    fun `a later profile failure leaves a working session alone`() {
        // loadProfile runs again on every reopen, so without this a reload failure would replace a
        // working screen with the full-screen Retry pane.
        assertNull(SessionTransition.onProfileLoadFailed(signedIn, "putus"))
    }

    @Test
    fun `a profile failure from an error state stays an error, with the new message`() {
        val next = SessionTransition.onProfileLoadFailed(SessionManager.State.Error("lama"), "baru")
        assertEquals(SessionManager.State.Error("baru"), next)
    }
}
