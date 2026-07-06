package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Laravel returns scalars inconsistently typed (123 | "123", 12.5 | "12.5" |
// "$1,550.00", true | 1 | "1"), mirroring Swift's decodeFlexible* helpers.

internal fun parseFlexString(p: JsonPrimitive): String? {
    if (p is JsonNull) return null
    return p.content
}

internal fun parseFlexInt(p: JsonPrimitive): Int? {
    if (p is JsonNull) return null
    if (p.isString) return p.content.trim().toIntOrNull()
    return p.longOrNull?.toInt() ?: p.doubleOrNull?.toInt()
}

internal fun parseFlexLong(p: JsonPrimitive): Long? {
    if (p is JsonNull) return null
    if (p.isString) return p.content.trim().toLongOrNull()
    return p.longOrNull ?: p.doubleOrNull?.toLong()
}

// Laravel money strings carry assorted currency prefixes and grouping commas:
// "$1,550.00", "J$1,550.00", "US$1,550.00", "JMD 1550", "1,550.50". Strip every
// char except digits, the decimal point and a sign so a multi-char prefix (not
// just a leading "$") no longer defeats the parse (BUG_AUDIT H2). Shared with
// AuctionProduct.currencyDouble in Products.kt so the two can't drift.
internal fun parseMoneyString(raw: String): Double? =
    raw.replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()

internal fun parseFlexDouble(p: JsonPrimitive): Double? {
    if (p is JsonNull) return null
    if (p.isString) return parseMoneyString(p.content)
    return p.doubleOrNull ?: p.longOrNull?.toDouble()
}

internal fun parseFlexBool(p: JsonPrimitive): Boolean? {
    if (p is JsonNull) return null
    if (!p.isString) {
        p.booleanOrNull?.let { return it }
        p.longOrNull?.let { return it != 0L }
        p.doubleOrNull?.let { return it != 0.0 }
        return null
    }
    return when (p.content.trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
}

object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ga.airdrop.FlexibleInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? {
        val input = decoder as? JsonDecoder ?: return runCatching { decoder.decodeInt() }.getOrNull()
        return (input.decodeJsonElement() as? JsonPrimitive)?.let(::parseFlexInt)
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

object FlexibleLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ga.airdrop.FlexibleLong", PrimitiveKind.LONG).nullable

    override fun deserialize(decoder: Decoder): Long? {
        val input = decoder as? JsonDecoder ?: return runCatching { decoder.decodeLong() }.getOrNull()
        return (input.decodeJsonElement() as? JsonPrimitive)?.let(::parseFlexLong)
    }

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

object FlexibleDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ga.airdrop.FlexibleDouble", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? {
        val input = decoder as? JsonDecoder ?: return runCatching { decoder.decodeDouble() }.getOrNull()
        return (input.decodeJsonElement() as? JsonPrimitive)?.let(::parseFlexDouble)
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ga.airdrop.FlexibleString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val input = decoder as? JsonDecoder ?: return runCatching { decoder.decodeString() }.getOrNull()
        return (input.decodeJsonElement() as? JsonPrimitive)?.let(::parseFlexString)
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

object FlexibleBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ga.airdrop.FlexibleBoolean", PrimitiveKind.BOOLEAN).nullable

    override fun deserialize(decoder: Decoder): Boolean? {
        val input = decoder as? JsonDecoder ?: return runCatching { decoder.decodeBoolean() }.getOrNull()
        return (input.decodeJsonElement() as? JsonPrimitive)?.let(::parseFlexBool)
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}

// ── JsonObject helpers used by the hand-written serializers below ──

internal fun JsonObject.firstPrimitive(vararg keys: String): JsonPrimitive? {
    for (key in keys) {
        val el = this[key]
        if (el is JsonPrimitive && el !is JsonNull) return el
    }
    return null
}

internal fun JsonObject.flexString(vararg keys: String): String? =
    firstPrimitive(*keys)?.let(::parseFlexString)

internal fun JsonObject.flexInt(vararg keys: String): Int? =
    firstPrimitive(*keys)?.let(::parseFlexInt)

internal fun JsonObject.flexDouble(vararg keys: String): Double? =
    firstPrimitive(*keys)?.let(::parseFlexDouble)

internal fun JsonObject.flexBool(vararg keys: String): Boolean? =
    firstPrimitive(*keys)?.let(::parseFlexBool)

internal fun JsonObject.objectAt(vararg keys: String): JsonObject? {
    for (key in keys) {
        (this[key] as? JsonObject)?.let { return it }
    }
    return null
}

internal fun JsonObject.arrayAt(vararg keys: String): JsonArray? {
    for (key in keys) {
        (this[key] as? JsonArray)?.let { return it }
    }
    return null
}

// Charges arrive as {"Freight": 9.5} or {"Freight": "9.50"}; keep entries
// whose value parses, like Swift's decodeFlexibleDoubleMap.
internal fun JsonObject.flexDoubleMap(vararg keys: String): Map<String, Double>? {
    for (key in keys) {
        val obj = this[key] as? JsonObject ?: continue
        val map = buildMap {
            for ((name, value) in obj) {
                val primitive = value as? JsonPrimitive ?: continue
                parseFlexDouble(primitive)?.let { put(name, it) }
            }
        }
        if (map.isNotEmpty()) return map
    }
    return null
}

internal fun <T> Json.decodeListOrNull(serializer: KSerializer<T>, element: JsonElement?): List<T>? {
    val array = element as? JsonArray ?: return null
    return runCatching { decodeFromJsonElement(ListSerializer(serializer), array) }.getOrNull()
}

internal fun <T> Json.decodeOrNull(serializer: KSerializer<T>, element: JsonElement?): T? {
    if (element == null || element is JsonNull) return null
    return runCatching { decodeFromJsonElement(serializer, element) }.getOrNull()
}
