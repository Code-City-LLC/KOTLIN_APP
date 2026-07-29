package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
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
        // ⚠️ `data` arrives in TWO shapes and the cast only handled one.
        //
        // A plain `as? JsonObject` returns null when the server (or FCM, which
        // stringifies every data value) sends `data` as a JSON *string* rather
        // than an object. Null here silently discards the ENTIRE payload — the
        // route, the package reference, everything — and the notification then
        // renders with no deep link and no context, looking merely "generic"
        // rather than broken. Nothing logs, nothing throws.
        val dataPayload = obj.decodeEmbeddedObject("data")
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

/**
 * Read a nested object that the producer may have sent EITHER as a real JSON
 * object OR as a JSON string containing one.
 *
 * FCM is the reason: its data payload is `Map<String, String>`, so every value
 * is stringified in transit. The same notification therefore arrives as an
 * object over the REST inbox and as an escaped string over push. A plain
 * `as? JsonObject` silently returns null for the second, discarding the route
 * and every package reference with it — the notification still renders, just
 * inert, which is why this never surfaced as an error.
 *
 * Returns null only when the value is genuinely absent or is neither shape.
 */
private fun JsonObject.decodeEmbeddedObject(key: String): JsonObject? {
    (this[key] as? JsonObject)?.let { return it }
    val raw = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    if (raw.isBlank()) return null
    // Malformed embedded JSON must degrade to "no payload", never crash the
    // whole notification list.
    return runCatching { AirdropJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
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

/**
 * ⚠️ `appVersion` and `buildNumber` deliberately have NO default value.
 *
 * `AirdropJson` does not set `encodeDefaults`, so it defaults to FALSE and any
 * property carrying a default is OMITTED from the serialized body. Giving these
 * `= null` would compile, read correctly, and silently never reach the server —
 * the exact failure this pair exists to prevent. Nullable-but-required forces
 * every call site to pass something and keeps the key on the wire.
 *
 * Why the server needs them: without a version on the token row it cannot tell
 * which devices are running a stale build, so it cannot target an
 * "update available" push at the devices that actually need one.
 */
@Serializable
data class RegisterDeviceTokenRequest(
    @SerialName("device_token") val deviceToken: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("app_version") val appVersion: String?,
    @SerialName("build_number") val buildNumber: Int?,
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
