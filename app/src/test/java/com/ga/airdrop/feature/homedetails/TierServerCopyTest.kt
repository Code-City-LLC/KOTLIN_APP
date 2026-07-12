package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.model.ServiceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TierServerCopyTest {
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
