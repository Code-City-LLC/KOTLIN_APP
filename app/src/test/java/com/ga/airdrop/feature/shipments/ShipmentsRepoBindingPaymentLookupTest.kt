package com.ga.airdrop.feature.shipments

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Payment
import com.ga.airdrop.data.model.PaymentPackage
import com.ga.airdrop.data.repo.PaymentsRepository
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentsRepoBindingPaymentLookupTest {

    @Test
    fun paymentLookupContinuesPastTheFormerFivePageCutoff() = runBlocking {
        val targetPaymentId = 987_654_321
        val capture = CapturedPaymentCalls()
        val repository = DataShipmentsPaymentsRepository(
            PaymentsRepository(paymentsService(capture, targetPaymentId)),
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

    private class CapturedPaymentCalls {
        val pages = mutableListOf<Int>()
        val perPages = mutableListOf<Int>()
        val types = mutableListOf<String?>()
        val searches = mutableListOf<String?>()
    }

    @Suppress("UNCHECKED_CAST")
    private fun paymentsService(
        capture: CapturedPaymentCalls,
        targetPaymentId: Int,
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
                    Paginated(paymentsPage(page, perPage, targetPaymentId))
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
