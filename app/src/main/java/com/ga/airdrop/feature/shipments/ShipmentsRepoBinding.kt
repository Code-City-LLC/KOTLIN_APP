package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.Order
import com.ga.airdrop.data.model.Package
import com.ga.airdrop.data.model.PackageDetail
import com.ga.airdrop.data.model.Payment
import com.ga.airdrop.data.repo.InvoiceLocation
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.OrdersRepository
import com.ga.airdrop.data.repo.PackagesRepository
import com.ga.airdrop.data.repo.PaymentsRepository
import com.ga.airdrop.data.api.UploadFile
import java.io.File

/**
 * Binds the shipments feature's repo contracts to the shared data layer
 * (single reconcile point per ShipmentsContracts.kt).
 */
object ShipmentsRepoBinding {

    fun install(cacheDir: File) {
        val packagesRepo = PackagesRepository(ApiClient.service)
        val paymentsRepo = PaymentsRepository(ApiClient.service)
        val ordersRepo = OrdersRepository(ApiClient.service)
        val miscRepo = MiscRepository(ApiClient.service)

        ShipmentsRepoProvider.hub = DataShipmentsHubRepository(packagesRepo, paymentsRepo, ordersRepo, miscRepo)
        ShipmentsRepoProvider.packages = DataShipmentsPackagesRepository(packagesRepo)
        ShipmentsRepoProvider.payments = DataShipmentsPaymentsRepository(paymentsRepo, cacheDir)
        ShipmentsRepoProvider.orders = DataShipmentsOrdersRepository(ordersRepo, miscRepo)
    }
}

// ─── Model mapping ─────────────────────────────────────────────────────────

/**
 * Server page verdict from the decoded envelope metadata; null when the
 * endpoint answered a bare array (callers fall back to the size heuristic).
 */
private fun com.ga.airdrop.data.model.Paginated<*>.isLastPage(): Boolean? {
    val current = pagination?.currentPage ?: return null
    val last = pagination?.lastPage ?: return null
    return current >= last
}

private fun Package.toShipment() = ShipmentPackage(
    id = id ?: 0,
    description = description,
    weight = weight,
    weightLbs = weightLbs,
    weightKg = weightKg,
    status = status,
    statusName = statusName,
    shippingMethod = shippingMethod,
    trackingCode = trackingCode,
    courierNumber = courierNumber,
    additionalCharges = additionalCharges,
    additionalChargesTotal = additionalChargesTotal,
    exchangeRate = exchangeRate,
    createdAt = createdAt,
)

private fun PackageDetail.toShipmentDetail() = ShipmentPackageDetail(
    id = id,
    status = status,
    statusName = statusName,
    shippingMethod = shippingMethod,
    trackingCode = trackingCode,
    store = store,
    shipper = shipper,
    courierNumber = courierNumber,
    description = description,
    weight = weight,
    weightLbs = weightLbs,
    weightKg = weightKg,
    numberOfPieces = numberOfPieces,
    amount = amount,
    originalPrice = originalPrice,
    history = history.map {
        PackageHistoryItem(
            status = it.status,
            statusName = it.statusName,
            comment = it.comment,
            changedDate = it.changedDate,
        )
    },
    invoices = invoices.map {
        PackageInvoiceDoc(id = it.id ?: 0, fileName = it.fileName, fullUrl = it.fullUrl)
    },
    additionalCharges = additionalCharges,
    additionalChargesTotal = additionalChargesTotal,
    exchangeRate = exchangeRate,
)

private fun Payment.toShipment() = ShipmentPayment(
    id = id ?: 0,
    invoiceId = invoiceId,
    paymentType = paymentType,
    method = method,
    totalAmount = totalAmount,
    trackingCode = trackingCode,
    paymentDate = paymentDate,
    packageId = packageId,
    orderId = orderId,
    packageDescription = packageDescription,
    packageStatusName = packageStatusName,
    exchangeRate = exchangeRate,
)

private fun Order.toShipment() = ShipmentOrder(
    id = id ?: 0,
    orderNumber = orderNumber,
    title = title,
    status = status,
    orderStatus = orderStatus,
    createdAt = createdAt,
    productImage = productImage,
    customerName = customerName,
    weightLbs = weightLbs,
    invoiceAmountUsd = invoiceAmountUsd,
    paymentMethod = paymentMethod,
    invoiceId = invoiceId,
    productName = productName,
    regularPriceUsd = regularPriceUsd,
    salePriceUsd = salePriceUsd,
    purchasedAt = purchasedAt,
    productStatus = productStatus,
    exchangeRate = exchangeRate,
)

// ─── Adapters ──────────────────────────────────────────────────────────────

private class DataShipmentsHubRepository(
    private val packages: PackagesRepository,
    private val payments: PaymentsRepository,
    private val orders: OrdersRepository,
    private val misc: MiscRepository,
) : ShipmentsHubRepository {

    override suspend fun exchangeRate(): Result<Double> =
        misc.exchangeRate().mapCatching { it.usdToJmd ?: error("Missing exchange rate") }

    override suspend fun summary(): Result<ShipmentsSummary> =
        packages.shipmentsSummary().map {
            ShipmentsSummary(
                totalShipments = it.totalShipments,
                totalPackages = it.totalPackages,
                totalPayments = it.totalPayments,
                totalOrders = it.totalOrders,
            )
        }

    override suspend fun packagesShortlist(): Result<List<ShipmentPackage>> =
        packages.packagesShortlist().map { list -> list.map { it.toShipment() } }

    override suspend fun paymentsShortlist(): Result<List<ShipmentPayment>> =
        payments.paymentsShortlist().map { list ->
            list.map { it.toShipment().also(PaymentPageCache::remember) }
        }

    override suspend fun ordersShortlist(): Result<List<ShipmentOrder>> =
        orders.ordersShortlist().map { list -> list.map { it.toShipment() } }
}

private class DataShipmentsPackagesRepository(
    private val repo: PackagesRepository,
) : ShipmentsPackagesRepository {

    override suspend fun packages(
        page: Int,
        perPage: Int,
        status: Int?,
        search: String?,
    ): Result<Paged<ShipmentPackage>> =
        repo.packages(page = page, perPage = perPage, status = status, search = search)
            .map { page -> Paged(page.items.map { it.toShipment() }, page.isLastPage()) }

    override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> =
        repo.packageDetails(packageId).map { it.toShipmentDetail() }

    override suspend fun packageStatuses(): Result<List<PackageStatusInfo>> =
        repo.packageStatuses().map { list ->
            list.map { PackageStatusInfo(id = it.id, name = it.name, colorCode = it.colorCode, order = it.order) }
        }

    override suspend fun uploadInvoices(
        packageId: String,
        files: List<InvoiceUploadFile>,
    ): Result<Unit> =
        repo.uploadPackageInvoices(
            packageId,
            files.map { UploadFile(fileName = it.fileName, mimeType = it.mimeType, bytes = it.bytes) },
        ).map { }

    override suspend fun deleteInvoice(packageId: String, invoiceId: Int): Result<Unit> =
        repo.deletePackageInvoice(packageId, invoiceId).map { }

    override suspend fun reportDamage(
        packageId: String,
        description: String,
        photos: List<DamageReportUploadFile>,
    ): Result<Unit> =
        repo.reportPackageDamage(
            packageId,
            description,
            photos.map { UploadFile(fileName = it.fileName, mimeType = it.mimeType, bytes = it.bytes) },
        ).map { }
}

/**
 * The API has no GET /payments/{id} (Swift passes the tapped Payment object
 * through navigation) — detail routes resolve from this page cache, refilled
 * by scanning recent pages when a deep link arrives cold.
 */
private const val PAYMENT_LOOKUP_PER_PAGE = 15
private const val MAX_PAYMENT_LOOKUP_PAGES = 20

private object PaymentPageCache {
    private val byId = LinkedHashMap<Int, ShipmentPayment>()

    @Synchronized
    fun remember(payment: ShipmentPayment) {
        if (payment.id != 0) byId[payment.id] = payment
        // Bound the cache: keep the most recent ~200 rows.
        while (byId.size > 200) byId.remove(byId.keys.first())
    }

    @Synchronized
    fun get(id: Int): ShipmentPayment? = byId[id]

    @Synchronized
    fun clear() = byId.clear()
}

/**
 * Logout hygiene — drops the process-global shipment caches so a following
 * account's session can't see the prior user's payment rows (FuchsiaTower
 * Pass-3b C3).
 */
internal fun clearShipmentsSessionCaches() {
    PaymentPageCache.clear()
}

internal class DataShipmentsPaymentsRepository(
    private val repo: PaymentsRepository,
    private val cacheDir: File,
) : ShipmentsPaymentsRepository {

    override suspend fun payments(
        page: Int,
        perPage: Int,
        type: String?,
        search: String?,
    ): Result<Paged<ShipmentPayment>> =
        repo.payments(page = page, perPage = perPage, type = type, search = search)
            .map { page ->
                Paged(page.items.map { it.toShipment().also(PaymentPageCache::remember) }, page.isLastPage())
            }

    override suspend fun payment(paymentId: Int, refresh: Boolean): Result<ShipmentPayment> {
        if (!refresh) PaymentPageCache.get(paymentId)?.let { return Result.success(it) }
        // Cold detail entry: keep scanning until the backend reports the end
        // instead of cutting off at five pages; still cap metadata-less scans
        // so a broken backend cannot spin through unbounded full pages.
        var page = 1
        while (page <= MAX_PAYMENT_LOOKUP_PAGES) {
            val result = repo.payments(page = page, perPage = PAYMENT_LOOKUP_PER_PAGE, type = null, search = null)
            val envelope = result.getOrNull() ?: return Result.failure(
                result.exceptionOrNull() ?: IllegalStateException("Payment not found"),
            )
            val rows = envelope.items
            if (rows.isEmpty()) break
            rows.forEach { PaymentPageCache.remember(it.toShipment()) }
            PaymentPageCache.get(paymentId)?.let { return Result.success(it) }
            if (envelope.isLastPage() == true) break
            if (rows.size < PAYMENT_LOOKUP_PER_PAGE) break
            page += 1
        }
        return Result.failure(IllegalStateException("Payment #$paymentId not found"))
    }

    override suspend fun paymentInvoiceUrl(paymentId: Int): Result<String> =
        repo.paymentInvoice(paymentId, cacheDir).map { location ->
            when (location) {
                is InvoiceLocation.Remote -> location.url
                // Uri.fromFile → "file:///..." (java.io.File.toURI() emits a
                // single-slash "file:/..." the viewer's file:// gate rejects).
                is InvoiceLocation.Local -> android.net.Uri.fromFile(location.file).toString()
            }
        }
}

private class DataShipmentsOrdersRepository(
    private val repo: OrdersRepository,
    private val misc: MiscRepository,
) : ShipmentsOrdersRepository {

    override suspend fun orders(page: Int, perPage: Int, search: String?): Result<Paged<ShipmentOrder>> =
        repo.orders(page = page, perPage = perPage, search = search)
            .map { page -> Paged(page.items.map { it.toShipment() }, page.isLastPage()) }

    override suspend fun orderDetails(orderId: Int): Result<ShipmentOrder> =
        repo.orderDetail(orderId).map { it.toShipment() }

    override suspend fun exchangeRate(): Result<Double> =
        misc.exchangeRate().mapCatching { it.usdToJmd ?: error("Missing exchange rate") }
}
