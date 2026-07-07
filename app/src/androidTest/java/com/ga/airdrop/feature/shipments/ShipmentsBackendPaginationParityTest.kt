package com.ga.airdrop.feature.shipments

import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Runtime rails that Figma cannot encode: Swift remains the source of truth for
 * request sizes, reset behavior, search gating, and rendered shortlist limits.
 */
class ShipmentsBackendPaginationParityTest {

    @Test
    fun hubKeepsSwiftShortlistRenderLimitsAfterBackendFetch() = runBlocking {
        val repo = RecordingHubRepository(
            packages = (1..12).map { samplePackage(it) },
            payments = (1..6).map { samplePayment(it) },
            orders = (1..10).map { sampleOrder(it) },
        )
        val viewModel = ShipmentsViewModel(repo)

        waitUntil { !viewModel.state.value.loading && repo.ordersCalls == 1 }

        val state = viewModel.state.value
        assertEquals(
            "Swift renders packagesData.prefix(10) on the Shipments hub",
            10,
            state.packages.size,
        )
        assertEquals(
            "Swift renders paymentsData.prefix(4) on the Shipments hub",
            4,
            state.payments.size,
        )
        assertEquals(
            "Swift renders ordersData.prefix(6) on the Shipments hub",
            6,
            state.orders.size,
        )
        assertEquals(160.625, state.exchangeRate, 0.0)
        assertEquals(1, repo.summaryCalls)
        assertEquals(1, repo.packagesCalls)
        assertEquals(1, repo.paymentsCalls)
        assertEquals(1, repo.ordersCalls)
    }

    @Test
    fun packagesListKeepsSwiftSubmitPaginationAndStatusContract() = runBlocking {
        val repo = RecordingPackagesRepository()
        val viewModel = PackagesViewModel(repo = repo, hubRepo = RecordingHubRepository())

        waitUntil("packages initial load", { packageSnapshot(repo, viewModel) }) {
            repo.calls.isNotEmpty() && !viewModel.state.value.loading
        }
        assertEquals(PackageCall(page = 1, perPage = 15, status = null, search = null), repo.calls.first())

        val callsBeforeTyping = repo.calls.size
        viewModel.onSearchTextChange(" ARD000 ")
        assertEquals(
            "Swift filters cached packages while typing and only re-fetches on Return",
            callsBeforeTyping,
            repo.calls.size,
        )

        viewModel.onSearchSubmit()
        waitUntil("packages submitted search", { packageSnapshot(repo, viewModel) }) {
            repo.calls.any { it.page == 1 && it.search == "ARD000" } &&
                !viewModel.state.value.loading
        }

        viewModel.loadNextPage()
        waitUntil("packages page 2", { packageSnapshot(repo, viewModel) }) {
            repo.calls.any { it.page == 2 && it.search == "ARD000" } &&
                !viewModel.state.value.loadingMore
        }

        viewModel.selectStatus(7)
        waitUntil("packages status filter", { packageSnapshot(repo, viewModel) }) {
            repo.calls.any { it.page == 1 && it.status == 7 && it.search == "ARD000" } &&
                !viewModel.state.value.loading
        }
    }

    @Test
    fun paymentsListKeepsSwiftDefaultTypeDebouncedSearchAndPagination() = runBlocking {
        val repo = RecordingPaymentsRepository()
        val viewModel = PaymentsViewModel(repo)

        waitUntil { repo.calls.isNotEmpty() && !viewModel.state.value.loading }
        assertEquals(PaymentCall(page = 1, perPage = 15, type = "package", search = null), repo.calls.first())

        viewModel.loadNextPage()
        waitUntil { repo.calls.any { it.page == 2 && it.type == "package" } }

        viewModel.onSearchTextChange("ab")
        waitUntil { repo.calls.last().page == 1 && repo.calls.last().search == null && repo.calls.size >= 3 }

        viewModel.onSearchTextChange("abc")
        waitUntil { repo.calls.last().page == 1 && repo.calls.last().search == "abc" }

        viewModel.selectTypeFilter(PaymentTypeFilter.All)
        waitUntil { repo.calls.last().type == null && repo.calls.last().search == "abc" }
    }

    @Test
    fun ordersListKeepsSwiftDebouncedSearchPaginationAndRefresh() = runBlocking {
        val repo = RecordingOrdersRepository()
        val viewModel = OrdersViewModel(repo)

        waitUntil("orders initial load", { repo.calls.toString() }) {
            repo.calls.isNotEmpty() && !viewModel.state.value.loading
        }
        assertEquals(OrderCall(page = 1, perPage = 10, search = null), repo.calls.first())

        viewModel.loadNextPage()
        waitUntil("orders page 2", { repo.calls.toString() }) {
            repo.calls.any { it.page == 2 && it.perPage == 10 } &&
                !viewModel.state.value.loadingMore
        }

        viewModel.refresh()
        waitUntil("orders refresh", { repo.calls.toString() }) {
            repo.calls.count { it.page == 1 && it.search == null } >= 2
        }

        viewModel.onSearchTextChange("xy")
        waitUntil("orders short search", { repo.calls.toString() }) {
            repo.calls.size >= 4 && repo.calls.last().search == null
        }

        viewModel.onSearchTextChange("xyz")
        waitUntil("orders debounced search", { repo.calls.toString() }) {
            repo.calls.last().page == 1 && repo.calls.last().search == "xyz"
        }
    }

    private suspend fun waitUntil(
        label: String = "condition",
        snapshot: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(10_000) {
                while (!condition()) delay(25)
            }
        } catch (e: TimeoutCancellationException) {
            fail("$label timed out; ${snapshot()}")
        }
    }

    private fun packageSnapshot(
        repo: RecordingPackagesRepository,
        viewModel: PackagesViewModel,
    ): String {
        val state = viewModel.state.value
        return "calls=${repo.calls}; loading=${state.loading}; loadingMore=${state.loadingMore}; " +
            "hasMore=${state.hasMorePages}; search='${state.searchText}'; status=${state.statusFilter}; " +
            "items=${state.items.size}; error=${state.error}"
    }

    private data class PackageCall(
        val page: Int,
        val perPage: Int,
        val status: Int?,
        val search: String?,
    )

    private class RecordingPackagesRepository : ShipmentsPackagesRepository {
        private val recordedCalls = Collections.synchronizedList(mutableListOf<PackageCall>())

        val calls: List<PackageCall>
            get() = synchronized(recordedCalls) { recordedCalls.toList() }

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
        ): Result<Paged<ShipmentPackage>> {
            recordedCalls += PackageCall(page, perPage, status, search)
            val count = if (page == 1) perPage else 2
            // Metadata-less page (isLastPage = null) so the batch-size
            // heuristic these rails assert stays exercised.
            return Result.success(Paged((1..count).map { samplePackage(page * 100 + it) }))
        }

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException())

        override suspend fun packageStatuses() = Result.success(ShipmentStatusCatalog.defaults)

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(UnsupportedOperationException())

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(UnsupportedOperationException())
    }

    private data class PaymentCall(
        val page: Int,
        val perPage: Int,
        val type: String?,
        val search: String?,
    )

    private class RecordingPaymentsRepository : ShipmentsPaymentsRepository {
        private val recordedCalls = Collections.synchronizedList(mutableListOf<PaymentCall>())

        val calls: List<PaymentCall>
            get() = synchronized(recordedCalls) { recordedCalls.toList() }

        override suspend fun payments(
            page: Int,
            perPage: Int,
            type: String?,
            search: String?,
        ): Result<Paged<ShipmentPayment>> {
            recordedCalls += PaymentCall(page, perPage, type, search)
            val count = if (page == 1) perPage else 2
            return Result.success(Paged((1..count).map { samplePayment(page * 100 + it, type ?: "package") }))
        }

        override suspend fun payment(paymentId: Int) = Result.success(samplePayment(paymentId))

        override suspend fun paymentInvoiceUrl(paymentId: Int) = Result.success("https://example.test/invoice.pdf")
    }

    private data class OrderCall(
        val page: Int,
        val perPage: Int,
        val search: String?,
    )

    private class RecordingOrdersRepository : ShipmentsOrdersRepository {
        private val recordedCalls = Collections.synchronizedList(mutableListOf<OrderCall>())

        val calls: List<OrderCall>
            get() = synchronized(recordedCalls) { recordedCalls.toList() }

        override suspend fun orders(page: Int, perPage: Int, search: String?): Result<Paged<ShipmentOrder>> {
            recordedCalls += OrderCall(page, perPage, search)
            val count = if (page == 1) perPage else 2
            return Result.success(Paged((1..count).map { sampleOrder(page * 100 + it) }))
        }

        override suspend fun orderDetails(orderId: Int) = Result.success(sampleOrder(orderId))

        override suspend fun exchangeRate() = Result.success(160.625)
    }

    private class RecordingHubRepository(
        private val packages: List<ShipmentPackage> = emptyList(),
        private val payments: List<ShipmentPayment> = emptyList(),
        private val orders: List<ShipmentOrder> = emptyList(),
    ) : ShipmentsHubRepository {
        var summaryCalls = 0
            private set
        var packagesCalls = 0
            private set
        var paymentsCalls = 0
            private set
        var ordersCalls = 0
            private set

        override suspend fun exchangeRate() = Result.success(160.625)

        override suspend fun summary(): Result<ShipmentsSummary> {
            summaryCalls += 1
            return Result.success(
                ShipmentsSummary(
                    totalShipments = 7,
                    totalPackages = 34,
                    totalPayments = 234,
                    totalOrders = 3,
                )
            )
        }

        override suspend fun packagesShortlist(): Result<List<ShipmentPackage>> {
            packagesCalls += 1
            return Result.success(packages)
        }

        override suspend fun paymentsShortlist(): Result<List<ShipmentPayment>> {
            paymentsCalls += 1
            return Result.success(payments)
        }

        override suspend fun ordersShortlist(): Result<List<ShipmentOrder>> {
            ordersCalls += 1
            return Result.success(orders)
        }
    }

    private companion object {
        fun samplePackage(id: Int) = ShipmentPackage(
            id = id,
            description = "Swift package $id",
            weightLbs = 1.3,
            statusName = "Ready for Pick-Up",
            shippingMethod = "Standard",
            additionalChargesTotal = 50.0,
        )

        fun samplePayment(id: Int, type: String = "package") = ShipmentPayment(
            id = id,
            invoiceId = "INV-$id",
            paymentType = type,
            totalAmount = 50.0,
            trackingCode = "ARD$id",
            paymentDate = "2024-01-12T15:14:00Z",
            packageId = id,
            packageDescription = "Swift payment $id",
        )

        fun sampleOrder(id: Int) = ShipmentOrder(
            id = id,
            orderNumber = "ORD-$id",
            title = "Swift order $id",
            status = "pending",
            orderStatus = "order placed",
            invoiceAmountUsd = 403.35,
        )
    }
}
