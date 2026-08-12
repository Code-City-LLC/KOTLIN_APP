package com.ga.airdrop.feature.shipments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `GET /payments/{invoiceId}/invoice` takes the INVOICE id — never the
 * payment id.
 *
 * ⚠️ THE DOWNLOAD BUTTON 404'D FOR VIRTUALLY EVERY PAYMENT.
 *
 * Laravel resolves this route with `WHERE p.packages_invoice_id = ?`
 * (InvoiceService.php:111 — verified in the Laravel source, not inferred).
 * `payment.id` is a different sequence, so sending it asked for an invoice
 * that essentially never exists and the customer read "Invoice not found"
 * on a payment they were looking at.
 *
 * Found on iOS by @Claude-SwiftHawk (fixed in Swift f052e9c); confirmed live
 * in Kotlin during the cross-lane API verification Kemar ordered — same
 * endpoint, same wrong id. The tell was one function away: the viewer title
 * already used `payment.invoiceId`, so the code always knew the right id and
 * only the request used the wrong one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsInvoiceIdContractTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo : ShipmentsPaymentsRepository {
        val requestedIds = mutableListOf<Int>()
        override suspend fun payments(
            page: Int,
            perPage: Int,
            type: String?,
            search: String?,
        ): Result<Paged<ShipmentPayment>> = Result.success(Paged(emptyList(), isLastPage = true))

        override suspend fun payment(paymentId: Int, refresh: Boolean): Result<ShipmentPayment> =
            Result.failure(IllegalStateException("unused in this test"))

        override suspend fun paymentInvoiceUrl(invoiceId: Int): Result<String> {
            requestedIds += invoiceId
            return Result.success("https://example.test/invoice/$invoiceId.pdf")
        }
    }

    private fun payment(id: Int, invoiceId: String?) = ShipmentPayment(
        id = id,
        invoiceId = invoiceId,
        paymentType = "package",
        totalAmount = 100.0,
    )

    @Test
    fun `the request carries the INVOICE id, not the payment id`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = PaymentsViewModel(repo)

        // The regression in one line: payment id 9, invoice id 123 — the
        // server must be asked for 123.
        vm.downloadInvoice(payment(id = 9, invoiceId = "123"))
        advanceUntilIdle()

        assertEquals(listOf(123), repo.requestedIds)
    }

    @Test
    fun `a padded invoice id still resolves`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = PaymentsViewModel(repo)

        vm.downloadInvoice(payment(id = 7, invoiceId = " 456 "))
        advanceUntilIdle()

        assertEquals(listOf(456), repo.requestedIds)
    }

    @Test
    fun `a payment with NO invoice never reaches the network and says so plainly`() =
        runTest(dispatcher) {
            val repo = FakeRepo()
            val vm = PaymentsViewModel(repo)

            vm.downloadInvoice(payment(id = 7, invoiceId = null))
            advanceUntilIdle()

            assertTrue("no request may be issued without an invoice id", repo.requestedIds.isEmpty())
            assertEquals(
                "This payment has no invoice to download.",
                vm.state.value.error,
            )
            assertNull("the busy latch must not be left set", vm.state.value.downloadingInvoiceId)
        }

    @Test
    fun `a non-numeric invoice reference also fails closed`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = PaymentsViewModel(repo)

        vm.downloadInvoice(payment(id = 7, invoiceId = "INV-2026-889"))
        advanceUntilIdle()

        assertTrue(repo.requestedIds.isEmpty())
        assertEquals("This payment has no invoice to download.", vm.state.value.error)
    }
}
