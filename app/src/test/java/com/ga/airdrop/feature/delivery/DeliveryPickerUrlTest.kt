package com.ga.airdrop.feature.delivery

import com.ga.airdrop.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPickerUrlTest {
    @Test
    fun `picker uses the current flavor API origin`() {
        val url = deliveryPickerBaseUrl(BuildConfig.API_BASE_URL)
        val expectedHost = when (BuildConfig.ENV_NAME) {
            "Production" -> "app.airdropja.com"
            "Staging" -> "pre-staging.airdropja.com"
            else -> error("Unexpected environment ${BuildConfig.ENV_NAME}")
        }

        assertEquals(expectedHost, deliveryPickerAllowedHost(BuildConfig.API_BASE_URL))
        assertTrue(url.startsWith(BuildConfig.API_BASE_URL.trimEnd('/') + "/"))
        assertEquals(
            "${BuildConfig.API_BASE_URL.trimEnd('/')}/delivery/picker?embed=ios",
            url,
        )
        if (BuildConfig.ENV_NAME == "Production") {
            assertFalse(url.startsWith(BuildConfig.WEB_BASE_URL.trimEnd('/') + "/api/"))
        }
    }

    @Test
    fun `marker coordinates use a stable locale-independent query`() {
        assertEquals(
            "https://app.airdropja.com/api/v1/delivery/picker" +
                "?embed=ios&lat=18.017900&lng=-76.809900",
            deliveryPickerUrl(
                apiBaseUrl = "https://app.airdropja.com/api/v1/",
                marker = 18.0179 to -76.8099,
            ),
        )
    }
}
