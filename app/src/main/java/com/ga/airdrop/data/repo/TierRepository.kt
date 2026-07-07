package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.CustomsEstimate
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.InsuranceOptions
import com.ga.airdrop.data.model.InsuranceSelection
import com.ga.airdrop.data.model.InsuranceSelectionRequest
import com.ga.airdrop.data.model.PackageTierInfo
import com.ga.airdrop.data.model.RatePreview
import com.ga.airdrop.data.model.ReturnEligibility
import com.ga.airdrop.data.model.ReturnQuote
import com.ga.airdrop.data.model.ReturnQuoteRequest
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.ShipmentQuote
import com.ga.airdrop.data.model.ShipmentQuoteRequest
import com.ga.airdrop.data.model.TierChangeRequest
import com.ga.airdrop.data.model.TierChangeResult

/**
 * The current-tier + change subset the Customer Tier page depends on. A narrow
 * seam so the ViewModel can be unit-tested without a live backend (the whole
 * [TierRepository] implements it).
 */
interface CustomerTierGateway {
    suspend fun customerTier(): Result<CustomerTier>
    suspend fun changeTier(requestedTierCode: String): Result<TierChangeResult>
}

/**
 * Tier system data access — Laravel is the single source of truth for every
 * tier, price, insurance, customs, return and AirCoins rule. This layer only
 * fetches and returns backend values; nothing here (or above it) recomputes
 * money math or eligibility (Kemar tier brief).
 *
 * Customs note (CUSTOMS FREEZE ruling #16965): [customsEstimate] exists so the
 * contract is typed, but no screen may render it until the group unblocks the
 * tier customs path. The original shipping-calculator remains the only
 * customer-facing customs number.
 */
class TierRepository(private val service: AirdropApiService) : CustomerTierGateway {

    /** GET /service-tiers — the tier catalogue (badges, lanes, eligibility). */
    suspend fun serviceTiers(): Result<List<ServiceTier>> =
        apiResult { service.serviceTiers().requireData("service tiers") }

    /** GET /customers/me/tier — the signed-in customer's tier + benefits. */
    override suspend fun customerTier(): Result<CustomerTier> =
        apiResult { service.customerTier().requireData("customer tier") }

    /** PATCH /customers/me/tier — backend-validated upgrade/downgrade. */
    override suspend fun changeTier(requestedTierCode: String): Result<TierChangeResult> =
        apiResult {
            service.changeTier(TierChangeRequest(requestedTierCode)).requireData("tier change")
        }

    /** GET /rates — rate sheet preview for display only, never final math. */
    suspend fun ratePreview(
        method: String? = null,
        tierCode: String? = null,
        destination: String? = null,
    ): Result<RatePreview> =
        apiResult {
            service.ratePreview(method, tierCode, destination).requireData("rate preview")
        }

    /**
     * POST /shipments/quote — the backend's immutable quote snapshot. Line
     * items and totals render exactly as returned. When the quote reports
     * expired, callers must request a fresh one before checkout proceeds.
     */
    suspend fun createShipmentQuote(request: ShipmentQuoteRequest): Result<ShipmentQuote> =
        apiResult { service.createShipmentQuote(request).requireData("shipment quote") }

    /** GET /shipments/quotes/{reference} — re-fetch an existing quote. */
    suspend fun shipmentQuote(reference: String): Result<ShipmentQuote> =
        apiResult { service.shipmentQuote(reference).requireData("shipment quote") }

    /** GET /insurance/options — premium + whether an explicit choice is required. */
    suspend fun insuranceOptions(
        insuredValue: Double,
        tierCode: String? = null,
        method: String? = null,
    ): Result<InsuranceOptions> =
        apiResult {
            service.insuranceOptions(insuredValue, tierCode, method)
                .requireData("insurance options")
        }

    /** POST /packages/{id}/insurance-selection — record select OR decline. */
    suspend fun selectInsurance(packageId: Int, insuredValue: Double): Result<InsuranceSelection> =
        apiResult {
            service.saveInsuranceSelection(
                packageId,
                InsuranceSelectionRequest(selected = true, insuredValue = insuredValue),
            ).requireData("insurance selection")
        }

    /** POST /packages/{id}/insurance-selection — record an explicit decline. */
    suspend fun declineInsurance(packageId: Int): Result<InsuranceSelection> =
        apiResult {
            service.saveInsuranceSelection(
                packageId,
                InsuranceSelectionRequest(declined = true),
            ).requireData("insurance selection")
        }

    /**
     * GET /customs/estimate — typed contract only. CUSTOMS FREEZE (#16965):
     * do not render this in any screen until the group unblocks it.
     */
    suspend fun customsEstimate(
        declaredValue: Double,
        itemName: String? = null,
        destination: String? = null,
        method: String? = null,
    ): Result<CustomsEstimate> =
        apiResult {
            service.customsEstimate(declaredValue, itemName, destination, method)
                .requireData("customs estimate")
        }

    /** GET /returns/eligibility — free returns per the backend rule only. */
    suspend fun returnEligibility(weight: Double, tierCode: String? = null): Result<ReturnEligibility> =
        apiResult {
            service.returnEligibility(weight, tierCode).requireData("return eligibility")
        }

    /** POST /returns/quote — return charge line items, rendered as returned. */
    suspend fun returnQuote(weight: Double, tierCode: String? = null): Result<ReturnQuote> =
        apiResult {
            service.returnQuote(ReturnQuoteRequest(weight, tierCode)).requireData("return quote")
        }

    /** GET /packages/{id}/tier — a package's tier snapshot, insurance, flags. */
    suspend fun packageTier(packageId: Int): Result<PackageTierInfo> =
        apiResult { service.packageTier(packageId).requireData("package tier") }
}

/** Unwrap the Laravel `{success, message, data}` envelope or fail clearly. */
private fun <T> DataEnvelope<T>.requireData(what: String): T {
    if (success == false) {
        throw ApiException(message ?: "The server could not provide the $what.")
    }
    return data ?: throw ApiException(message ?: "The server returned no $what data.")
}
