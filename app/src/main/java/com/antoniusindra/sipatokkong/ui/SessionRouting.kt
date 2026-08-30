package com.antoniusindra.sipatokkong.ui

import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.data.model.UserRole

/**
 * Which route is the root of the graph for a given session, as a pure function.
 *
 * Extracted because `NavHost` reads `startDestination` exactly once — when it enters composition —
 * and remembers its graph keyed on it. So this answer has to be right the first time; it cannot be
 * corrected afterwards without rebuilding the graph and discarding the back stack.
 */
object SessionRouting {

    const val LOGIN = "login"

    fun homeRoute(role: UserRole): String = when (role) {
        UserRole.ADMIN -> "admin_home"
        UserRole.ORANGTUA -> "orangtua_home"
        UserRole.RELAWAN -> "relawan_home"
    }

    /**
     * The root route for [state], or **null while the session is unresolved**.
     *
     * The null case is the load-bearing one. The graph used to start at [LOGIN] unconditionally, so
     * the instant a session resolved the `NavHost` composed, drew the login screen, and only then
     * played a 400 ms slide to the real home — a login screen every signed-in user watched leave, on
     * every launch. There is no session for which [LOGIN] is the right guess; while it is unknown
     * the honest answer is to draw nothing yet.
     */
    fun startRoute(state: SessionManager.State): String? = when (state) {
        is SessionManager.State.SignedIn -> homeRoute(state.profile.role)
        is SessionManager.State.SignedOut -> LOGIN
        is SessionManager.State.Loading, is SessionManager.State.Error -> null
    }
}
