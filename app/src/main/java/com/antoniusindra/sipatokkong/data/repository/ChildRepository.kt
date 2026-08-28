package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.AssignedChild
import com.antoniusindra.sipatokkong.data.model.Child
import com.antoniusindra.sipatokkong.data.model.ChildInsert
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.ChildUpdate
import com.antoniusindra.sipatokkong.data.model.ChildVolunteerInsert
import com.antoniusindra.sipatokkong.data.model.ChildVolunteerRow
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

    /**
     * The same children as [childrenForCurrentVolunteer], with the columns the home screen shows.
     *
     * The extra columns ride along on a request that was being made anyway, so this costs no round
     * trip over the narrow projection. Scoping is still RLS's job (`anak_select` via
     * `is_relawan_of`) -- there is no client-side filter on the volunteer.
     *
     * Sorted by name because the query has no `order()` and never had one, which left the old chip
     * row in an unspecified order that could differ between loads.
     */
    suspend fun assignedChildrenForCurrentVolunteer(): List<AssignedChild> =
        client.postgrest.from("anak")
            .select(
                columns = Columns.list("id", "nama", "tanggal_lahir", "catatan_kondisi", "orangtua_id")
            ) {
                filter { eq("is_active", true) }
            }
            .decodeList<AssignedChild>()
            .sortedBy { it.nama.lowercase() }

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

    /**
     * Admin: every child, active or not, for the manage screen.
     * RLS `anak_select` already narrows this to admins (others only see their own children).
     */
    suspend fun allChildren(): List<Child> =
        client.postgrest.from("anak")
            .select()
            .decodeList<Child>()
            .sortedBy { it.nama.lowercase() }

    /** A single child by id. */
    suspend fun getChild(id: String): Child? =
        client.postgrest.from("anak")
            .select {
                filter { eq("id", id) }
            }
            .decodeSingleOrNull<Child>()

    /** Admin-only: update a child row. */
    suspend fun updateChild(id: String, update: ChildUpdate) {
        client.postgrest.from("anak").update(update) {
            filter { eq("id", id) }
        }
    }

    /** Ids of the volunteers currently assigned to a child. */
    suspend fun assignedVolunteerIds(anakId: String): List<String> =
        client.postgrest.from("anak_relawan")
            .select(columns = Columns.list("relawan_id")) {
                filter { eq("anak_id", anakId) }
            }
            .decodeList<ChildVolunteerRow>()
            .map { it.relawanId }

    /** Admin-only: remove a volunteer assignment. */
    suspend fun unassignVolunteer(anakId: String, relawanId: String) {
        client.postgrest.from("anak_relawan").delete {
            filter {
                eq("anak_id", anakId)
                eq("relawan_id", relawanId)
            }
        }
    }
}
