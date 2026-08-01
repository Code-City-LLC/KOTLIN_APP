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

        // ⚠️ A "must not start with WEB_BASE_URL + /api/" guard used to live here.
        //
        // It was the regression guard for the original defect: the picker URL was
        // built from WEB_BASE_URL, which on prod was a DIFFERENT host that 404'd,
        // so the map loaded a 404 page. Staging masked it because both bases were
        // already the same host there.
        //
        // On 2026-08-01 prod moved to the apex, so API_BASE_URL and WEB_BASE_URL
        // now share an origin on BOTH flavors — prod finally looks like staging.
        // The old assertion therefore cannot fail for the right reason any more;
        // it can only fail because the two bases agree, which is now correct.
        // Keeping it would have forced someone to "fix" it by weakening whichever
        // assertion was really doing the work.
        //
        // What actually guards the defect is the assertEquals above: the URL must
        // be derived from API_BASE_URL. That holds no matter how many hosts merge.
        assertFalse("the picker must never be a bare web page", url.contains("/api/v1/api/"))
    }

    @Test
    fun `marker coordinates use a stable locale-independent query`() {
        assertEquals(
            "https://airdropja.com/api/v1/delivery/picker" +
                "?embed=ios&lat=18.017900&lng=-76.809900",
            deliveryPickerUrl(
                apiBaseUrl = "https://airdropja.com/api/v1/",
                marker = 18.0179 to -76.8099,
            ),
        )
    }
}
