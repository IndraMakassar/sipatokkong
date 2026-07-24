package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class ProfileRepository(private val client: SupabaseClient) {

    suspend fun createProfile(profile: Profile) {
        client.postgrest.from("profiles").insert(profile)
    }

    suspend fun getProfile(userId: String): Profile? {
        return client.postgrest.from("profiles")
            .select {
                filter {
                    eq("id", userId)
                }
            }
            .decodeSingleOrNull<Profile>()
    }
}
