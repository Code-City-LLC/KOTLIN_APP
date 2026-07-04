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

// GET /faqs row: {id, question, answer} or CMS {title, content}; id falls
// back to the question text when absent (Swift FAQItem).
@Serializable(with = FaqItemSerializer::class)
data class FaqItem(
    val id: String = "",
    val question: String = "",
    val answer: String = "",
)

object FaqItemSerializer : KSerializer<FaqItem> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.FaqItem")

    override fun serialize(encoder: Encoder, value: FaqItem) =
        throw UnsupportedOperationException("FaqItem is decode-only")

    override fun deserialize(decoder: Decoder): FaqItem {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return FaqItem()
        val question = obj.flexString("question", "title") ?: ""
        val answer = obj.flexString("answer", "content") ?: ""
        val id = obj.flexString("id")?.takeIf { it.isNotEmpty() } ?: question
        return FaqItem(id = id, question = question, answer = answer)
    }
}

// GET /content/terms-conditions and /content/privacy-policy:
// {data:{content|html|html_content|body}}, {data:"<html>"} or flat keys.
// Non-JSON (raw HTML) bodies are handled by the repository fallback.
@Serializable(with = CmsContentResponseSerializer::class)
data class CmsContentResponse(
    val content: String? = null,
)

object CmsContentResponseSerializer : KSerializer<CmsContentResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.CmsContentResponse")

    override fun serialize(encoder: Encoder, value: CmsContentResponse) =
        throw UnsupportedOperationException("CmsContentResponse is decode-only")

    override fun deserialize(decoder: Decoder): CmsContentResponse {
        val input = decoder as JsonDecoder
        val element = input.decodeJsonElement()
        if (element is JsonPrimitive) return CmsContentResponse(parseFlexString(element))
        val obj = element as? JsonObject ?: return CmsContentResponse()
        val data = obj["data"]
        val content = when (data) {
            is JsonObject -> data.flexString("content", "html", "html_content", "body")
            is JsonPrimitive -> parseFlexString(data)
            else -> null
        } ?: obj.flexString("content", "html", "html_content", "body")
        return CmsContentResponse(content)
    }
}

// ── Promotional banners (GET /promotional-banners) ──

@Serializable
data class PromotionalBanner(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val title: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String? = null,
    @SerialName("image_url")
    @Serializable(with = FlexibleStringSerializer::class)
    val imageUrl: String? = null,
    @SerialName("image_path")
    @Serializable(with = FlexibleStringSerializer::class)
    val imagePath: String? = null,
    @SerialName("start_date")
    @Serializable(with = FlexibleStringSerializer::class)
    val startDate: String? = null,
    @SerialName("end_date")
    @Serializable(with = FlexibleStringSerializer::class)
    val endDate: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val featured: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val active: Boolean? = null,
    @SerialName("created_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val createdAt: String? = null,
    @SerialName("updated_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val updatedAt: String? = null,
)

// ── Refer a friend (POST/GET /refer-friend) ──

@Serializable
data class ReferFriendRequest(
    @SerialName("friend_first_name") val friendFirstName: String,
    @SerialName("friend_last_name") val friendLastName: String,
    @SerialName("friend_email") val friendEmail: String,
    val description: String? = null,
)

@Serializable
data class ReferredFriend(
    val id: Int = 0,
    @SerialName("friend_name") val friendName: String? = null,
    @SerialName("friend_first_name") val friendFirstName: String? = null,
    @SerialName("friend_last_name") val friendLastName: String? = null,
    @SerialName("friend_email") val friendEmail: String? = null,
    @SerialName("refer_url") val referUrl: String? = null,
    val description: String? = null,
    @SerialName("refer_date") val referDate: String? = null,
    @SerialName("updated_date") val updatedDate: String? = null,
    val status: Int? = null,
    @SerialName("status_text") val statusText: String? = null,
    @SerialName("is_url_active") val isUrlActive: Int? = null,
)
