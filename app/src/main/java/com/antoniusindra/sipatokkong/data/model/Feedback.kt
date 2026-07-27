package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of `public.feedback` (behavior notes + parent feedback thread root). */
@Serializable
data class Feedback(
    val id: String,
    @SerialName("anak_id") val anakId: String,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("catatan_perilaku") val catatanPerilaku: String? = null,
    val kendala: String? = null,
    val isi: String,
    val status: String,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Insert payload for `public.feedback` (parent only, per RLS). */
@Serializable
data class FeedbackInsert(
    @SerialName("anak_id") val anakId: String,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("catatan_perilaku") val catatanPerilaku: String? = null,
    val kendala: String? = null,
    val isi: String,
)

/** A row of `public.feedback_balasan` (a reply on a feedback thread). */
@Serializable
data class FeedbackReply(
    val id: String,
    @SerialName("feedback_id") val feedbackId: String,
    @SerialName("author_id") val authorId: String,
    val isi: String,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Insert payload for `public.feedback_balasan`. */
@Serializable
data class FeedbackReplyInsert(
    @SerialName("feedback_id") val feedbackId: String,
    @SerialName("author_id") val authorId: String,
    val isi: String,
)
