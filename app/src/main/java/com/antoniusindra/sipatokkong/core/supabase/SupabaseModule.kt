package com.antoniusindra.sipatokkong.core.supabase

import com.antoniusindra.sipatokkong.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Single Supabase client for the whole app.
 *
 * Uses ONLY the public anon key (never the service_role key — that must never ship in the APK).
 * All authorization is enforced server-side by Row Level Security; the client is untrusted.
 */
object SupabaseModule {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    /**
     * A secondary client that does NOT persist sessions.
     * Used for registration flows to avoid logging out the current admin.
     */
    val noSessionClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                sessionManager = null
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
            }
            install(Postgrest)
        }
    }
}
