package com.ga.airdrop.data.model

import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

// GET /user/notifications row. Accepts the canonical Laravel shape
// ({id,title,description,type,is_read,read_at,screen_name,data}) and the
// legacy PHP message_* shape, matching Swift's AirdropNotification.
@Serializable(with = AirdropNotificationSerializer::class)
data class AirdropNotification(
    val id: String = "",
    val title: String = "Notification",
    val body: String = "",
    val type: String? = null,
    val isRead: Boolean = false,
    val createdAt: String? = null,
    val route: String? = null,
    val referenceId: String? = null,
)

object AirdropNotificationSerializer : KSerializer<AirdropNotification> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.AirdropNotification")

    override fun serialize(encoder: Encoder, value: AirdropNotification) =
        throw UnsupportedOperationException("AirdropNotification is decode-only")

    override fun deserialize(decoder: Decoder): AirdropNotification {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject
            ?: return AirdropNotification(id = UUID.randomUUID().toString())

        val id = obj.flexString("id")?.takeIf { it.isNotEmpty() }
            ?: obj.flexString("message_id")?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString()

        val isRead = obj.flexBool("is_read")
            ?: obj.flexString("read_at")?.takeIf { it.isNotEmpty() }?.let { true }
            ?: obj.flexBool("message_read")
            ?: false

        val topRoute = obj.flexString("route", "screen_name")
        val dataPayload = obj["data"] as? JsonObject
        val route: String?
        val referenceId: String?
        if (dataPayload != null) {
            route = dataPayload.flexString("route") ?: topRoute
            referenceId = dataPayload.flexString("reference_id", "package_id", "tracking_code")
        } else {
            route = topRoute
            referenceId = obj.flexString("reference_id")
        }

        return AirdropNotification(
            id = id,
            title = obj.flexString("title", "message_title") ?: "Notification",
            body = obj.flexString("body", "description", "message_description") ?: "",
            type = obj.flexString("type"),
            isRead = isRead,
            createdAt = obj.flexString("created_at", "sent_at", "message_date"),
            route = route,
            referenceId = referenceId,
        )
    }
}

@Serializable
data class MarkNotificationReadRequest(
    @SerialName("notification_id") val notificationId: String,
)

@Serializable
data class RegisterDeviceTokenRequest(
    @SerialName("device_token") val deviceToken: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("device_info") val deviceInfo: String? = null,
)

@Serializable
data class DeviceToken(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("device_token")
    @Serializable(with = FlexibleStringSerializer::class)
    val deviceToken: String? = null,
    @SerialName("device_type")
    @Serializable(with = FlexibleStringSerializer::class)
    val deviceType: String? = null,
    @SerialName("device_info")
    @Serializable(with = FlexibleStringSerializer::class)
    val deviceInfo: String? = null,
    @SerialName("is_active")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isActive: Boolean? = null,
)

@Serializable
data class SendTestNotificationRequest(
    @SerialName("device_id") val deviceId: String,
    val title: String,
    val body: String,
    val screen: String,
    @SerialName("notification_type") val notificationType: String,
    val type: String,
    @SerialName("deep_link") val deepLink: String,
    @SerialName("tracking_code") val trackingCode: String? = null,
)
