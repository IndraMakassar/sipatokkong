package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.AnakOption
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class AnakRepository(private val client: SupabaseClient) {

    /**
     * Children the current volunteer may act on. RLS (`anak_select` via `is_relawan_of`)
     * limits the result to children assigned to the signed-in relawan.
     */
    suspend fun childrenForCurrentRelawan(): List<AnakOption> {
        return client.postgrest.from("anak")
            .select(columns = Columns.list("id", "nama")) {
                filter { eq("is_active", true) }
            }
            .decodeList<AnakOption>()
    }
}
