package com.ga.airdrop.feature.security

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.prefs.ExchangeRateStore
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Payment
import com.ga.airdrop.data.model.PaymentPackage
import com.ga.airdrop.data.repo.PaymentsRepository
import com.ga.airdrop.feature.shipments.DataShipmentsPaymentsRepository
import com.ga.airdrop.feature.shop.ShopCheckoutStore
import com.ga.airdrop.feature.shop.ShopProduct
import com.ga.airdrop.feature.shop.ShopProductHandoffStore
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricLockSignOutParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun signOutClearsEverySessionBoundaryBeforeDismissingLock() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.save("biometric-token")
        SessionStore.update { it.copy(firstName = "Prior", cartCount = 4) }
        ExchangeRateStore.init(context)
        ExchangeRateStore.update(199.75)
        ShopCheckoutStore.product = ShopProduct(id = 71, slug = "stale-checkout")
        ShopCheckoutStore.pendingRef = "stale-ref"
        ShopProductHandoffStore.put(ShopProduct(id = 72, slug = "stale-details"))

        val paymentId = 7301
        val seedPayments = paymentsRepository(listOf(payment(paymentId)))
        runBlocking {
            seedPayments.payments(1, 15, null, null).getOrThrow()
            assertEquals(paymentId, seedPayments.payment(paymentId).getOrThrow().id)
        }

        var dismissed = 0
        compose.setContent {
            BiometricLockContent(
                context = context,
                typeName = "Biometrics",
                authenticate = { false },
                onUnlocked = { dismissed += 1 },
                autoAuthenticate = false,
            )
        }

        compose.onNodeWithTag("biometric-lock-signout").performClick()
        compose.runOnIdle {
            assertNull(AuthTokenStore.token)
            assertEquals(SessionStore.HeaderInfo(), SessionStore.header.value)
            assertNull(ShopCheckoutStore.product)
            assertNull(ShopCheckoutStore.pendingRef)
            assertNull(ShopProductHandoffStore.consume("stale-details"))
            assertEquals(ExchangeRateStore.DEFAULT_USD_TO_JMD, ExchangeRateStore.current, 0.0)
            assertEquals(1, dismissed)
        }

        val emptyPayments = paymentsRepository(emptyList())
        runBlocking {
            assertTrue(emptyPayments.payment(paymentId).isFailure)
        }
        ExchangeRateStore.init(context)
        assertEquals(ExchangeRateStore.DEFAULT_USD_TO_JMD, ExchangeRateStore.current, 0.0)
    }

    private fun paymentsRepository(rows: List<Payment>) = DataShipmentsPaymentsRepository(
        PaymentsRepository(paymentService(rows)),
        Files.createTempDirectory("biometric-signout-payment-cache").toFile().apply { deleteOnExit() },
    )

    @Suppress("UNCHECKED_CAST")
    private fun paymentService(rows: List<Payment>): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "payments" -> Paginated(rows)
                else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService

    private fun payment(id: Int) = Payment(
        id = id,
        invoiceId = "INV-$id",
        paymentType = "package",
        method = "card",
        totalAmount = 42.50,
        trackingCode = "TRK-$id",
        paymentDate = "2026-07-07T00:00:00Z",
        packageId = id + 1,
        paymentPackage = PaymentPackage(description = "Package $id", statusName = "Paid"),
    )
}
