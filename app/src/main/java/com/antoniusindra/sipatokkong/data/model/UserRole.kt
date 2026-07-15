package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the Postgres enum `user_role`. Drives role-based navigation. */
@Serializable
enum class UserRole {
    @SerialName("admin") ADMIN,
    @SerialName("relawan") RELAWAN,
    @SerialName("orangtua") ORANGTUA,
}
