package com.ga.airdrop.feature.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Legacy server-generated form downloads — Swift
 * FigmaDocumentsViewController.legacyDownloadURL(for:) (:770-793) parity:
 * contract keys the id on `user_documenttype`, 1583 + custom-auth on
 * `user_id`; ID Card and TRN have NO legacy form; blank/absent user id
 * disables the fallback entirely.
 */
class DocumentsLegacyDownloadTest {

    private val base = "https://pre-staging.airdropja.com/airdrop/inc"

    @Test
    fun `contract keys id on user_documenttype`() {
        assertEquals(
            "$base/api_download-contract-form.php?user_documenttype=14172",
            legacyDownloadUrl("airdrop_contract", "14172", base),
        )
    }

    @Test
    fun `1583 and custom auth key id on user_id`() {
        assertEquals(
            "$base/api_download_file_1583.php?user_id=14172",
            legacyDownloadUrl("file_1583", "14172", base),
        )
        assertEquals(
            "$base/api_form_authorization.php?user_id=14172",
            legacyDownloadUrl("authorization_form", "14172", base),
        )
    }

    @Test
    fun `id card and trn have no legacy form`() {
        assertNull(legacyDownloadUrl("id_card_form", "14172", base))
        assertNull(legacyDownloadUrl("trn", "14172", base))
    }

    @Test
    fun `blank or missing user id disables the fallback`() {
        assertNull(legacyDownloadUrl("airdrop_contract", null, base))
        assertNull(legacyDownloadUrl("airdrop_contract", "", base))
        assertNull(legacyDownloadUrl("airdrop_contract", "   ", base))
    }

    @Test
    fun `trailing slash on base is normalized`() {
        assertEquals(
            "$base/api_download_file_1583.php?user_id=7",
            legacyDownloadUrl("file_1583", "7", "$base/"),
        )
    }
}
