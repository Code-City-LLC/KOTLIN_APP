package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Tier API contract (Laravel pre_staging Tier controllers/services)
 * so the app decodes exactly what the backend sends. The app renders these
 * values verbatim — eligibility and money math are never recomputed locally,
 * so decode fidelity IS the correctness guarantee.
 */
class TierModelsTest {

    private inline fun <reified T> decode(json: String): T =
        AirdropJson.decodeFromString(json)

    @Test
    fun `service tiers catalogue decodes with eligibility flags`() {
        // benefits_summary is a STRING ARRAY (service_tiers cast `array`), now
        // populated live. An earlier String? here threw on the array and broke
        // the whole catalogue parse — this pins the correct shape.
        val json = """
            {"success":true,"message":"Service tiers retrieved","data":[
              {"code":"DIAM","display_name":"Diamond Elite","badge":"diamond","lane_rank":5,
               "is_priority":true,"aircoins_eligible":true,"free_return_lb_cap":30,
               "processing_copy":"Fastest lane",
               "benefits_summary":["VIP priority — next possible ship-out","Free returns up to 30 lb"]},
              {"code":"RUBY","display_name":"Ruby Starter","badge":"ruby","lane_rank":2,
               "is_priority":false,"aircoins_eligible":false,"free_return_lb_cap":0,
               "processing_copy":null,"benefits_summary":null}
            ]}
        """.trimIndent()
        val tiers = decode<DataEnvelope<List<ServiceTier>>>(json).data!!
        assertEquals(2, tiers.size)
        assertEquals("Diamond Elite", tiers[0].displayName)
        assertTrue(tiers[0].aircoinsEligible)
        assertEquals(30.0, tiers[0].freeReturnLbCap, 0.0)
        // Backend benefit bullets decode as a list...
        assertEquals(2, tiers[0].benefitsSummary.size)
        assertEquals("VIP priority — next possible ship-out", tiers[0].benefitsSummary[0])
        // ...and a null benefits_summary coerces to an empty list (fallback copy).
        assertTrue(tiers[1].benefitsSummary.isEmpty())
        // Ruby Starter earns no AirCoins and no free returns — from the API.
        assertFalse(tiers[1].aircoinsEligible)
        assertEquals(0.0, tiers[1].freeReturnLbCap, 0.0)
    }

    @Test
    fun `error envelope decodes the machine-readable error_code`() {
        // Live shape: {"success":false,"message":"…","error_code":"NO_RATE_CARD"}.
        val json = """{"success":false,"message":"No rate card","error_code":"NO_RATE_CARD"}"""
        val envelope = decode<com.ga.airdrop.data.api.ApiErrorEnvelope>(json)
        assertEquals("NO_RATE_CARD", envelope.errorCode)
        assertEquals("No rate card", envelope.message)
        // Absent error_code stays null (non-tier failures).
        val plain = decode<com.ga.airdrop.data.api.ApiErrorEnvelope>(
            """{"message":"Something went wrong"}""",
        )
        assertNull(plain.errorCode)
    }

    @Test
    fun `customer tier decodes with available changes`() {
        val json = """
            {"success":true,"message":"Customer tier retrieved","data":{
              "current_tier":"SAVR","display_name":"Sapphire Saver","badge":"sapphire",
              "effective_at":"2026-07-07T12:00:00+00:00","can_change":true,
              "available_changes":[
                {"code":"GOLD","name":"Gold Standard","lane_rank":3,"is_current":false,"direction":"upgrade"},
                {"code":"SAVR","name":"Sapphire Saver","lane_rank":1,"is_current":true,"direction":"same"}
              ],
              "aircoins_eligible":false,"free_return_eligible":false,"free_return_lb_cap":0}}
        """.trimIndent()
        val tier = decode<DataEnvelope<CustomerTier>>(json).data!!
        assertEquals("SAVR", tier.currentTier)
        assertFalse(tier.aircoinsEligible)
        assertFalse(tier.freeReturnEligible)
        assertEquals("upgrade", tier.availableChanges[0].direction)
        assertTrue(tier.availableChanges[1].isCurrent)
    }

    @Test
    fun `shipment quote decodes line items, insurance and expiry as returned`() {
        val json = """
            {"success":true,"message":"Quote created","data":{
              "quote_reference":"QT-2026-0001","customer_tier":"GOLD","method":"AIR",
              "destination":"JM","currency":"USD",
              "line_items":[
                {"code":"base_shipping","label":"Base shipping","amount":12.50,
                 "meta":{"weight":3.2,"rate_card_item_id":7}},
                {"code":"insurance","label":"Insurance","amount":3.00,
                 "meta":{"insured_value":200,"blocks":2,"mandatory":true}},
                {"code":"aircoins_credit","label":"AirCoins earned","amount":0.0,
                 "meta":{"aircoins_earned":12.5,"on_subtotal":15.50}}
              ],
              "subtotal":15.50,"total_due":15.50,"status":"active",
              "is_expired":false,"expires_at":"2026-07-08T12:00:00+00:00",
              "insurance_options":{"insured_value":200,"rate_per_100":1.5,"block_size":100,
                "blocks":2,"blocks_used":2,"premium":3.0,"max_coverage":null,
                "covered_value":200,"can_decline":false,"mandatory":true,
                "explicit_required":false,"rule_version_id":4},
              "insurance_choice_required":false,"aircoins_earned":12.5,
              "rule_version_ids":{"rate":1,"insurance":4}}}
        """.trimIndent()
        val quote = decode<DataEnvelope<ShipmentQuote>>(json).data!!
        assertEquals("QT-2026-0001", quote.quoteReference)
        assertEquals(3, quote.lineItems.size)
        assertEquals("base_shipping", quote.lineItems[0].code)
        assertEquals(12.50, quote.lineItems[0].amount, 0.0)
        assertEquals(15.50, quote.totalDue, 0.0)
        assertFalse(quote.isExpired)
        assertEquals(false, quote.insuranceChoiceRequired)
        assertEquals(3.0, quote.insuranceOptions!!.premium, 0.0)
        assertFalse(quote.insuranceOptions!!.explicitRequired)
    }

    @Test
    fun `insurance options decode the explicit-choice contract (SAVR)`() {
        val json = """
            {"success":true,"message":"Insurance options","data":{
              "insured_value":150,"rate_per_100":1.5,"block_size":100,"blocks":2,
              "blocks_used":2,"premium":3.0,"max_coverage":5000,"covered_value":150,
              "can_decline":true,"mandatory":false,"explicit_required":true,
              "rule_version_id":9}}
        """.trimIndent()
        val options = decode<DataEnvelope<InsuranceOptions>>(json).data!!
        assertTrue(options.explicitRequired)
        assertTrue(options.canDecline)
        assertFalse(options.mandatory)
        assertEquals(100, options.blockSize)
    }

    @Test
    fun `return eligibility decodes caps and charges from the backend`() {
        val json = """
            {"success":true,"message":"Return eligibility","data":{
              "tier_code":"GOLD","eligible":true,"free_lb_cap":10,"free_lb_applied":10,
              "overage_weight":2.5,"overage_rate_per_lb":4,"estimated_return_charge":10.0,
              "rule_version_id":2}}
        """.trimIndent()
        val eligibility = decode<DataEnvelope<ReturnEligibility>>(json).data!!
        assertTrue(eligibility.eligible)
        assertEquals(10.0, eligibility.freeLbCap, 0.0)
        assertEquals(10.0, eligibility.estimatedReturnCharge, 0.0)
    }

    @Test
    fun `package tier info decodes insurance state and flags`() {
        val json = """
            {"success":true,"message":"Package tier","data":{
              "package_id":41,"tier_code":"SAVR","pricing_snapshot":null,
              "snapshot_at":"2026-07-07T09:00:00+00:00",
              "insurance":{"selected":false,"declined":true,"insured_value":0,
                "premium":0,"status":"recorded","selected_at":null},
              "flags":{"priority_customer":false,"ship_immediately":false,
                "cutoff_eligible":true,"note":null}}}
        """.trimIndent()
        val info = decode<DataEnvelope<PackageTierInfo>>(json).data!!
        assertEquals(41, info.packageId)
        assertEquals("SAVR", info.tierCode)
        assertTrue(info.insurance!!.declined)
        assertTrue(info.flags!!.cutoffEligible)
        assertNull(info.flags!!.note)
    }

    @Test
    fun `tier change result and error envelope decode`() {
        val ok = """
            {"success":true,"message":"Tier change applied","data":{
              "requested_tier_code":"GOLD","effective_at":"2026-07-07T13:00:00+00:00",
              "status":"applied","message":"Tier updated to GOLD"}}
        """.trimIndent()
        val result = decode<DataEnvelope<TierChangeResult>>(ok).data!!
        assertEquals("GOLD", result.requestedTierCode)
        assertEquals("applied", result.status)

        // Error envelope: success=false is the failure signal the repository
        // checks FIRST. (data is not asserted null here — the shared envelope
        // serializer deliberately retries the top-level object as the payload
        // when `data` is null, mirroring Swift's bare-decode fallback.)
        val failed = decode<DataEnvelope<TierChangeResult>>(
            """{"success":false,"message":"Failed to change tier","data":null}""",
        )
        assertEquals(false, failed.success)
        assertEquals("Failed to change tier", failed.message)
    }
}
