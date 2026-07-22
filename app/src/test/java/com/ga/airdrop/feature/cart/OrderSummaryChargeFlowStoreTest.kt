package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrderSummaryChargeFlowStoreTest {
    private val owner = AuthenticatedSessionOwner("charge-flow-owner", 501)
    private lateinit var checkoutPrefs: TestSharedPreferences

    @Before
    fun setUp() {
        checkoutPrefs = TestSharedPreferences()
        CartStore.restoreForTests(TestSharedPreferences(), owner)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
    }

    @After
    fun tearDown() {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
    }

    @Test
    fun `shipment snapshot persists while generic updates cannot rewrite captured values`() {
        val shipment = packageLine(301)
        val sale = saleLine(302)
        val started = requireNotNull(CheckoutFlowStore.start(owner, listOf(shipment, sale)))
        val summary = requireNotNull(
            CheckoutFlowStore.update(owner, started.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        val identity = summary.chargeCaptureIdentity()
        val snapshot = CheckoutShipmentChargeSnapshot(
            cartKey = shipment.key,
            packageId = requireNotNull(shipment.packageId),
            declaredValueUsd = 120.0,
            additionalCharges = mapOf("Insurance" to 2.0, "Freight" to 8.0),
            additionalChargesTotalUsd = 10.0,
            exchangeRateUsdToJmd = 160.0,
        )

        val hydrated = requireNotNull(
            CheckoutFlowStore.recordShipmentChargeSnapshots(owner, identity, listOf(snapshot)),
        )
        assertEquals(identity, hydrated.chargeCaptureIdentity())
        assertEquals(listOf(snapshot), hydrated.shipmentChargeSnapshots)

        val genericUpdate = requireNotNull(
            CheckoutFlowStore.update(owner, started.id) {
                it.copy(shipmentChargeSnapshots = emptyList())
            },
        )
        assertEquals(listOf(snapshot), genericUpdate.shipmentChargeSnapshots)

        CheckoutFlowStore.dropProcessStateForTests()
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
        val restored = requireNotNull(CheckoutFlowStore.current(owner))
        assertEquals(identity, restored.chargeCaptureIdentity())
        assertEquals(listOf(snapshot), restored.shipmentChargeSnapshots)

        val saleSnapshot = snapshot.copy(
            cartKey = sale.key,
            packageId = requireNotNull(sale.packageId),
        )
        assertNull(
            CheckoutFlowStore.recordShipmentChargeSnapshots(owner, identity, listOf(saleSnapshot)),
        )
        assertEquals(listOf(snapshot), CheckoutFlowStore.current(owner)?.shipmentChargeSnapshots)

        assertEquals(
            CapturedLineRemovalResult.UPDATED,
            CheckoutFlowStore.removeCapturedLine(
                owner = owner,
                expectedFlowId = started.id,
                removedKey = shipment.key,
                remainingLines = listOf(sale),
            ),
        )
        assertTrue(CheckoutFlowStore.current(owner)?.shipmentChargeSnapshots.orEmpty().isEmpty())
    }

    private fun packageLine(id: Int) = CartStore.CartLine(
        id = id,
        packageId = id,
        title = "Package $id",
        priceUsd = 10.0,
        kind = CartStore.CartLineKind.PACKAGE,
        statusCode = 7,
        serverConfirmed = true,
    )

    private fun saleLine(id: Int) = CartStore.CartLine(
        id = id,
        packageId = id + 1_000,
        title = "Sale $id",
        priceUsd = 20.0,
        kind = CartStore.CartLineKind.AUCTION,
        isAuction = true,
    )
}
