package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.SessionRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which screen the app opens on.
 *
 * `NavHost` reads `startDestination` once, when it enters composition, and keys its graph on it — so
 * this answer cannot be corrected afterwards without rebuilding the graph and throwing the back
 * stack away. The graph used to start at "login" unconditionally and navigate away afterwards, which
 * is why every signed-in user watched a login screen slide off on every single launch.
 *
 * The null cases are the point of the file: there is no session for which "login" is the right
 * guess, so while the session is unknown the honest answer is to draw nothing yet.
 */
class SessionRoutingTest {

    private fun signedIn(role: UserRole) = SessionManager.State.SignedIn(
        Profile(id = "p-1", role = role, fullName = "Sari Wulandari"),
    )

    // ---- resolved sessions ----

    @Test
    fun `an admin lands on the admin home`() {
        assertEquals("admin_home", SessionRouting.startRoute(signedIn(UserRole.ADMIN)))
    }

    @Test
    fun `a parent lands on the parent home`() {
        assertEquals("orangtua_home", SessionRouting.startRoute(signedIn(UserRole.ORANGTUA)))
    }

    @Test
    fun `a volunteer lands on the volunteer home`() {
        assertEquals("relawan_home", SessionRouting.startRoute(signedIn(UserRole.RELAWAN)))
    }

    @Test
    fun `signed out lands on login`() {
        assertEquals(SessionRouting.LOGIN, SessionRouting.startRoute(SessionManager.State.SignedOut))
    }

    // ---- unresolved sessions have no honest answer ----

    @Test
    fun `loading has no route yet`() {
        assertNull(SessionRouting.startRoute(SessionManager.State.Loading))
    }

    @Test
    fun `an error has no route yet`() {
        // Not login either: a failed profile load says nothing about whether the user is signed in,
        // and guessing login here would sign them out visually while the session is still valid.
        assertNull(SessionRouting.startRoute(SessionManager.State.Error("gagal")))
    }

    @Test
    fun `exactly the unresolved states are null`() {
        // The invariant the NavHost gating leans on: it only composes once this is non-null, so a
        // future state returning null silently would leave the app on a spinner forever, and one
        // returning a route too early would put the graph on the wrong root permanently.
        val all = listOf(
            signedIn(UserRole.ADMIN) to true,
            signedIn(UserRole.ORANGTUA) to true,
            signedIn(UserRole.RELAWAN) to true,
            SessionManager.State.SignedOut to true,
            SessionManager.State.Loading to false,
            SessionManager.State.Error("x") to false,
        )
        for ((state, shouldResolve) in all) {
            assertEquals("$state", shouldResolve, SessionRouting.startRoute(state) != null)
        }
    }

    // ---- the role mapping on its own ----

    @Test
    fun `every role has a home and they are all different`() {
        val routes = UserRole.entries.map { SessionRouting.homeRoute(it) }
        assertEquals(UserRole.entries.size, routes.toSet().size)
    }
}
