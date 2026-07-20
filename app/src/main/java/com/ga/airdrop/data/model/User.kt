package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Laravel ships several user schemas concurrently (user_* legacy columns,
// flat profile keys, nested address object) — mirror Swift's tolerant decode.
@Serializable(with = AirdropUserSerializer::class)
data class AirdropUser(
    val id: Int? = null,
    val accountNumber: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val profileImageUrl: String? = null,
    val pickupLocation: String? = null,
    val paymentCurrency: String? = null,
    val customerTierName: String? = null,
    /**
     * Server-truth identity completeness (Kemar 2026-07-19, Swift 44a9c5f):
     * true when BOTH TRN and identity number are on file. null on older
     * backend payloads — callers fall back to inspecting trnNumber/identityNumber.
     */
    val identityComplete: Boolean? = null,
    val trnNumber: String? = null,
    val identityNumber: String? = null,
) {
    val countryCode: String
        get() = when (country) {
            "Jamaica" -> "JM"
            "United States" -> "US"
            "Canada" -> "CA"
            "United Kingdom" -> "GB"
            else -> "JM"
        }
}

object AirdropUserSerializer : KSerializer<AirdropUser> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.AirdropUser")

    override fun serialize(encoder: Encoder, value: AirdropUser) =
        throw UnsupportedOperationException("AirdropUser is decode-only")

    override fun deserialize(decoder: Decoder): AirdropUser {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return AirdropUser()

        val addressElement = obj["address"]
        val flatAddress = (addressElement as? JsonPrimitive)
            ?.takeUnless { it is JsonNull }
            ?.let(::parseFlexString)
        val addressObj = addressElement as? JsonObject

        val address: String?
        val city: String?
        val state: String?
        val country: String?
        when {
            flatAddress != null -> {
                address = flatAddress
                city = obj.flexString("city", "user_address_city")
                state = obj.flexString("state", "user_address_state")
                country = obj.flexString("country", "user_address_country")
            }
            addressObj != null -> {
                address = addressObj.flexString("line_1", "address_line_1")
                    ?: obj.flexString("user_address_line_1")
                city = addressObj.flexString("city") ?: obj.flexString("city", "user_address_city")
                state = addressObj.flexString("state") ?: obj.flexString("state", "user_address_state")
                country = addressObj.flexString("country") ?: obj.flexString("country", "user_address_country")
            }
            else -> {
                address = obj.flexString("user_address_line_1")
                city = obj.flexString("city", "user_address_city")
                state = obj.flexString("state", "user_address_state")
                country = obj.flexString("country", "user_address_country")
            }
        }

        return AirdropUser(
            id = obj.flexInt("id"),
            accountNumber = obj.flexString("account_number", "user_account_number"),
            firstName = obj.flexString("first_name", "user_first_name", "user_first_last_name"),
            lastName = obj.flexString("last_name", "user_last_name", "user_second_last_name"),
            email = obj.flexString("email", "user_email"),
            phone = obj.flexString("phone", "mobile", "user_phone", "user_mobile"),
            address = address,
            city = city,
            state = state,
            country = country,
            profileImageUrl = obj.flexString("profile_image_url", "profile_image_remote"),
            pickupLocation = obj.flexString("pickup_location"),
            paymentCurrency = obj.flexString("payment_currency"),
            customerTierName = (obj["customer_tier"] as? JsonObject)?.flexString("name")
                ?: obj.flexString("customer_tier"),
            identityComplete = obj.flexBool("identity_complete"),
            trnNumber = obj.flexString("user_trn_number", "trn_number"),
            identityNumber = obj.flexString("user_identity_number", "identity_number"),
        )
    }
}

// GET /user/profile: {data:{user}}, {data:<user>}, {user:<user>} or bare user.
@Serializable(with = CurrentUserResponseSerializer::class)
data class CurrentUserResponse(
    val user: AirdropUser? = null,
)

object CurrentUserResponseSerializer : KSerializer<CurrentUserResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.CurrentUserResponse")

    override fun serialize(encoder: Encoder, value: CurrentUserResponse) =
        throw UnsupportedOperationException("CurrentUserResponse is decode-only")

    override fun deserialize(decoder: Decoder): CurrentUserResponse {
        val input = decoder as JsonDecoder
        val json = input.json
        val obj = input.decodeJsonElement() as? JsonObject ?: return CurrentUserResponse()
        val data = obj["data"]
        val fromData = when (data) {
            is JsonObject -> json.decodeOrNull(AirdropUser.serializer(), data["user"])
                ?: json.decodeOrNull(AirdropUser.serializer(), data)
            else -> null
        }
        val user = fromData
            ?: json.decodeOrNull(AirdropUser.serializer(), obj["user"])
            ?: if (!obj.containsKey("data") && !obj.containsKey("user")) {
                json.decodeOrNull(AirdropUser.serializer(), obj)
            } else {
                null
            }
        return CurrentUserResponse(user)
    }
}

@Serializable
data class ProfileUpdateRequest(
    @SerialName("user_id") val userId: Int? = null,
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("user_phone") val userPhone: String? = null,
    @SerialName("user_mobile") val userMobile: String? = null,
    @SerialName("user_country_code") val userCountryCode: String? = null,
    @SerialName("user_trn_number") val userTrnNumber: String? = null,
    @SerialName("user_identity_type") val userIdentityType: String? = null,
    @SerialName("user_identity_number") val userIdentityNumber: String? = null,
    @SerialName("user_dob") val userDob: String? = null,
    @SerialName("user_language") val userLanguage: String? = null,
    @SerialName("user_address_line_1") val userAddressLine1: String? = null,
    @SerialName("user_address_line_2") val userAddressLine2: String? = null,
    @SerialName("user_address_city") val userAddressCity: String? = null,
    @SerialName("user_address_state") val userAddressState: String? = null,
    @SerialName("user_address_country") val userAddressCountry: String? = null,
    @SerialName("pickup_location") val pickupLocation: String? = null,
    @SerialName("payment_currency") val paymentCurrency: String? = null,
    @SerialName("user_tnc") val userTnc: Boolean? = null,
    @SerialName("email_notification") val emailNotification: String? = null,
    @SerialName("sms_notification") val smsNotification: String? = null,
    @SerialName("offers_notification") val offersNotification: String? = null,
)

@Serializable(with = ProfileMutationResponseSerializer::class)
data class ProfileMutationResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val user: AirdropUser? = null,
)

object ProfileMutationResponseSerializer : KSerializer<ProfileMutationResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.ProfileMutationResponse")

    override fun serialize(encoder: Encoder, value: ProfileMutationResponse) =
        throw UnsupportedOperationException("ProfileMutationResponse is decode-only")

    override fun deserialize(decoder: Decoder): ProfileMutationResponse {
        val input = decoder as JsonDecoder
        val json = input.json
        val obj = input.decodeJsonElement() as? JsonObject ?: return ProfileMutationResponse()
        val data = obj["data"]
        val user = when (data) {
            is JsonObject -> json.decodeOrNull(AirdropUser.serializer(), data["user"])
                ?: json.decodeOrNull(AirdropUser.serializer(), data)
            else -> null
        } ?: json.decodeOrNull(AirdropUser.serializer(), obj["user"])
        return ProfileMutationResponse(
            success = obj.flexBool("success"),
            message = obj.flexString("message"),
            user = user,
        )
    }
}

enum class UserDocumentType(val wireName: String) {
    ID_CARD_FORM("id_card_form"),
    AIRDROP_CONTRACT("airdrop_contract"),
    FILE_1583("file_1583"),
    AUTHORIZATION_FORM("authorization_form"),
    IDENTIFICATION_FILE("identification_file"),
    AIRDROP_CONTRACT_FILE_1583("airdrop_contract_file_1583"),
    CUSTOM_FORM("custom_form"),
    TRN("trn"),
    PREORDER("preorder"),
}

@Serializable
data class UserDocumentFile(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @SerialName("file_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val fileName: String? = null,
    @SerialName("file_url")
    @Serializable(with = FlexibleStringSerializer::class)
    val fileUrl: String? = null,
    @SerialName("doc_type")
    @Serializable(with = FlexibleStringSerializer::class)
    val docType: String? = null,
    @SerialName("upload_status")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val uploadStatus: Boolean? = null,
    @SerialName("approved_status")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val approvedStatus: Boolean? = null,
)

// GET /user/documents: fixed doc-type keys, either flat or under {data:{...}}.
@Serializable(with = UserDocumentsSerializer::class)
data class UserDocuments(
    val file1583: UserDocumentFile? = null,
    val airdropContract: UserDocumentFile? = null,
    val authorizationForm: UserDocumentFile? = null,
    val identificationFile: UserDocumentFile? = null,
    val idCardForm: UserDocumentFile? = null,
    val airdropContractFile1583: UserDocumentFile? = null,
    val customForm: UserDocumentFile? = null,
    val trn: UserDocumentFile? = null,
    val preorder: UserDocumentFile? = null,
)

object UserDocumentsSerializer : KSerializer<UserDocuments> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.UserDocuments")

    override fun serialize(encoder: Encoder, value: UserDocuments) =
        throw UnsupportedOperationException("UserDocuments is decode-only")

    override fun deserialize(decoder: Decoder): UserDocuments {
        val input = decoder as JsonDecoder
        val json = input.json
        val obj = input.decodeJsonElement() as? JsonObject ?: return UserDocuments()
        val source = obj["data"] as? JsonObject ?: obj
        fun doc(key: String): UserDocumentFile? =
            json.decodeOrNull(UserDocumentFile.serializer(), source[key])
        return UserDocuments(
            file1583 = doc("file_1583"),
            airdropContract = doc("airdrop_contract"),
            authorizationForm = doc("authorization_form"),
            identificationFile = doc("identification_file"),
            idCardForm = doc("id_card_form"),
            airdropContractFile1583 = doc("airdrop_contract_file_1583"),
            customForm = doc("custom_form"),
            trn = doc("trn"),
            preorder = doc("preorder"),
        )
    }
}

// Upload/read responses for profile image + user documents; the file URL can
// arrive as url/image_url/file_url and path/image_path/file_path, flat or
// wrapped under data.
@Serializable(with = ProfileAssetResponseSerializer::class)
data class ProfileAssetResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val url: String? = null,
    val path: String? = null,
)

object ProfileAssetResponseSerializer : KSerializer<ProfileAssetResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.ProfileAssetResponse")

    override fun serialize(encoder: Encoder, value: ProfileAssetResponse) =
        throw UnsupportedOperationException("ProfileAssetResponse is decode-only")

    override fun deserialize(decoder: Decoder): ProfileAssetResponse {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return ProfileAssetResponse()
        val source = obj["data"] as? JsonObject ?: obj
        return ProfileAssetResponse(
            success = obj.flexBool("success"),
            message = obj.flexString("message"),
            url = source.flexString("url", "image_url", "file_url"),
            path = source.flexString("path", "image_path", "file_path"),
        )
    }
}
