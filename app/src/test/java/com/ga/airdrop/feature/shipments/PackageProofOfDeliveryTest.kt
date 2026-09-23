package com.ga.airdrop.feature.shipments

import com.ga.airdrop.data.model.PackageDetail
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageProofOfDeliveryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `valid embedded signature decodes and produces the Swift-equivalent presentation`() {
        val detail = json.decodeFromString<PackageDetail>(
            """
            {
              "id": 42,
              "proof_of_delivery": {
                "signature_url": "https://pre-staging.airdropja.com/api/v1/packages/42/proof-of-delivery/signature",
                "delivered_at": "2026-08-10T14:22:19Z",
                "received_by": "  D. Grant  "
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            "https://pre-staging.airdropja.com/api/v1/packages/42/proof-of-delivery/signature",
            detail.proofOfDelivery?.signatureUrl,
        )
        assertEquals(
            "Received by D. Grant \u00B7 10 Aug 2026",
            signedForPresentation(
                ShipmentProofOfDelivery(
                    signatureUrl = requireNotNull(detail.proofOfDelivery?.signatureUrl),
                    deliveredAt = detail.proofOfDelivery?.deliveredAt,
                    receivedBy = detail.proofOfDelivery?.receivedBy,
                ),
            )?.meta,
        )
    }

    @Test
    fun `blank or malformed signature is absent instead of becoming an empty panel`() {
        val blank = json.decodeFromString<PackageDetail>(
            """{"id":42,"proof_of_delivery":{"signature_url":"   "}}""",
        )
        val relative = json.decodeFromString<PackageDetail>(
            """{"id":42,"proof_of_delivery":{"signature_url":"/packages/42/signature"}}""",
        )
        val unsupportedScheme = json.decodeFromString<PackageDetail>(
            """{"id":42,"proof_of_delivery":{"signature_url":"file:///tmp/signature.png"}}""",
        )

        assertNull(blank.proofOfDelivery)
        assertNull(relative.proofOfDelivery)
        assertNull(unsupportedScheme.proofOfDelivery)
    }

    @Test
    fun `presentation accepts absolute HTTP or HTTPS but rejects unsafe URL forms`() {
        assertTrue(
            signedForPresentation(
                ShipmentProofOfDelivery(signatureUrl = "http://airdropja.com/proof.png"),
            ) != null,
        )
        assertTrue(
            signedForPresentation(
                ShipmentProofOfDelivery(signatureUrl = "https://airdropja.com/proof.png"),
            ) != null,
        )
        assertNull(signedForPresentation(ShipmentProofOfDelivery(signatureUrl = "proof.png")))
        assertNull(signedForPresentation(ShipmentProofOfDelivery(signatureUrl = "file:///tmp/proof.png")))
    }

    @Test
    fun `signature metadata never leaks unparseable timestamps`() {
        val dateOnly = signedForPresentation(
            ShipmentProofOfDelivery(
                signatureUrl = "https://airdropja.com/proof.png",
                deliveredAt = "2026-08-10 14:22:19",
            ),
        )
        val nameOnly = signedForPresentation(
            ShipmentProofOfDelivery(
                signatureUrl = "https://airdropja.com/proof.png",
                deliveredAt = "not a date",
                receivedBy = "D. Grant",
            ),
        )
        val neither = signedForPresentation(
            ShipmentProofOfDelivery(signatureUrl = "https://airdropja.com/proof.png"),
        )

        assertEquals("10 Aug 2026", dateOnly?.meta)
        assertEquals("Received by D. Grant", nameOnly?.meta)
        assertNull(neither?.meta)
        assertFalse(nameOnly?.meta.orEmpty().contains("not a date"))
    }
}
