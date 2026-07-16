package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropNotification
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
        assertEquals("24-48 hour target after clearance", catalog.items.single().processingCopy)
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
    fun rubyFiltersOnlyKnownProcessingAndStorageConflicts() {
        val rows = serverBenefitRows(
            listOf(
                ServiceTier(
                    code = "RUBY",
                    processingCopy = "3–5 business day basic processing",
                    benefitsSummary = listOf(
                        "No free storage included.",
                        "Competitive base shipping rates.",
                    ),
                )
            )
        )
        assertEquals(listOf("Competitive base shipping rates."), rows["RUBY"])
        assertFalse(rows.values.flatten().any { it.contains("3-5", ignoreCase = true) })
    }

    @Test
    fun rubyKeepsNonConflictingProcessingCopyAndDeduplicatesCosmeticDrift() {
        val rows = serverBenefitRows(
            listOf(
                ServiceTier(
                    code = "RUBY",
                    processingCopy = "Standard 2–3 business day processing.",
                    benefitsSummary = listOf(
                        "standard 2-3 business day processing",
                        "Competitive base shipping rates.",
                    ),
                )
            )
        )

        assertEquals(
            listOf(
                "Standard 2–3 business day processing.",
                "Competitive base shipping rates.",
            ),
            rows["RUBY"],
        )
    }

    @Test
    fun serverOwnedBenefitsUseCustomerFacingSaleTerminology() {
        val rows = serverBenefitRows(
            listOf(
                ServiceTier(
                    code = "GOLD",
                    processingCopy = "Priority in pre-auction events.",
                    benefitsSummary = listOf(
                        "Warehouse auctions and holiday offers.",
                        "AUCTION ACCESS",
                        "Gold early sale and auction notifications.",
                    ),
                )
            )
        )

        assertEquals(
            listOf(
                "Priority in pre-sale events.",
                "Warehouse sales and holiday offers.",
                "SALE ACCESS",
                "Gold early sale notifications.",
            ),
            rows["GOLD"],
        )
    }

    @Test
    fun customerFacingSaleCopyPreservesCaseDuringPhraseCleanup() {
        assertEquals("Sale", customerFacingSaleCopy("Auction and auction"))
        assertEquals("SALE", customerFacingSaleCopy("AUCTION and AUCTION"))
        assertEquals("Sales", customerFacingSaleCopy("Auctions and auctions"))
        assertEquals(
            "CLEARANCE EVENTS, LIMITED RELEASES, AND FLASH SALES",
            customerFacingSaleCopy("CLEARANCE EVENTS, AUCTIONS, AND FLASH SALES"),
        )
        assertEquals(
            "Clearance Events, Limited Releases, And Flash Sales",
            customerFacingSaleCopy("Clearance Events, Auctions, And Flash Sales"),
        )
    }

    @Test
    fun notificationSaleCopyPreservesNavigationIdentity() {
        val source = AirdropNotification(
            id = "auction-17",
            title = "Auction starts now",
            body = "Two auctions are ready.",
            type = "auction",
            isRead = true,
            createdAt = "2026-07-16T12:00:00Z",
            route = "auction/product/42",
            referenceId = "42",
            payload = mapOf("screen" to "auction", "package_id" to "42"),
        )

        val customerCopy = source.customerFacingCopy()

        assertEquals("Sale starts now", customerCopy.title)
        assertEquals("Two sales are ready.", customerCopy.body)
        assertEquals(source.id, customerCopy.id)
        assertEquals(source.type, customerCopy.type)
        assertEquals(source.isRead, customerCopy.isRead)
        assertEquals(source.createdAt, customerCopy.createdAt)
        assertEquals(source.route, customerCopy.route)
        assertEquals(source.referenceId, customerCopy.referenceId)
        assertEquals(source.payload, customerCopy.payload)
    }

    @Test
    fun customerTierCodeWinsLegacyNameFallback() {
        assertEquals(
            tierPages.indexOfFirst { it.id == "sapphire" },
            indexForTier(code = "SAVR", name = "Gold Standard"),
        )
    }
}
