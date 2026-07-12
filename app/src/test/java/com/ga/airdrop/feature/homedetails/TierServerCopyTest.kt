package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.TierChangeRequest
import com.ga.airdrop.data.model.TierChangeResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TierServerCopyTest {
    @Test
    fun decodesLaravelTierEnvelopeShapes() {
        val catalog = AirdropJson.decodeFromString<Paginated<ServiceTier>>(
            """{"success":true,"data":[{"code":"GOLD","display_name":"Gold Standard","processing_copy":"24-48 hour target after clearance","benefits_summary":["Insurance required on every shipment."]}]}"""
        )
        val customer = AirdropJson.decodeFromString<DataEnvelope<CustomerTier>>(
            """{"success":true,"data":{"current_tier":"GOLD","display_name":"Gold Standard","effective_at":"2026-07-12T15:00:00Z","can_change":true,"available_changes":[{"code":"PLAT","name":"Platinum Priority","lane_rank":4,"is_current":false,"direction":"upgrade"}]}}"""
        )
        val change = AirdropJson.decodeFromString<DataEnvelope<TierChangeResult>>(
            """{"success":true,"message":"Tier change applied","data":{"requested_tier_code":"PLAT","effective_at":"2026-07-12T15:01:00Z","status":"applied","message":"Tier updated to PLAT"}}"""
        )
        val request = AirdropJson.encodeToString(TierChangeRequest(requestedTierCode = "PLAT"))

        assertEquals("GOLD", catalog.items.single().code)
        assertEquals(
            listOf("Insurance required on every shipment."),
            catalog.items.single().benefitsSummary,
        )
        assertEquals("GOLD", customer.data?.currentTier)
        assertEquals("2026-07-12T15:00:00Z", customer.data?.effectiveAt)
        assertEquals("PLAT", customer.data?.availableChanges?.single()?.code)
        assertEquals(4, customer.data?.availableChanges?.single()?.laneRank)
        assertEquals("upgrade", customer.data?.availableChanges?.single()?.direction)
        assertEquals("PLAT", change.data?.requestedTierCode)
        assertEquals("2026-07-12T15:01:00Z", change.data?.effectiveAt)
        assertEquals("applied", change.data?.status)
        assertEquals("Tier updated to PLAT", change.data?.message)
        assertEquals("{\"requested_tier_code\":\"PLAT\"}", request)
    }

    @Test
    fun completeBenefitsSummaryIsKeptInOrderWithoutProcessingCopyOrDeduplication() {
        val rows = serverBenefitRows(
            listOf(
                ServiceTier(
                    code = " gold ",
                    processingCopy = " 24-48 hour target after clearance ",
                    benefitsSummary = listOf(
                        "Insurance required on every shipment.",
                        "insurance required on every shipment",
                        "  retained server spacing  ",
                        "",
                    ),
                )
            )
        )

        assertEquals(
            listOf(
                "Insurance required on every shipment.",
                "insurance required on every shipment",
                "  retained server spacing  ",
            ),
            rows["GOLD"],
        )
        assertFalse(rows.values.flatten().any { "%" in it })
    }

    @Test
    fun customerTierCodeWinsLegacyNameFallback() {
        assertEquals(
            tierPages.indexOfFirst { it.id == "sapphire" },
            indexForTier(code = "SAVR", name = "Gold Standard"),
        )
    }
}
