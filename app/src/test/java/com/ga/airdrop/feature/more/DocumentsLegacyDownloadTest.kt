package com.ga.airdrop.feature.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The deleted /airdrop/inc PHP tier must never return. Blank compliance forms
 * use Laravel's bearer-authenticated mobile API, with no customer id in the
 * URL. ID Card and TRN remain upload-only.
 */
class DocumentsMobileFormDownloadTest {

    private val base = "https://pre-staging.airdropja.com/api/v1"

    @Test
    fun `all generated forms use the authenticated mobile routes`() {
        assertEquals(
            "$base/user/forms/contract/download",
            mobileFormDownloadUrl("airdrop_contract", base),
        )
        assertEquals(
            "$base/user/forms/1583/download",
            mobileFormDownloadUrl("file_1583", base),
        )
        assertEquals(
            "$base/user/forms/authorization/download",
            mobileFormDownloadUrl("authorization_form", base),
        )
    }

    @Test
    fun `id card and trn have no generated form`() {
        assertNull(mobileFormDownloadUrl("id_card_form", base))
        assertNull(mobileFormDownloadUrl("trn", base))
    }

    @Test
    fun `trailing slash on base is normalized`() {
        assertEquals(
            "$base/user/forms/1583/download",
            mobileFormDownloadUrl("file_1583", "$base/"),
        )
    }
}
