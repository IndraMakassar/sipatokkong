package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Lightweight `public.anak` projection for the "Pilih Anak" dropdown (only the columns we select). */
@Serializable
data class ChildOption(
    val id: String,
    val nama: String,
)

/** Lightweight `public.profiles` projection for admin dropdowns (orang tua / relawan). */
@Serializable
data class ProfileOption(
    val id: String,
    @SerialName("full_name") val fullName: String,
)

/** Insert payload for `public.anak` (admin-only, per RLS). */
@Serializable
data class ChildInsert(
    val nama: String,
    @SerialName("tanggal_lahir") val tanggalLahir: String? = null,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("catatan_kondisi") val catatanKondisi: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
)

/** Insert payload for `public.anak_relawan` (assign a volunteer to a child; admin-only). */
@Serializable
data class ChildVolunteerInsert(
    @SerialName("anak_id") val anakId: String,
    @SerialName("relawan_id") val relawanId: String,
)

/** Full `public.anak` row — used by the admin manage/edit screens. */
@Serializable
data class Child(
    val id: String,
    val nama: String,
    @SerialName("tanggal_lahir") val tanggalLahir: String? = null,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("catatan_kondisi") val catatanKondisi: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

/** Update payload for `public.anak` — deliberately omits `id`/`created_by` so they are never overwritten. */
@Serializable
data class ChildUpdate(
    val nama: String,
    @SerialName("tanggal_lahir") val tanggalLahir: String? = null,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("catatan_kondisi") val catatanKondisi: String? = null,
    @SerialName("is_active") val isActive: Boolean,
)

/** Projection of `public.anak_relawan` for reading which volunteers are assigned to a child. */
@Serializable
data class ChildVolunteerRow(
    @SerialName("relawan_id") val relawanId: String,
)
