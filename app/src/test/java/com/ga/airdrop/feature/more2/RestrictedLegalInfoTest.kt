package com.ga.airdrop.feature.more2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content-integrity guard for the legal "Information" page — ported verbatim
 * from Swift FigmaRestrictedItemsLegalInfoViewController (authorities :24-30).
 * Pins the copy so a drive-by "cleanup" can't silently diverge from Swift.
 */
class RestrictedLegalInfoTest {

    @Test
    fun `five jamaican authorities with https links, Swift order`() {
        val a = RestrictedLegalInfo.jamaicanAuthorities
        assertEquals(5, a.size)
        assertEquals("Jamaica Customs Agency:", a[0].name)
        assertEquals("Trade Board Limited:", a[4].name)
        a.forEach { assertTrue(it.url.startsWith("https://www.")) }
    }

    @Test
    fun `labeling bullets match Swift count and first entry`() {
        assertEquals(4, RestrictedLegalInfo.labelingBullets.size)
        assertTrue(RestrictedLegalInfo.labelingBullets[0].startsWith("Lack English labeling"))
    }

    @Test
    fun `key copy matches Swift verbatim`() {
        assertEquals(
            "AirDrop Logistics LLC. Item Classifications (Jamaica)",
            RestrictedLegalInfo.TITLE,
        )
        assertTrue(RestrictedLegalInfo.LEGAL_NOTICE_BODY.startsWith("This guide is illustrative"))
        assertTrue(RestrictedLegalInfo.US_AUTHORITIES_BODY.contains("(EAR)"))
        // Kemar copy fix: corrupted "export-asian" → Swift-verbatim
        // "export and import regulations".
        assertTrue(RestrictedLegalInfo.DISCLAIMER.contains("export and import regulations"))
        assertFalse(RestrictedLegalInfo.DISCLAIMER.contains("asian"))
        assertEquals("https://www.bis.doc.gov", RestrictedLegalInfo.bisLink.url)
    }
}
