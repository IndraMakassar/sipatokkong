package com.antoniusindra.sipatokkong.core.auth

import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observes the Supabase auth session and resolves the current user's [Profile] (with role)
 * from `public.profiles`. Role drives which module graph the app routes to after login.
 *
 * Note: the client-side role is for UX/routing only — real authorization is enforced by RLS.
 */
class SessionManager(private val client: SupabaseClient) {

    sealed interface State {
        data object Loading : State
        data object SignedOut : State
        data class SignedIn(val profile: Profile) : State
        data class Error(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> loadProfile(status.session.user?.id)
                    is SessionStatus.NotAuthenticated -> _state.value = State.SignedOut
                    is SessionStatus.Initializing -> _state.value = State.Loading
                    is SessionStatus.RefreshFailure -> _state.value = State.SignedOut
                }
            }
        }
    }

    fun retry() {
        val status = client.auth.currentSessionOrNull()
        if (status != null) {
            scope.launch { loadProfile(status.user?.id) }
        } else {
            _state.value = State.SignedOut
        }
    }

    private suspend fun loadProfile(userId: String?) {
        if (userId == null) {
            _state.value = State.SignedOut
            return
        }
        _state.value = runCatching {
            val profile = client.postgrest.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingle<Profile>()
            State.SignedIn(profile)
        }.getOrElse { State.Error(it.message ?: "Gagal memuat profil") }
    }

    val currentRole: UserRole?
        get() = (_state.value as? State.SignedIn)?.profile?.role

    suspend fun signOut() {
        client.auth.signOut()
    }
}
