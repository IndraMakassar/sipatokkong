package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.ProfileOption
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class ProfileRepository(private val client: SupabaseClient) {

    /** All profiles of a given role (id + name). Admin can read all per RLS `can_view_profile`. */
    suspend fun profilesByRole(role: String): List<ProfileOption> =
        client.postgrest.from("profiles")
            .select(columns = Columns.list("id", "full_name")) {
                filter { eq("role", role) }
            }
            .decodeList<ProfileOption>()

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

    suspend fun updateProfile(profile: Profile) {
        client.postgrest.from("profiles").update(profile) {
            filter {
                eq("id", profile.id)
            }
        }
    }
}
