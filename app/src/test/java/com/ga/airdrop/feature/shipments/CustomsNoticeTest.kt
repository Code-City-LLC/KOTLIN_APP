package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Customs Notice — Swift FigmaCustomsNoticeViewController / Figma
 * 40008798:29642 port guards: the charge-name matcher mirrors Swift
 * isCustomsDutyCharge (:1621), and the copy is pinned verbatim so cleanup
 * passes cannot silently diverge from the source of truth.
 */
class CustomsNoticeTest {

    @Test
    fun `matcher mirrors Swift - needs both custom and duty, any case`() {
        assertTrue(isCustomsDutyCharge("Customs Duty"))
        assertTrue(isCustomsDutyCharge("custom duty"))
        assertTrue(isCustomsDutyCharge("CUSTOMS IMPORT DUTY"))
        assertFalse(isCustomsDutyCharge("Customs Processing"))
        assertFalse(isCustomsDutyCharge("Duty Free"))
        assertFalse(isCustomsDutyCharge("Freight"))
    }

    @Test
    fun `copy pinned verbatim to Swift-Figma source`() {
        assertEquals("Customs Notice", CustomsNoticeContent.TITLE)
        assertTrue(CustomsNoticeContent.LEAD.startsWith("Jamaica Customs calculates import duties"))
        assertEquals(3, CustomsNoticeContent.bullets.size)
        assertEquals("Cost:", CustomsNoticeContent.bullets[0].first)
        assertEquals("Insurance:", CustomsNoticeContent.bullets[1].first)
        assertEquals("Freight:", CustomsNoticeContent.bullets[2].first)
        assertEquals(3, CustomsNoticeContent.closingParagraphs.size)
        assertTrue(CustomsNoticeContent.closingParagraphs[1].contains("20% to 50% of the CIF value"))
        assertTrue(CustomsNoticeContent.closingParagraphs[2].contains("Jamaica Customs Agency"))
    }
}
