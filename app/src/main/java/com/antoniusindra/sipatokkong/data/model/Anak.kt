package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.Serializable

/** Lightweight `public.anak` projection for the "Pilih Anak" dropdown (only the columns we select). */
@Serializable
data class AnakOption(
    val id: String,
    val nama: String,
)
