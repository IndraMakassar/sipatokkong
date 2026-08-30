package com.antoniusindra.sipatokkong.core.di

import android.content.Context
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.push.PushRegistrar
import com.antoniusindra.sipatokkong.core.supabase.SupabaseModule
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.AuthRepository
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import com.antoniusindra.sipatokkong.data.repository.NotificationRepository
import com.antoniusindra.sipatokkong.data.repository.PresensiRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Minimal manual dependency container. Initialized once from [com.antoniusindra.sipatokkong.SipatokkongApp].
 * Repositories and managers are exposed here; screens obtain them via their ViewModels.
 */
object ServiceLocator {

    lateinit var appContext: Context
        private set

    /**
     * Process-lifetime scope for writes that must outlive the screen that started them.
     *
     * "Tandai semua dibaca" is the case this exists for. It is fired from a confirmation dialog
     * whose natural next gesture is Back — which pops the notifications destination, clears that
     * `NavBackStackEntry`'s `ViewModelStore`, calls `onCleared()`, and cancels `viewModelScope`,
     * often before the PATCH has reached the socket. The write is not a screen's business; it is
     * the app's.
     *
     * Deliberately never cancelled. There is no app-shutdown hook worth wiring here: the process
     * dying takes it along, and a write lost to process death is lost either way. If a write ever
     * genuinely has to survive process death, the answer is a WorkManager one-shot — `work-runtime`
     * is already a dependency — not a wider scope than this.
     *
     * `Dispatchers.IO` to match [com.antoniusindra.sipatokkong.core.push.PushRegistrar]; the Ktor
     * engine moves off it immediately anyway, but the name says what this is for.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val supabase: SupabaseClient get() = SupabaseModule.client

    val sessionManager: SessionManager by lazy { SessionManager(supabase) }

    val authRepository: AuthRepository by lazy { AuthRepository(supabase) }

    /** Used specifically for admin creating other users without losing admin session. */
    val registrationRepository: AuthRepository by lazy { AuthRepository(SupabaseModule.noSessionClient) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(supabase) }

    val childRepository: ChildRepository by lazy { ChildRepository(supabase) }

    val taskRepository: TaskRepository by lazy { TaskRepository(supabase) }

    val feedbackRepository: FeedbackRepository by lazy { FeedbackRepository(supabase) }

    val presensiRepository: PresensiRepository by lazy { PresensiRepository(supabase) }

    val notificationRepository: NotificationRepository by lazy { NotificationRepository(supabase) }

    /** Keeps `push_tokens` in step with the signed-in user. See [PushRegistrar] for the two rules. */
    val pushRegistrar: PushRegistrar by lazy { PushRegistrar(supabase, sessionManager) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
