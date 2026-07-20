package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    val payload: Map<String, String> = emptyMap(),
)

object AirdropNotificationSerializer : KSerializer<AirdropNotification> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.AirdropNotification")

    override fun serialize(encoder: Encoder, value: AirdropNotification) =
        throw UnsupportedOperationException("AirdropNotification is decode-only")

    override fun deserialize(decoder: Decoder): AirdropNotification {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject
            ?: return AirdropNotification(id = syntheticNotificationId())

        val title = obj.flexString("title", "message_title") ?: "Notification"
        val body = obj.flexString("body", "description", "message_description") ?: ""
        val createdAt = obj.flexString("created_at", "sent_at", "message_date")
        val id = obj.flexString("id")?.takeIf { it.isNotEmpty() }
            ?: obj.flexString("message_id")?.takeIf { it.isNotEmpty() }
            ?: syntheticNotificationId(title, body, createdAt)

        val isRead = obj.flexBool("is_read")
            ?: obj.flexString("read_at")?.takeIf { it.isNotEmpty() }?.let { true }
            ?: obj.flexBool("message_read")
            ?: false

        val topRoute = obj.flexString("screen", "navigate_to", "route", "screen_name")
        val dataPayload = obj["data"] as? JsonObject
        val payload = dataPayload?.stringPayload().orEmpty()
        val route = dataPayload?.flexString("screen", "navigate_to", "route") ?: topRoute
        val topReference = obj.flexString(
            "package_id",
            "packageId",
            "packageID",
            "tracking_code",
            "package_tracking_code",
            "courier_number",
            "package_courier_number",
            "package_couirer_number",
            "reference_id",
        )
        val payloadPackageReference = dataPayload?.flexString(
            "package_id",
            "packageId",
            "packageID",
            "reference_id",
        )
        val payloadTrackingReference = dataPayload?.flexString(
            "tracking_code",
            "package_tracking_code",
            "courier_number",
            "package_courier_number",
            "package_couirer_number",
        )
        val referenceId = payloadPackageReference ?: payloadTrackingReference ?: topReference

        return AirdropNotification(
            id = id,
            title = title,
            body = body,
            type = obj.flexString("type", "notification_type")
                ?: dataPayload?.flexString("type", "notification_type"),
            isRead = isRead,
            createdAt = createdAt,
            route = route,
            referenceId = referenceId,
            payload = payload,
        )
    }
}

private fun JsonObject.stringPayload(): Map<String, String> = buildMap {
    for ((key, value) in this@stringPayload) {
        val primitive = value as? JsonPrimitive ?: continue
        parseFlexString(primitive)?.let { put(key, it) }
    }
}

private fun syntheticNotificationId(
    title: String = "",
    body: String = "",
    createdAt: String? = null,
): String {
    val raw = listOf(title, body, createdAt.orEmpty()).joinToString("|")
    return "synthetic.${Integer.toUnsignedString(raw.hashCode(), 16)}"
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

/** Swift deactivateFCMToken (b43cec6) — POST /device-tokens/deactivate. */
@Serializable
data class DeactivateDeviceTokenRequest(
    @SerialName("device_token") val deviceToken: String,
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
