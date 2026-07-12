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

        assertEquals(
            listOf(
                "24-48 hour target after clearance",
                "Insurance required on every shipment.",
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
