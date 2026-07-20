package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A signed-in device (Sanctum personal-access-token) for the current user.
 * Matches Laravel UserController::sessions — GET /api/v1/user/sessions.
 */
@Serializable
data class ActiveSession(
    val id: String,
    @SerialName("device_name") val deviceName: String? = null,
    val platform: String? = null,
    @SerialName("last_seen_ip") val lastSeenIp: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
)

/** Result of POST /api/v1/user/sessions/revoke (bulk sign-out of other devices). */
@Serializable
data class RevokeSessionsResult(
    @SerialName("revoked_count") val revokedCount: Int = 0,
)
