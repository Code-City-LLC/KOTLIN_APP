package com.ga.airdrop.feature.auth

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.SignUpRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpRequestSerializationTest {

    @Test
    fun signUpIdentityFieldsUseCanonicalKeyWithCurrentLaravelAlias() {
        val request = SignUpRequest(
            firstName = "Kemar",
            lastName = "Campbell",
            email = "kemar@example.com",
            password = "password123",
            confirmPassword = "password123",
            userCountryCode = "1-876",
            userMobile = "5290736",
            userAddressLine1 = "6175 NW 167th Street",
            userAddressLine2 = "G6",
            userAddressCity = "Miami",
            userAddressState = "Florida",
            userAddressCountry = "United States",
            userTrnNumber = "123456789",
            userIdentityType = "Passport",
            userIdentityNumber = "P-4242",
            legacyIdentityType = "Passport",
            userHearType = "Other",
            userPickupLocation = "Kingston",
            userTnc = true,
            userAuth = true,
        )

        val encoded = AirdropJson.encodeToString(request)
        val fields = AirdropJson.parseToJsonElement(encoded) as JsonObject

        assertEquals("123456789", fields["user_trn_number"]?.jsonPrimitive?.content)
        assertEquals("Passport", fields["user_identity_type"]?.jsonPrimitive?.content)
        assertEquals("P-4242", fields["user_identity_number"]?.jsonPrimitive?.content)
        assertEquals("Passport", fields["indentity_type"]?.jsonPrimitive?.content)
        assertTrue(fields.containsKey("comfirm_passsord"))
        assertFalse(fields.containsKey("identity_type"))
    }
}
