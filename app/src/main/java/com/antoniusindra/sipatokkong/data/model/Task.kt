package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** UI priority option, mapped to the Postgres enum `task_priority`. */
enum class TaskPriority(val dbValue: String, val label: String) {
    LOW("low", "Rendah"),
    MEDIUM("medium", "Sedang"),
    HIGH("high", "Tinggi"),
}

/** Media kind, mapped to the Postgres enum `media_kind`. */
enum class MediaKind(val dbValue: String) {
    IMAGE("image"),
    VIDEO("video"),
}

/**
 * Insert payload for `public.tugas`. Omits id / status / created_at (DB defaults).
 * `media_path` is required by both the schema (NOT NULL) and the RLS insert policy.
 */
@Serializable
data class TaskInsert(
    @SerialName("anak_id") val anakId: String,
    @SerialName("relawan_id") val relawanId: String,
    val judul: String,
    val instruksi: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("priority_level") val priorityLevel: String,
    val deadline: String? = null,
    @SerialName("week_of") val weekOf: String? = null,
)

/** A row of `public.tugas` (read model). */
@Serializable
data class Task(
    val id: String,
    @SerialName("anak_id") val anakId: String,
    @SerialName("relawan_id") val relawanId: String,
    val judul: String,
    val instruksi: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("media_thumb_path") val mediaThumbPath: String? = null,
    @SerialName("priority_level") val priorityLevel: String,
    val deadline: String? = null,
    @SerialName("week_of") val weekOf: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
)
