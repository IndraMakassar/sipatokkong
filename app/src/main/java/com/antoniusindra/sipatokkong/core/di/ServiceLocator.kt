package com.antoniusindra.sipatokkong.core.di

import android.content.Context
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.supabase.SupabaseModule
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

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
