@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

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

// GET /aircoins/status: wrapped {data:{accumulated,redeemed,expired,available}}
// (balance mirrors available) or flat {accumulated,...,balance,tier}.
@Serializable(with = AirCoinsStatusSerializer::class)
data class AirCoinsStatus(
    val accumulated: Int? = null,
    val redeemed: Int? = null,
    val expired: Int? = null,
    val available: Int? = null,
    val balance: Int? = null,
    val tier: String? = null,
)

object AirCoinsStatusSerializer : KSerializer<AirCoinsStatus> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.AirCoinsStatus")

    override fun serialize(encoder: Encoder, value: AirCoinsStatus) =
        throw UnsupportedOperationException("AirCoinsStatus is decode-only")

    override fun deserialize(decoder: Decoder): AirCoinsStatus {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return AirCoinsStatus()
        val payload = obj["data"] as? JsonObject
        if (payload != null) {
            val available = payload.flexInt("available")
            return AirCoinsStatus(
                accumulated = payload.flexInt("accumulated"),
                redeemed = payload.flexInt("redeemed"),
                expired = payload.flexInt("expired"),
                available = available,
                balance = available,
                tier = null,
            )
        }
        return AirCoinsStatus(
            accumulated = obj.flexInt("accumulated"),
            redeemed = obj.flexInt("redeemed"),
            expired = obj.flexInt("expired"),
            available = obj.flexInt("available"),
            balance = obj.flexInt("balance"),
            tier = obj.flexString("tier"),
        )
    }
}

// GET /aircoins/history row; amount is positive (earn) or negative (spend).
@Serializable
data class AirCoinTransaction(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @SerialName("user_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val userId: Int? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val amount: Double? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val type: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String? = null,
    @SerialName("reference_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val referenceId: String? = null,
    // Swift ba98785 — RN's Courier Tracking column; server key varies.
    @SerialName("tracking_no")
    @kotlinx.serialization.json.JsonNames("trackingNo", "courier_tracking")
    @Serializable(with = FlexibleStringSerializer::class)
    val trackingNo: String? = null,
    @SerialName("created_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val createdAt: String? = null,
) {
    val isEarn: Boolean get() = (amount ?: 0.0) >= 0

    val displayDescription: String
        get() {
            if (!description.isNullOrEmpty()) return description
            return when (type?.lowercase()) {
                "package_collected" -> "Package collected"
                "redemption" -> "Redeemed AirCoins"
                "expired" -> "Expired AirCoins"
                "referral" -> "Referral bonus"
                "promo" -> "Promotional bonus"
                else -> "AirCoin activity"
            }
        }
}
