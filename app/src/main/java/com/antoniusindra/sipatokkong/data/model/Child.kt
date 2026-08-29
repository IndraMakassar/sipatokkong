package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Period

/** Lightweight `public.anak` projection for the "Pilih Anak" dropdown (only the columns we select). */
@Serializable
data class ChildOption(
    val id: String,
    val nama: String,
)

/**
 * `public.anak` as the volunteer's home screen needs it: identity plus the bits worth showing.
 *
 * Separate from [ChildOption] rather than widening it. [ChildOption] feeds the create-program
 * dropdown and the parent's home; adding columns there would put two screens nobody asked about at
 * risk for the sake of one.
 */
@Serializable
data class AssignedChild(
    val id: String,
    val nama: String,
    @SerialName("tanggal_lahir") val tanggalLahir: String? = null,
    @SerialName("catatan_kondisi") val catatanKondisi: String? = null,
    @SerialName("orangtua_id") val orangtuaId: String? = null,
) {
    /**
     * Age as it should read on screen, or null when there is no usable birth date.
     *
     * **Under one year it counts months.** "0 tahun" says nothing about a baby, and the children
     * this app serves are young enough that a few months is a real difference in what therapy is
     * appropriate.
     *
     * `tanggal_lahir` is nullable and has never been displayed anywhere before, so a stored value
     * that will not parse is treated as absent rather than crashing a home screen -- the same
     * defensive read `EditChildViewModel` already does.
     */
    fun ageLabel(today: LocalDate): String? {
        val birth = tanggalLahir?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        if (birth.isAfter(today)) return null
        val period = Period.between(birth, today)
        return if (period.years < 1) "${period.years * 12 + period.months} bulan"
        else "${period.years} tahun"
    }

    /**
     * First letter of the name, for the avatar circle.
     *
     * Null for a blank name so the avatar can fall back to an icon instead of rendering an empty
     * circle -- `nama` is `not null` in Postgres but nothing stops it being whitespace.
     */
    val initial: Char? get() = nama.trim().firstOrNull()?.uppercaseChar()
}

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
