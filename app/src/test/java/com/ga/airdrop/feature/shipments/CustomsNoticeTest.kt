package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.designsystem.components.formatUsdJmd
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

    /**
     * The lead is split so the CIF phrase can be an inline tap target. The
     * RENDERED paragraph must stay character-identical to Figma — a stray space
     * introduced while editing one of the three parts would be invisible in
     * review but visible on screen.
     */
    @Test
    fun `split lead reassembles to the exact Figma paragraph`() {
        assertEquals(
            "Jamaica Customs calculates import duties not on the declared value or invoice " +
                "amount, but on the CIF (Cost, Insurance, and Freight) value of the shipment.",
            CustomsNoticeContent.LEAD,
        )
        assertEquals(
            CustomsNoticeContent.LEAD,
            CustomsNoticeContent.LEAD_PREFIX +
                CustomsNoticeContent.LEAD_CIF_PHRASE +
                CustomsNoticeContent.LEAD_SUFFIX,
        )
        // The tappable phrase must actually occur in the paragraph, exactly once.
        assertEquals(
            CustomsNoticeContent.LEAD.indexOf(CustomsNoticeContent.LEAD_CIF_PHRASE),
            CustomsNoticeContent.LEAD.lastIndexOf(CustomsNoticeContent.LEAD_CIF_PHRASE),
        )
    }

    /**
     * CIF table cells — Figma 40001761:29633 shows "1.00 / 161.00" style pairs.
     * A component the server does not send must render an em-dash rather than a
     * fabricated 0.00, which would understate the customer's landed cost.
     */
    @Test
    fun `CIF cells pair USD with JMD and never invent a missing component`() {
        assertEquals("1.00 / 161.00", formatUsdJmd(1.0, 161.0))
        assertEquals("9.50 / 1,529.50", formatUsdJmd(9.5, 161.0))
        assertEquals("—", formatUsdJmd(null, 161.0))
    }
}
