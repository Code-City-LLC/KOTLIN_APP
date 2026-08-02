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
            "Production" -> "airdropja.com"
            "Staging" -> "pre-staging.airdropja.com"
            else -> error("Unexpected environment ${BuildConfig.ENV_NAME}")
        }

        assertEquals(expectedHost, deliveryPickerAllowedHost(BuildConfig.API_BASE_URL))
        assertTrue(url.startsWith(BuildConfig.API_BASE_URL.trimEnd('/') + "/"))
        assertEquals(
            "${BuildConfig.API_BASE_URL.trimEnd('/')}/delivery/picker?embed=ios",
            url,
        )
        // The old guard here asserted the picker URL must NOT start with
        // WEB_BASE_URL + "/api/". That caught the original defect, where the URL
        // was built from WEB_BASE_URL — a DIFFERENT host on production that
        // 404'd. Staging never caught it because both bases were already one
        // host there.
        //
        // Production now uses that same host, so the two bases share an origin
        // on BOTH flavors and the assertion can no longer fail for the reason it
        // was written. It can only fail because the bases agree, which is now
        // correct. Removing it is part of the host change, not a weakening: the
        // assertEquals above still pins the URL to API_BASE_URL, which is what
        // actually guards the defect and survives any number of hosts merging.
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
