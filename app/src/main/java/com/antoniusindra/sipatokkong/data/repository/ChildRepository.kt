package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.ChildInsert
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.ChildVolunteerInsert
import com.antoniusindra.sipatokkong.data.model.InsertedId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class ChildRepository(private val client: SupabaseClient) {

    /**
     * Children the current volunteer may act on. RLS (`anak_select` via `is_relawan_of`)
     * limits the result to children assigned to the signed-in relawan.
     */
    suspend fun childrenForCurrentVolunteer(): List<ChildOption> {
        return client.postgrest.from("anak")
            .select(columns = Columns.list("id", "nama")) {
                filter { eq("is_active", true) }
            }
            .decodeList<ChildOption>()
    }

    /** True if the given orang tua already has an active child (soft MVP "1 child per parent" rule). */
    suspend fun hasChild(orangtuaId: String): Boolean =
        client.postgrest.from("anak")
            .select(columns = Columns.list("id")) {
                filter {
                    eq("orangtua_id", orangtuaId)
                    eq("is_active", true)
                }
            }
            .decodeList<InsertedId>()
            .isNotEmpty()

    /** The current parent's child (RLS `anak_select` via `is_parent_of`); first one (soft 1-child rule). */
    suspend fun childForCurrentParent(): ChildOption? =
        client.postgrest.from("anak")
            .select(columns = Columns.list("id", "nama")) {
                filter { eq("is_active", true) }
            }
            .decodeList<ChildOption>()
            .firstOrNull()

    /** Admin-only: create a child row, returns the new id. */
    suspend fun createChild(insert: ChildInsert): String =
        client.postgrest.from("anak")
            .insert(insert) { select(Columns.list("id")) }
            .decodeSingle<InsertedId>()
            .id

    /** Admin-only: assign a volunteer to a child. */
    suspend fun assignVolunteer(anakId: String, relawanId: String) {
        client.postgrest.from("anak_relawan")
            .insert(ChildVolunteerInsert(anakId = anakId, relawanId = relawanId))
    }
}
