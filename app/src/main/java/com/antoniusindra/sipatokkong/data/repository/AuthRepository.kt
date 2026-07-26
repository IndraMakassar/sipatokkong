package com.antoniusindra.sipatokkong.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class AuthRepository(private val client: SupabaseClient) {

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String): String {
        val result = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return result?.id ?: throw Exception("Gagal mendaftarkan user: ID tidak ditemukan")
    }

    suspend fun signOut() {
        client.auth.signOut()
    }
}
