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

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class SignUpRequest(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val email: String,
    val password: String,
    // Backend field is misspelled on the wire; keep it verbatim.
    @SerialName("comfirm_passsord") val confirmPassword: String,
    @SerialName("user_country_code") val userCountryCode: String,
    @SerialName("user_mobile") val userMobile: String,
    @SerialName("user_address_line_1") val userAddressLine1: String,
    @SerialName("user_address_line_2") val userAddressLine2: String? = null,
    @SerialName("user_address_city") val userAddressCity: String,
    @SerialName("user_address_state") val userAddressState: String,
    @SerialName("user_address_country") val userAddressCountry: String,
    @SerialName("user_trn_number") val userTrnNumber: String? = null,
    @SerialName("user_identity_type") val userIdentityType: String? = null,
    @SerialName("user_identity_number") val userIdentityNumber: String? = null,
    // Current Laravel RegisterRequest still consumes this misspelled alias
    // while the profile/update rail uses user_identity_type.
    @SerialName("indentity_type") val legacyIdentityType: String? = null,
    @SerialName("user_hear_type") val userHearType: String,
    @SerialName("user_pickup_location") val userPickupLocation: String,
    @SerialName("user_tnc") val userTnc: Boolean,
    @SerialName("user_auth") val userAuth: Boolean,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val email: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class ReactivateAccountRequest(
    val email: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class DeactivateAccountRequest(
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

// {token, token_type, user} either top-level or wrapped under {data:{...}}.
@Serializable(with = LoginResponseSerializer::class)
data class LoginResponse(
    val token: String? = null,
    val tokenType: String? = null,
    val user: AirdropUser? = null,
    val message: String? = null,
)

object LoginResponseSerializer : KSerializer<LoginResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.LoginResponse")

    override fun serialize(encoder: Encoder, value: LoginResponse) =
        throw UnsupportedOperationException("LoginResponse is decode-only")

    override fun deserialize(decoder: Decoder): LoginResponse {
        val input = decoder as JsonDecoder
        val json = input.json
        val obj = input.decodeJsonElement() as? JsonObject ?: return LoginResponse()
        val message = obj.flexString("message")
        val payload = obj["data"] as? JsonObject
        val source = payload ?: obj
        return LoginResponse(
            token = source.flexString("token"),
            tokenType = source.flexString("token_type"),
            user = json.decodeOrNull(AirdropUser.serializer(), source["user"]),
            message = message,
        )
    }
}
