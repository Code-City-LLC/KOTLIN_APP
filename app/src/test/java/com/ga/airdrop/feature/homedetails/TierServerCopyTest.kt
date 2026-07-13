package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.ServiceTier
import kotlinx.serialization.decodeFromString
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
            """{"success":true,"data":{"current_tier":"GOLD","display_name":"Gold Standard","can_change":true}}"""
        )

        assertEquals("GOLD", catalog.items.single().code)
        assertEquals(
            listOf("Insurance required on every shipment."),
            catalog.items.single().benefitsSummary,
        )
        assertEquals("GOLD", customer.data?.currentTier)
    }

    @Test
    fun serverCopyIsTrimmedDeduplicatedAndKeptInOrder() {
        val rows = serverBenefitRows(
            listOf(
                ServiceTier(
                    code = " gold ",
                    processingCopy = " 24-48 hour target after clearance ",
                    benefitsSummary = listOf(
                        "Insurance required on every shipment.",
                        "insurance required on every shipment",
                        "",
                    ),
                )
            )
        )

        // benefits_summary only — processing_copy must NOT be prepended
        // (Kemar #22831: Ruby's "3-5 business day" line arrives via
        // processing_copy; it is a lane label, not a benefit row).
        assertEquals(
            listOf("Insurance required on every shipment."),
            rows["GOLD"],
        )
        assertFalse(rows.values.flatten().any { "%" in it })
    }

    @Test
    fun processingCopyNeverLeaksIntoBenefitRows() {
        val rows = serverBenefitRows(
            listOf(
                ServiceTier(
                    code = "RUBY",
                    processingCopy = "3-5 business day basic processing",
                    benefitsSummary = listOf("Competitive base shipping rates."),
                )
            )
        )
        assertEquals(listOf("Competitive base shipping rates."), rows["RUBY"])
        assertFalse(rows.values.flatten().any { it.contains("3-5", ignoreCase = true) })
    }

    @Test
    fun customerTierCodeWinsLegacyNameFallback() {
        assertEquals(
            tierPages.indexOfFirst { it.id == "sapphire" },
            indexForTier(code = "SAVR", name = "Gold Standard"),
        )
    }
}
