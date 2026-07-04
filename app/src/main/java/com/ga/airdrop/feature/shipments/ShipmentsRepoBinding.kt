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
 * Rows seen by any list/detail load, kept so cart toggles (which only carry
 * an id) can build a full CartLine — Swift passes the object itself.
 */
internal object ShipmentPackageRegistry {
    private val byId = LinkedHashMap<Int, ShipmentPackage>()

    @Synchronized
    fun remember(pkg: ShipmentPackage) {
        if (pkg.id != 0) byId[pkg.id] = pkg
        while (byId.size > 300) byId.remove(byId.keys.first())
    }

    @Synchronized
    fun get(id: Int): ShipmentPackage? = byId[id]
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
).also(ShipmentPackageRegistry::remember)

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
    ): Result<List<ShipmentPackage>> =
        repo.packages(page = page, perPage = perPage, status = status, search = search)
            .map { list -> list.map { it.toShipment() } }

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
}

/**
 * The API has no GET /payments/{id} (Swift passes the tapped Payment object
 * through navigation) — detail routes resolve from this page cache, refilled
 * by scanning recent pages when a deep link arrives cold.
 */
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
}

private class DataShipmentsPaymentsRepository(
    private val repo: PaymentsRepository,
    private val cacheDir: File,
) : ShipmentsPaymentsRepository {

    override suspend fun payments(
        page: Int,
        perPage: Int,
        type: String?,
        search: String?,
    ): Result<List<ShipmentPayment>> =
        repo.payments(page = page, perPage = perPage, type = type, search = search)
            .map { list -> list.map { it.toShipment().also(PaymentPageCache::remember) } }

    override suspend fun payment(paymentId: Int): Result<ShipmentPayment> {
        PaymentPageCache.get(paymentId)?.let { return Result.success(it) }
        // Cold deep link: scan the first pages for the row.
        for (page in 1..5) {
            val result = repo.payments(page = page, perPage = 15, type = null, search = null)
            val rows = result.getOrNull() ?: return Result.failure(
                result.exceptionOrNull() ?: IllegalStateException("Payment not found"),
            )
            rows.forEach { PaymentPageCache.remember(it.toShipment()) }
            PaymentPageCache.get(paymentId)?.let { return Result.success(it) }
            if (rows.isEmpty()) break
        }
        return Result.failure(IllegalStateException("Payment #$paymentId not found"))
    }

    override suspend fun paymentInvoiceUrl(paymentId: Int): Result<String> =
        repo.paymentInvoice(paymentId, cacheDir).map { location ->
            when (location) {
                is InvoiceLocation.Remote -> location.url
                is InvoiceLocation.Local -> location.file.toURI().toString()
            }
        }
}

private class DataShipmentsOrdersRepository(
    private val repo: OrdersRepository,
    private val misc: MiscRepository,
) : ShipmentsOrdersRepository {

    override suspend fun orders(page: Int, perPage: Int, search: String?): Result<List<ShipmentOrder>> =
        repo.orders(page = page, perPage = perPage, search = search)
            .map { list -> list.map { it.toShipment() } }

    override suspend fun orderDetails(orderId: Int): Result<ShipmentOrder> =
        repo.orderDetail(orderId).map { it.toShipment() }

    override suspend fun exchangeRate(): Result<Double> =
        misc.exchangeRate().mapCatching { it.usdToJmd ?: error("Missing exchange rate") }
}
