package com.ga.airdrop.feature.shipments

/*
 * Data contracts for the SHIPMENTS feature group.
 *
 * RECONCILE: the shared data layer (app/src/main/java/com/ga/airdrop/data/) was
 * not present when this feature was written. The interfaces below are the
 * minimal repository surface the shipments ViewModels need; the orchestrator
 * should back them with the real data-layer repos (or typealias/adapters) and
 * swap the [ShipmentsRepoProvider] defaults. Every function documents the
 * endpoint + fields it expects (mirrors AirdropAPI in the Swift app and the RN
 * modules/Shipments API).
 */

// ─── Models (field names mirror the API JSON snake_case keys) ─────────────

data class ShipmentsSummary(
    // RECONCILE: GET /shipments/summary → totalShipments, totalPackages, totalPayments, totalOrders (all optional Ints)
    val totalShipments: Int? = null,
    val totalPackages: Int? = null,
    val totalPayments: Int? = null,
    val totalOrders: Int? = null,
)

data class ShipmentPackage(
    val id: Int,
    val description: String? = null,
    val weight: String? = null,
    val weightLbs: Double? = null,
    val weightKg: String? = null,
    val status: String? = null,        // numeric status code as string, e.g. "7"
    val statusName: String? = null,    // display name, e.g. "Ready for Pickup"
    val shippingMethod: String? = null, // "Standard" | "Express" | "SeaDrop"
    val trackingCode: String? = null,
    val courierNumber: String? = null,
    val additionalCharges: Map<String, Double> = emptyMap(),
    val additionalChargesTotal: Double? = null,
    val exchangeRate: Double? = null,
    /** ISO-ish creation stamp — drives the Newest/Oldest §B.4 sort. */
    val createdAt: String? = null,
)

data class PackageHistoryItem(
    val status: Int? = null,
    val statusName: String? = null,
    val comment: String? = null,
    val changedDate: String? = null,
)

data class PackageInvoiceDoc(
    val id: Int,
    val fileName: String? = null,
    val fullUrl: String? = null,
)

data class ShipmentPackageDetail(
    val id: Int,
    val status: String? = null,
    val statusName: String? = null,
    val shippingMethod: String? = null,
    val trackingCode: String? = null,
    val store: String? = null,
    val shipper: String? = null,
    val courierNumber: String? = null,
    val description: String? = null,
    val weight: String? = null,
    val weightLbs: Double? = null,
    val weightKg: String? = null,
    val numberOfPieces: Int? = null,
    val amount: Double? = null,          // declared value
    val originalPrice: Double? = null,
    val history: List<PackageHistoryItem> = emptyList(),
    val invoices: List<PackageInvoiceDoc> = emptyList(),
    val additionalCharges: Map<String, Double> = emptyMap(),
    val additionalChargesTotal: Double? = null,
    val exchangeRate: Double? = null,
)

data class ShipmentPayment(
    val id: Int,
    val invoiceId: String? = null,       // invoice_id
    val paymentType: String? = null,     // "package" | "product"
    val method: String? = null,
    val totalAmount: Double? = null,     // total_amount
    val trackingCode: String? = null,    // tracking_code — "Drop Number"
    val paymentDate: String? = null,     // payment_date ISO string
    val packageId: Int? = null,          // package_id (package payments)
    val orderId: Int? = null,            // RECONCILE: order id for product payments (Swift passes it alongside the payment)
    val packageDescription: String? = null, // package_description (may contain HTML entities)
    val packageStatusName: String? = null,
    val exchangeRate: Double? = null,
)

data class ShipmentOrder(
    val id: Int,
    val orderNumber: String? = null,
    val title: String? = null,           // order description
    val status: String? = null,
    val orderStatus: String? = null,
    val createdAt: String? = null,
    val productImage: String? = null,
    val customerName: String? = null,
    val weightLbs: Double? = null,
    val invoiceAmountUsd: Double? = null, // invoice_amount_usd
    val paymentMethod: String? = null,
    val invoiceId: String? = null,
    val productName: String? = null,
    val regularPriceUsd: Double? = null,
    val salePriceUsd: Double? = null,
    val purchasedAt: String? = null,
    val productStatus: String? = null,
    val exchangeRate: Double? = null,
)

data class PackageStatusInfo(
    val id: Int,
    val name: String,
    val colorCode: String,
    val order: Int,
)

/** File payload for the multipart invoice upload. */
data class InvoiceUploadFile(
    val fileName: String,
    val mimeType: String, // application/pdf, image/jpeg, image/png, image/gif, image/bmp, image/webp
    val bytes: ByteArray,
)

/** File payload for multipart package damage photos. */
data class DamageReportUploadFile(
    val fileName: String,
    val mimeType: String, // image/png, image/jpeg
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is DamageReportUploadFile && fileName == other.fileName &&
            mimeType == other.mimeType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int =
        31 * (31 * fileName.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()
}

// ─── Repository interfaces (constructor-injected into the ViewModels) ─────

interface ShipmentsHubRepository {
    // RECONCILE: GET /exchange-rate → { usdToJmd: Double } (Swift default 160.625)
    suspend fun exchangeRate(): Result<Double>

    // RECONCILE: GET /shipments/summary → ShipmentsSummary fields
    suspend fun summary(): Result<ShipmentsSummary>

    // RECONCILE: GET /packages/shortlist → [Package]; hub shows first 10
    suspend fun packagesShortlist(): Result<List<ShipmentPackage>>

    // RECONCILE: GET /payments/shortlist → [Payment]; hub shows first 4
    suspend fun paymentsShortlist(): Result<List<ShipmentPayment>>

    // RECONCILE: GET /orders/shortlist → [Order]; hub shows first 6
    suspend fun ordersShortlist(): Result<List<ShipmentOrder>>
}

/**
 * One page of rows plus the server's page verdict when the envelope carried
 * pagination metadata. `isLastPage == null` means the endpoint answered with a
 * bare array (no metadata) and callers should fall back to the batch-size
 * heuristic. Threading this through kills the wasted empty-page probe when the
 * total is an exact multiple of perPage (FuchsiaTower Pass-3 C2).
 */
data class Paged<T>(
    val items: List<T>,
    val isLastPage: Boolean? = null,
)

interface ShipmentsPackagesRepository {
    // RECONCILE: GET /packages?page=&perPage=15&status=&search=&shipping_method= → [Package]
    // (perPage 15; status omitted when 0; search omitted when blank; shipping_method
    // omitted for All — server accepts Standard | Seadrop | Express)
    suspend fun packages(
        page: Int,
        perPage: Int,
        status: Int?,
        search: String?,
        shippingMethod: String?,
    ): Result<Paged<ShipmentPackage>>

    // RECONCILE: GET /packages/{id} → PackageDetail (incl. history[], invoices[], additional_charges{})
    suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail>

    // RECONCILE: GET /package-statuses → [{id, name, colorCode, order}]
    suspend fun packageStatuses(): Result<List<PackageStatusInfo>>

    // RECONCILE: multipart POST /packages/{id}/invoices, form field "invoices[]" (max 3 files, 10MB each)
    suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>): Result<Unit>

    // RECONCILE: DELETE /packages/{packageId}/invoices/{invoiceId}
    suspend fun deleteInvoice(packageId: String, invoiceId: Int): Result<Unit>

    // RECONCILE: multipart POST /packages/{id}/damage-reports, form field "photos[]" (max 5 images, 10MB each)
    suspend fun reportDamage(
        packageId: String,
        description: String,
        photos: List<DamageReportUploadFile>,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Report damage not implemented"))
}

interface ShipmentsPaymentsRepository {
    // RECONCILE: GET /payments?page=&perPage=15&type=&search= → [Payment] (type: package|product, omitted for all; search only when >= 3 chars)
    suspend fun payments(page: Int, perPage: Int, type: String?, search: String?): Result<Paged<ShipmentPayment>>

    // RECONCILE: single payment lookup for the detail routes. Swift passes the tapped Payment
    // object through the navigation push; Android routes carry only the id, so the repo must
    // resolve it (GET /payments/{id} if available, else an in-memory page cache).
    suspend fun payment(paymentId: Int, refresh: Boolean = false): Result<ShipmentPayment>

    // RECONCILE: GET /payments/{id}/invoice → invoice URL. Accept envelopes
    // {data:{url}}, {data:{file_url}}, {data:"..."}, {url}, {file_url}; raw bytes → cache file URL.
    suspend fun paymentInvoiceUrl(paymentId: Int): Result<String>
}

interface ShipmentsOrdersRepository {
    // RECONCILE: GET /orders?page=&perPage=10&search= → [Order]
    suspend fun orders(page: Int, perPage: Int, search: String?): Result<Paged<ShipmentOrder>>

    // RECONCILE: GET /orders/{id} → Order
    suspend fun orderDetails(orderId: Int): Result<ShipmentOrder>

    // RECONCILE: GET /exchange-rate → usdToJmd (shared with hub; duplicated for injection simplicity)
    suspend fun exchangeRate(): Result<Double>
}

// ─── Placeholder implementations until the data layer lands ───────────────

private class PendingDataLayerException :
    IllegalStateException("com.ga.airdrop.data repositories not wired yet — RECONCILE")

/**
 * RECONCILE: swap these for adapters over the real `com.ga.airdrop.data.repo.*`
 * classes once the data agent lands. Screens render loading/empty/error states
 * correctly against these stubs.
 */
object ShipmentsRepoProvider {
    var hub: ShipmentsHubRepository = object : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.failure<Double>(PendingDataLayerException())
        override suspend fun summary() = Result.failure<ShipmentsSummary>(PendingDataLayerException())
        override suspend fun packagesShortlist() = Result.failure<List<ShipmentPackage>>(PendingDataLayerException())
        override suspend fun paymentsShortlist() = Result.failure<List<ShipmentPayment>>(PendingDataLayerException())
        override suspend fun ordersShortlist() = Result.failure<List<ShipmentOrder>>(PendingDataLayerException())
    }
    var packages: ShipmentsPackagesRepository = object : ShipmentsPackagesRepository {
        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ) = Result.failure<Paged<ShipmentPackage>>(PendingDataLayerException())
        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(PendingDataLayerException())
        override suspend fun packageStatuses() =
            Result.failure<List<PackageStatusInfo>>(PendingDataLayerException())
        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(PendingDataLayerException())
        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(PendingDataLayerException())
    }
    var payments: ShipmentsPaymentsRepository = object : ShipmentsPaymentsRepository {
        override suspend fun payments(page: Int, perPage: Int, type: String?, search: String?) =
            Result.failure<Paged<ShipmentPayment>>(PendingDataLayerException())
        override suspend fun payment(paymentId: Int, refresh: Boolean) =
            Result.failure<ShipmentPayment>(PendingDataLayerException())
        override suspend fun paymentInvoiceUrl(paymentId: Int) =
            Result.failure<String>(PendingDataLayerException())
    }
    var orders: ShipmentsOrdersRepository = object : ShipmentsOrdersRepository {
        override suspend fun orders(page: Int, perPage: Int, search: String?) =
            Result.failure<Paged<ShipmentOrder>>(PendingDataLayerException())
        override suspend fun orderDetails(orderId: Int) =
            Result.failure<ShipmentOrder>(PendingDataLayerException())
        override suspend fun exchangeRate() = Result.failure<Double>(PendingDataLayerException())
    }
}

/*
 * Package → shared-cart mapping — Swift FigmaCartStore.shared.toggle(package:)
 * (FigmaShipmentsViewController.swift:1152-1172) and
 * FigmaPackageDetailsViewController.swift:1186-1224: package cart actions go
 * through the ONE persisted cart (feature.cart.CartStore) so MyCart renders
 * the line items and the header badge never desyncs. The previous
 * ShipmentsCartStore was an orphan id-set whose adds silently vanished.
 */

fun ShipmentPackage.toCartLine(): com.ga.airdrop.feature.cart.CartStore.CartLine =
    com.ga.airdrop.feature.cart.CartStore.CartLine(
        id = id,
        packageId = id,
        title = description?.replaceFirstChar { it.uppercase(java.util.Locale.US) }
            ?: trackingCode ?: "Package #$id",
        qty = 1,
        priceUsd = additionalChargesTotal ?: additionalCharges.values.sum(),
        kind = com.ga.airdrop.feature.cart.CartStore.CartLineKind.PACKAGE,
        weightKg = resolvedCartWeightKg(weightKg, weightLbs),
        shippingMethod = shippingMethod,
        status = statusName,
        statusCode = status?.toIntOrNull(),
        isAuction = false,
    )

fun ShipmentPackageDetail.toCartLine(): com.ga.airdrop.feature.cart.CartStore.CartLine =
    com.ga.airdrop.feature.cart.CartStore.CartLine(
        id = id,
        packageId = id,
        title = description?.replaceFirstChar { it.uppercase(java.util.Locale.US) }
            ?: trackingCode ?: "Package #$id",
        qty = 1,
        priceUsd = additionalChargesTotal ?: additionalCharges.values.sum(),
        kind = com.ga.airdrop.feature.cart.CartStore.CartLineKind.PACKAGE,
        weightKg = resolvedCartWeightKg(weightKg, weightLbs),
        shippingMethod = shippingMethod,
        status = statusName,
        statusCode = status?.toIntOrNull(),
        isAuction = false,
    )

private fun resolvedCartWeightKg(
    weightKg: String?,
    weightLbs: Double?,
): Double? {
    fun parse(raw: String?): Double? = raw
        ?.trim()
        ?.replace(Regex("[^0-9.+-]"), "")
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }

    return parse(weightKg)
        ?: weightLbs?.takeIf { it > 0.0 }?.times(0.45359237)
}
