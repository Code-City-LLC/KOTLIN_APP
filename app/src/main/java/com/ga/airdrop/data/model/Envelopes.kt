package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

@Serializable
class EmptyRequest

@Serializable
data class MutationResponse(
    val success: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class Pagination(
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    val total: Int? = null,
    @SerialName("last_page") val lastPage: Int? = null,
)

// Laravel list endpoints answer in several shapes concurrently (see Swift
// PackagesListResponse et al.): bare array, {data:[...]}, {data:{items:[...],
// pagination:{...}}}, or a resource-named key ({packages:[...]}, {faqs:[...]}).
@Serializable(with = PaginatedSerializer::class)
data class Paginated<T>(
    val items: List<T> = emptyList(),
    val pagination: Pagination? = null,
)

private val LIST_KEYS = listOf(
    "items", "packages", "orders", "payments", "transactions", "notifications",
    "faqs", "banners", "tokens", "device_tokens", "categories", "statuses",
    "results", "data",
)

class PaginatedSerializer<T>(private val itemSerializer: KSerializer<T>) : KSerializer<Paginated<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.Paginated")

    override fun serialize(encoder: Encoder, value: Paginated<T>) =
        throw UnsupportedOperationException("Paginated is decode-only")

    override fun deserialize(decoder: Decoder): Paginated<T> {
        val input = decoder as JsonDecoder
        val json = input.json
        val element = input.decodeJsonElement()
        val listSerializer = ListSerializer(itemSerializer)

        if (element is JsonArray) {
            return Paginated(json.decodeOrNull(listSerializer, element) ?: emptyList())
        }
        val obj = element as? JsonObject ?: return Paginated()

        fun paginationIn(container: JsonObject?): Pagination? =
            json.decodeOrNull(Pagination.serializer(), container?.get("pagination"))

        val data = obj["data"]
        if (data is JsonArray) {
            return Paginated(json.decodeOrNull(listSerializer, data) ?: emptyList(), paginationIn(obj))
        }
        if (data is JsonObject) {
            for (key in LIST_KEYS) {
                val list = json.decodeOrNull(listSerializer, data[key]) ?: continue
                return Paginated(list, paginationIn(data) ?: paginationIn(obj))
            }
            return Paginated(emptyList(), paginationIn(data) ?: paginationIn(obj))
        }
        for (key in LIST_KEYS) {
            val list = json.decodeOrNull(listSerializer, obj[key]) ?: continue
            return Paginated(list, paginationIn(obj))
        }
        return Paginated(emptyList(), paginationIn(obj))
    }
}

// {success, message, data} envelope; when `data` is absent the payload may be
// the top-level object itself (Swift retries the bare decode in that case).
@Serializable(with = DataEnvelopeSerializer::class)
data class DataEnvelope<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val data: T? = null,
)

class DataEnvelopeSerializer<T>(private val dataSerializer: KSerializer<T>) : KSerializer<DataEnvelope<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.DataEnvelope")

    override fun serialize(encoder: Encoder, value: DataEnvelope<T>) =
        throw UnsupportedOperationException("DataEnvelope is decode-only")

    override fun deserialize(decoder: Decoder): DataEnvelope<T> {
        val input = decoder as JsonDecoder
        val json = input.json
        val element = input.decodeJsonElement()
        val obj = element as? JsonObject
            ?: return DataEnvelope(data = json.decodeOrNull(dataSerializer, element))

        val dataEl = obj["data"]
        val data = if (dataEl != null && dataEl !is JsonNull) {
            json.decodeOrNull(dataSerializer, dataEl)
        } else {
            json.decodeOrNull(dataSerializer, obj)
        }
        return DataEnvelope(
            success = obj.flexBool("success"),
            message = obj.flexString("message"),
            data = data,
        )
    }
}
