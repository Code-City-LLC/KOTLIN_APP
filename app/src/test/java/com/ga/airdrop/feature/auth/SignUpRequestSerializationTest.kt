package com.ga.airdrop.feature.auth

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.SignUpRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpRequestSerializationTest {

    /**
     * KEMAR RULING 2026-07-19 (Swift 64f4fdc): TRN + identity documents are
     * deliberately NOT collected at sign-up. Customers add them via Profile
     * after shipping a package. This test locks the ruling — the sign-up
     * payload must never carry identity keys again.
     */
    @Test
    fun signUpPayloadCarriesNoIdentityFieldsPerKemarRuling() {
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
            userHearType = "Other",
            userPickupLocation = "Kingston",
            userTnc = true,
            userAuth = true,
        )

        val encoded = AirdropJson.encodeToString(request)
        val fields = AirdropJson.parseToJsonElement(encoded) as JsonObject

        assertFalse(fields.containsKey("user_trn_number"))
        assertFalse(fields.containsKey("user_identity_type"))
        assertFalse(fields.containsKey("user_identity_number"))
        assertFalse(fields.containsKey("indentity_type"))
        assertFalse(fields.containsKey("identity_type"))
        // The intentionally misspelled backend password field stays verbatim.
        assertTrue(fields.containsKey("comfirm_passsord"))
    }
}
