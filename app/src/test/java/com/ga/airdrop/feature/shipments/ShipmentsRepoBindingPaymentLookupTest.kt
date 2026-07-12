package com.ga.airdrop.feature.shipments

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Pagination
import com.ga.airdrop.data.model.Payment
import com.ga.airdrop.data.model.PaymentPackage
import com.ga.airdrop.data.repo.PaymentsRepository
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentsRepoBindingPaymentLookupTest {

    @Before
    fun resetPaymentCache() {
        clearShipmentsSessionCaches()
    }

    @Test
    fun paymentLookupContinuesPastTheFormerFivePageCutoff() = runBlocking {
        val targetPaymentId = 987_654_321
        val capture = CapturedPaymentCalls()
        val repository = DataShipmentsPaymentsRepository(
            PaymentsRepository(
                paymentsService(
                    capture = capture,
                    pages = { page, perPage -> paymentsPage(page, perPage, targetPaymentId) },
                ),
            ),
            Files.createTempDirectory("airdrop-payment-cache").toFile().apply { deleteOnExit() },
        )

        val payment = repository.payment(targetPaymentId).getOrThrow()

        assertEquals(targetPaymentId, payment.id)
        assertEquals("INV-$targetPaymentId", payment.invoiceId)
        assertEquals((1..6).toList(), capture.pages)
        assertTrue(capture.perPages.all { it == 15 })
        assertTrue(capture.types.all { it == null })
        assertTrue(capture.searches.all { it == null })
    }

    @Test
    fun paymentLookupRefreshIgnoresStaleCacheAndRemembersFreshRow() = runBlocking {
        val targetPaymentId = 4321
        val staleRepository = DataShipmentsPaymentsRepository(
            PaymentsRepository(
                paymentsService(
                    capture = CapturedPaymentCalls(),
                    pages = { page, _ ->
                        if (page == 1) listOf(payment(targetPaymentId, "INV-STALE")) else emptyList()
                    },
                ),
            ),
            Files.createTempDirectory("airdrop-payment-cache-stale").toFile().apply { deleteOnExit() },
        )
        val freshCapture = CapturedPaymentCalls()
        val freshRepository = DataShipmentsPaymentsRepository(
            PaymentsRepository(
                paymentsService(
                    capture = freshCapture,
                    pages = { page, _ ->
                        if (page == 1) listOf(payment(targetPaymentId, "INV-FRESH")) else emptyList()
                    },
                ),
            ),
            Files.createTempDirectory("airdrop-payment-cache-fresh").toFile().apply { deleteOnExit() },
        )

        staleRepository.payments(page = 1, perPage = 15, type = null, search = null).getOrThrow()

        assertEquals("INV-STALE", freshRepository.payment(targetPaymentId).getOrThrow().invoiceId)
        assertEquals("INV-FRESH", freshRepository.payment(targetPaymentId, refresh = true).getOrThrow().invoiceId)
        assertEquals(listOf(1), freshCapture.pages)
        assertEquals("INV-FRESH", freshRepository.payment(targetPaymentId).getOrThrow().invoiceId)
    }

    @Test
    fun clearSessionCachesEvictsPreviouslyRememberedPayment() = runBlocking {
        val targetPaymentId = 5432
        val seedRepository = repository(
            capture = CapturedPaymentCalls(),
            pages = { page, _ ->
                if (page == 1) listOf(payment(targetPaymentId, "INV-CACHED")) else emptyList()
            },
            directoryName = "airdrop-payment-cache-eviction-seed",
        )
        seedRepository.payments(page = 1, perPage = 15, type = null, search = null).getOrThrow()
        assertEquals("INV-CACHED", seedRepository.payment(targetPaymentId).getOrThrow().invoiceId)

        clearShipmentsSessionCaches()

        val postClearCapture = CapturedPaymentCalls()
        val postClearRepository = repository(
            capture = postClearCapture,
            pages = { _, _ -> emptyList() },
            directoryName = "airdrop-payment-cache-eviction-check",
        )
        assertTrue(postClearRepository.payment(targetPaymentId).isFailure)
        assertEquals(listOf(1), postClearCapture.pages)
    }

    @Test
    fun refreshFallsBackToCachedPaymentWhenFreshRowMovesBeyondScanCap() = runBlocking {
        val targetPaymentId = 6543
        val seedRepository = repository(
            capture = CapturedPaymentCalls(),
            pages = { page, _ ->
                if (page == 1) listOf(payment(targetPaymentId, "INV-CACHED")) else emptyList()
            },
            directoryName = "airdrop-payment-cache-page21-seed",
        )
        seedRepository.payments(page = 1, perPage = 15, type = null, search = null).getOrThrow()

        val refreshCapture = CapturedPaymentCalls()
        val refreshRepository = repository(
            capture = refreshCapture,
            pages = { page, perPage ->
                if (page <= 20) {
                    List(perPage) { index -> payment(page * 10_000 + index, "INV-$page-$index") }
                } else {
                    listOf(payment(targetPaymentId, "INV-PAGE-21"))
                }
            },
            directoryName = "airdrop-payment-cache-page21-refresh",
        )

        val refreshed = refreshRepository.payment(targetPaymentId, refresh = true).getOrThrow()

        assertEquals("INV-CACHED", refreshed.invoiceId)
        assertEquals((1..20).toList(), refreshCapture.pages)
    }

    @Test
    fun paymentLookupStopsOnPaginationLastPageEvenWhenPageIsFull() = runBlocking {
        val capture = CapturedPaymentCalls()
        val repository = DataShipmentsPaymentsRepository(
            PaymentsRepository(
                paymentsService(
                    capture = capture,
                    pages = { _, perPage -> List(perPage) { index -> payment(2000 + index, "INV-$index") } },
                    pagination = { page -> Pagination(currentPage = page, perPage = 15, total = 15, lastPage = 1) },
                ),
            ),
            Files.createTempDirectory("airdrop-payment-cache-last-page").toFile().apply { deleteOnExit() },
        )

        assertTrue(repository.payment(1111, refresh = true).isFailure)
        assertEquals(listOf(1), capture.pages)
    }

    @Test
    fun paymentLookupCapsMetadataLessFullPageScan() = runBlocking {
        val capture = CapturedPaymentCalls()
        val repository = DataShipmentsPaymentsRepository(
            PaymentsRepository(
                paymentsService(
                    capture = capture,
                    pages = { page, perPage ->
                        List(perPage) { index -> payment(page * 10_000 + index, "INV-$page-$index") }
                    },
                ),
            ),
            Files.createTempDirectory("airdrop-payment-cache-cap").toFile().apply { deleteOnExit() },
        )

        assertTrue(repository.payment(2222, refresh = true).isFailure)
        assertEquals((1..20).toList(), capture.pages)
    }

    private class CapturedPaymentCalls {
        val pages = mutableListOf<Int>()
        val perPages = mutableListOf<Int>()
        val types = mutableListOf<String?>()
        val searches = mutableListOf<String?>()
    }

    private fun repository(
        capture: CapturedPaymentCalls,
        pages: (page: Int, perPage: Int) -> List<Payment>,
        directoryName: String,
    ) = DataShipmentsPaymentsRepository(
        PaymentsRepository(paymentsService(capture = capture, pages = pages)),
        Files.createTempDirectory(directoryName).toFile().apply { deleteOnExit() },
    )

    @Suppress("UNCHECKED_CAST")
    private fun paymentsService(
        capture: CapturedPaymentCalls,
        pages: (page: Int, perPage: Int) -> List<Payment>,
        pagination: (page: Int) -> Pagination? = { null },
    ): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "payments" -> {
                    val page = args?.getOrNull(0) as Int
                    val perPage = args.getOrNull(1) as Int
                    capture.pages += page
                    capture.perPages += perPage
                    capture.types += args.getOrNull(4) as? String
                    capture.searches += args.getOrNull(5) as? String
                    Paginated(pages(page, perPage), pagination(page))
                }
                else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService

    private fun paymentsPage(page: Int, perPage: Int, targetPaymentId: Int): List<Payment> =
        when (page) {
            in 1..5 -> List(perPage) { index ->
                payment(id = page * 10_000 + index, invoiceId = "INV-$page-$index")
            }
            6 -> listOf(payment(id = targetPaymentId, invoiceId = "INV-$targetPaymentId"))
            else -> emptyList()
        }

    private fun payment(id: Int, invoiceId: String): Payment =
        Payment(
            id = id,
            invoiceId = invoiceId,
            paymentType = "package",
            method = "card",
            totalAmount = 42.50,
            trackingCode = "TRK-$id",
            paymentDate = "2026-07-07T00:00:00Z",
            packageId = id + 1,
            paymentPackage = PaymentPackage(description = "Package $id", statusName = "Paid"),
        )
}
