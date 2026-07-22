package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.PackageDetail
import kotlinx.coroutines.CancellationException
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

internal interface OrderSummaryChargesRepository {
    suspend fun packageDetail(
        cartKey: CartStore.CartLineKey,
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<CheckoutShipmentChargeSnapshot>
}

/**
 * Session-bound adapter for the existing Laravel package-detail route.
 *
 * The shared PackagesRepository remains the canonical package repository, but
 * its current method has no request-provenance parameters. This cart-local
 * adapter uses the same Retrofit client and wire models while binding each
 * checkout request to the captured auth generation.
 */
internal class DataOrderSummaryChargesRepository(
    private val api: OrderSummaryChargesApi =
        ApiClient.retrofit.create(OrderSummaryChargesApi::class.java),
) : OrderSummaryChargesRepository {

    override suspend fun packageDetail(
        cartKey: CartStore.CartLineKey,
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<CheckoutShipmentChargeSnapshot> = try {
        val envelope = api.packageDetail(
            packageId = packageId,
            expectedRevision = expectedSession.revision.toString(),
            expectedSessionId = expectedSession.sessionId,
        )
        val detail = envelope.data
            ?: error(envelope.message?.takeIf(String::isNotBlank) ?: "Package details were empty.")
        Result.success(detail.toCheckoutChargeSnapshot(cartKey, packageId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

internal interface OrderSummaryChargesApi {
    @GET("packages/{id}")
    suspend fun packageDetail(
        @Path("id") packageId: Int,
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) expectedRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) expectedSessionId: String,
    ): DataEnvelope<PackageDetail>
}

internal fun PackageDetail.toCheckoutChargeSnapshot(
    cartKey: CartStore.CartLineKey,
    requestedPackageId: Int,
): CheckoutShipmentChargeSnapshot {
    require(cartKey.kind == CartStore.CartLineKind.PACKAGE) {
        "Package details cannot be attached to a sale row."
    }
    require(id == requestedPackageId && requestedPackageId > 0) {
        "Package details did not match the captured shipment."
    }
    require(amount.isNullOrFiniteNonNegative()) { "Declared value was invalid." }
    require(additionalChargesTotal.isNullOrFiniteNonNegative()) {
        "Package charge total was invalid."
    }
    require(exchangeRate == null || (exchangeRate.isFinite() && exchangeRate > 0.0)) {
        "Package exchange rate was invalid."
    }
    require(additionalCharges.all { (name, value) ->
        name.isNotBlank() && value.isFinite() && value >= 0.0
    }) { "Package charge rows were invalid." }

    return CheckoutShipmentChargeSnapshot(
        cartKey = cartKey,
        packageId = requestedPackageId,
        declaredValueUsd = amount,
        additionalCharges = additionalCharges,
        additionalChargesTotalUsd = additionalChargesTotal,
        exchangeRateUsdToJmd = exchangeRate,
    )
}

private fun Double?.isNullOrFiniteNonNegative(): Boolean =
    this == null || (isFinite() && this >= 0.0)
