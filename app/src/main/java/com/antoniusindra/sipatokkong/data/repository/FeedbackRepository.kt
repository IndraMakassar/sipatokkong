package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackInsert
import com.antoniusindra.sipatokkong.data.model.FeedbackReply
import com.antoniusindra.sipatokkong.data.model.FeedbackReplyInsert
import com.antoniusindra.sipatokkong.data.model.InsertedId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class FeedbackRepository(private val client: SupabaseClient) {

    /** Parent-only: create a feedback thread; returns the new id. */
    suspend fun create(insert: FeedbackInsert): String =
        client.postgrest.from("feedback")
            .insert(insert) { select(Columns.list("id")) }
            .decodeSingle<InsertedId>()
            .id

    /** Feedback visible to the current user. RLS scopes to the parent's own or the volunteer's children. */
    suspend fun list(): List<Feedback> =
        client.postgrest.from("feedback")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList()

    suspend fun get(id: String): Feedback? =
        client.postgrest.from("feedback")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun replies(feedbackId: String): List<FeedbackReply> =
        client.postgrest.from("feedback_balasan")
            .select {
                filter { eq("feedback_id", feedbackId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun addReply(insert: FeedbackReplyInsert) {
        client.postgrest.from("feedback_balasan").insert(insert)
    }
}
