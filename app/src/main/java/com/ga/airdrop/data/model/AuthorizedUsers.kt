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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthorizedUserRequest(
    @SerialName("user_first_name") val userFirstName: String,
    @SerialName("user_middle_name") val userMiddleName: String? = null,
    @SerialName("user_last_name") val userLastName: String,
    @SerialName("identification_type") val identificationType: String,
    @SerialName("identification_id_number") val identificationIdNumber: String,
    @SerialName("user_email") val userEmail: String,
    @SerialName("user_country_code") val userCountryCode: String,
    @SerialName("user_mobile_number") val userMobileNumber: String,
    @SerialName("trn_no") val trnNo: String,
    @SerialName("active_times") val activeTimes: Int? = null,
)

// Rows arrive with `user_*`-prefixed keys, flat keys, or a mix; the status
// is either a string ("active"/"1") or an is_active flag.
@Serializable(with = AuthorizedUserSerializer::class)
data class AuthorizedUser(
    val id: Int = 0,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val identificationType: String? = null,
    val identificationIdNumber: String? = null,
    val email: String? = null,
    val countryCode: String? = null,
    val mobileNumber: String? = null,
    val trnNumber: String? = null,
    val status: String? = null,
    val activeTimes: Int? = null,
) {
    val isActive: Boolean get() = status?.lowercase() == "active"
}

object AuthorizedUserSerializer : KSerializer<AuthorizedUser> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.AuthorizedUser")

    override fun serialize(encoder: Encoder, value: AuthorizedUser) =
        throw UnsupportedOperationException("AuthorizedUser is decode-only")

    override fun deserialize(decoder: Decoder): AuthorizedUser {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return AuthorizedUser()

        val explicitStatus = obj.flexString("status")
        val status = when {
            explicitStatus != null ->
                if (explicitStatus.lowercase() == "active" || explicitStatus == "1") "Active"
                else explicitStatus
            else -> obj.flexBool("is_active")?.let { if (it) "Active" else "Inactive" }
        }

        return AuthorizedUser(
            id = obj.flexInt("secondary_user_id", "id") ?: 0,
            firstName = obj.flexString("user_first_name", "first_name"),
            middleName = obj.flexString("user_middle_name", "middle_name"),
            lastName = obj.flexString("user_last_name", "last_name"),
            identificationType = obj.flexString("identification_type", "identity_type"),
            identificationIdNumber = obj.flexString("identification_id_number", "identity_number"),
            email = obj.flexString("user_email", "email"),
            countryCode = obj.flexString("user_country_code", "country_code"),
            mobileNumber = obj.flexString("user_mobile_number", "mobile_number", "mobile"),
            trnNumber = obj.flexString("trn_no", "trn_number"),
            status = status,
            activeTimes = obj.flexInt("active_times", "active_user_count"),
        )
    }
}

data class AuthorizedUsers(
    val active: List<AuthorizedUser> = emptyList(),
    val inactive: List<AuthorizedUser> = emptyList(),
)

// GET /authorized-users: bare array, {data:[...]}, {data:{active,inactive}}
// or top-level {active,inactive}. Flat arrays are split by status.
@Serializable(with = AuthorizedUsersEnvelopeSerializer::class)
data class AuthorizedUsersEnvelope(
    val users: AuthorizedUsers = AuthorizedUsers(),
)

object AuthorizedUsersEnvelopeSerializer : KSerializer<AuthorizedUsersEnvelope> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.AuthorizedUsersEnvelope")

    override fun serialize(encoder: Encoder, value: AuthorizedUsersEnvelope) =
        throw UnsupportedOperationException("AuthorizedUsersEnvelope is decode-only")

    override fun deserialize(decoder: Decoder): AuthorizedUsersEnvelope {
        val input = decoder as JsonDecoder
        val json = input.json
        val listSerializer = ListSerializer(AuthorizedUser.serializer())
        val element = input.decodeJsonElement()

        fun split(users: List<AuthorizedUser>) = AuthorizedUsers(
            active = users.filter { it.isActive },
            inactive = users.filterNot { it.isActive },
        )

        // Separated active/inactive arrays are the server's authority on which
        // bucket a user belongs to — but individual entries sometimes omit the
        // status/is_active field, which would leave status null -> isActive
        // false -> the detail CTA offers "Activate" for an already-active user
        // and the row shows "-". Stamp the bucket's status when the entry is
        // blank (Swift parity: force section status defaults).
        fun List<AuthorizedUser>.stampBucket(active: Boolean): List<AuthorizedUser> =
            map {
                if (it.status.isNullOrBlank()) {
                    it.copy(status = if (active) "Active" else "Inactive")
                } else {
                    it
                }
            }

        fun decodeBucket(el: JsonElement?, active: Boolean): List<AuthorizedUser> =
            (json.decodeOrNull(listSerializer, el) ?: emptyList()).stampBucket(active)

        if (element is JsonArray) {
            return AuthorizedUsersEnvelope(split(json.decodeOrNull(listSerializer, element) ?: emptyList()))
        }
        val obj = element as? JsonObject ?: return AuthorizedUsersEnvelope()
        val data = obj["data"]
        if (data is JsonArray) {
            return AuthorizedUsersEnvelope(split(json.decodeOrNull(listSerializer, data) ?: emptyList()))
        }
        if (data is JsonObject) {
            return AuthorizedUsersEnvelope(
                AuthorizedUsers(
                    active = decodeBucket(data["active"], active = true),
                    inactive = decodeBucket(data["inactive"], active = false),
                ),
            )
        }
        return AuthorizedUsersEnvelope(
            AuthorizedUsers(
                active = decodeBucket(obj["active"], active = true),
                inactive = decodeBucket(obj["inactive"], active = false),
            ),
        )
    }
}

// Single authorized-user responses: {data:{user}}, {data:<user>} or {user}.
@Serializable(with = AuthorizedUserEnvelopeSerializer::class)
data class AuthorizedUserEnvelope(
    val user: AuthorizedUser? = null,
)

object AuthorizedUserEnvelopeSerializer : KSerializer<AuthorizedUserEnvelope> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.AuthorizedUserEnvelope")

    override fun serialize(encoder: Encoder, value: AuthorizedUserEnvelope) =
        throw UnsupportedOperationException("AuthorizedUserEnvelope is decode-only")

    override fun deserialize(decoder: Decoder): AuthorizedUserEnvelope {
        val input = decoder as JsonDecoder
        val json = input.json
        val obj = input.decodeJsonElement() as? JsonObject ?: return AuthorizedUserEnvelope()
        val data = obj["data"]
        val user = when (data) {
            is JsonObject -> json.decodeOrNull(AuthorizedUser.serializer(), data["user"])
                ?: json.decodeOrNull(AuthorizedUser.serializer(), data)
            else -> null
        } ?: json.decodeOrNull(AuthorizedUser.serializer(), obj["user"])
        return AuthorizedUserEnvelope(user)
    }
}
