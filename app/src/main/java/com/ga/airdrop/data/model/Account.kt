package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Account-security surface — Swift AirdropAPI §D.1/D.2 parity
// (GET/DELETE /user/sessions, POST /user/export-data). All endpoints are
// LIVE on pre_staging (probed 2026-07-19: 401 auth-gated, not 404).

/** One signed-in device row from GET /user/sessions. */
@Serializable
data class ActiveSession(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("device_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val deviceName: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val platform: String? = null,
    @SerialName("last_seen_ip")
    @Serializable(with = FlexibleStringSerializer::class)
    val lastSeenIp: String? = null,
    @SerialName("last_seen_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val lastSeenAt: String? = null,
    @SerialName("is_current")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isCurrent: Boolean? = null,
)

/** Swift tolerates both `data` and `sessions` keys — mirror that. */
@Serializable
data class ActiveSessionsResponse(
    val data: List<ActiveSession>? = null,
    val sessions: List<ActiveSession>? = null,
) {
    val all: List<ActiveSession> get() = data ?: sessions ?: emptyList()
}

/** POST /user/export-data — link now, or an email-when-ready message. */
@Serializable
data class ExportPersonalDataResponse(
    @SerialName("download_url")
    @Serializable(with = FlexibleStringSerializer::class)
    val downloadUrl: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val message: String? = null,
)
