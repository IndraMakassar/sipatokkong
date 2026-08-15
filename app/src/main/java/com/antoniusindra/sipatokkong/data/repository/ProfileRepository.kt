package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.ProfileOption
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class ProfileRepository(private val client: SupabaseClient) {

    /**
     * Active profiles of a given role (id + name), for pickers.
     * Deactivated accounts are excluded so they can no longer be assigned to new records.
     * Admin can read all per RLS `can_view_profile`.
     */
    suspend fun profilesByRole(role: String): List<ProfileOption> =
        client.postgrest.from("profiles")
            .select(columns = Columns.list("id", "full_name")) {
                filter {
                    eq("role", role)
                    eq("is_active", true)
                }
            }
            .decodeList<ProfileOption>()

    /**
     * Every profile of a given role with all columns, active or not — for the admin
     * manage screens, which must also surface deactivated accounts so they can be restored.
     */
    suspend fun profilesByRoleFull(role: String): List<Profile> =
        client.postgrest.from("profiles")
            .select {
                filter { eq("role", role) }
            }
            .decodeList<Profile>()
            .sortedBy { it.fullName.lowercase() }

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
