package com.antoniusindra.sipatokkong.core.di

import android.content.Context
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.supabase.SupabaseModule
import com.antoniusindra.sipatokkong.data.repository.AnakRepository
import com.antoniusindra.sipatokkong.data.repository.AuthRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import com.antoniusindra.sipatokkong.data.repository.TugasRepository
import io.github.jan.supabase.SupabaseClient

/**
 * Minimal manual dependency container. Initialized once from [com.antoniusindra.sipatokkong.SipatokkongApp].
 * Repositories and managers are exposed here; screens obtain them via their ViewModels.
 */
object ServiceLocator {

    lateinit var appContext: Context
        private set

    val supabase: SupabaseClient get() = SupabaseModule.client

    val sessionManager: SessionManager by lazy { SessionManager(supabase) }

    val authRepository: AuthRepository by lazy { AuthRepository(supabase) }

    /** Used specifically for admin creating other users without losing admin session. */
    val registrationRepository: AuthRepository by lazy { AuthRepository(SupabaseModule.noSessionClient) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(supabase) }

    val anakRepository: AnakRepository by lazy { AnakRepository(supabase) }

    val tugasRepository: TugasRepository by lazy { TugasRepository(supabase) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
