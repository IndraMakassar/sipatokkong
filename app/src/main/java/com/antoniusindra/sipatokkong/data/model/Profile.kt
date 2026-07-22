package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.profiles` — one per auth user, all roles. */
@Serializable
data class Profile(
    val id: String,
    val role: UserRole,
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)
