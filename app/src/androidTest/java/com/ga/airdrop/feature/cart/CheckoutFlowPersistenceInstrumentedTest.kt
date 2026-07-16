package com.ga.airdrop.feature.cart

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android [SharedPreferences] binding proof for the checkout authority.
 * JVM tests retain deterministic commit-failure injection; these tests prove
 * the same serialized state survives an actual process-memory drop/reload.
 */
@RunWith(AndroidJUnit4::class)
class CheckoutFlowPersistenceInstrumentedTest {

    private val ownerA = AuthenticatedSessionOwner("instrumented-session-a", 101)
    private val ownerB = AuthenticatedSessionOwner("instrumented-session-b", 202)
    private lateinit var cartPrefs: SharedPreferences
    private lateinit var checkoutPrefs: SharedPreferences

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        cartPrefs = context.getSharedPreferences(CART_PREFS, Context.MODE_PRIVATE)
        checkoutPrefs = context.getSharedPreferences(CHECKOUT_PREFS, Context.MODE_PRIVATE)
        assertTrue(cartPrefs.edit().clear().commit())
        assertTrue(checkoutPrefs.edit().clear().commit())
        CartStore.restoreForTests(cartPrefs, ownerA)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, ownerA)
    }

    @After
    fun tearDown() {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        cartPrefs.edit().clear().commit()
        checkoutPrefs.edit().clear().commit()
    }

    @Test
    fun hostedRecordReloadsWithExactOwnerAndCapturedKeys() {
        val captured = sale(1, packageId = 901)
        CartStore.add(captured)
        pendingCheckout(captured, "cs_reload")

        reload(ownerA)

        val pending = CheckoutFlowStore.pending("cs_reload", ownerA)
        assertEquals(setOf(captured.key), pending?.cartKeys?.toSet())
        assertEquals(ownerA.sessionId, pending?.ownerSessionId)
        assertEquals(ownerA.accountId, pending?.ownerAccountId)
        assertTrue(CartStore.contains(captured.key))
    }

    @Test
    fun verifiedPaidRemovalAndPendingConsumeSurviveReload() {
        val captured = sale(2, packageId = 902)
        val laterSameNumericPackage = pkg(2)
        val laterOther = pkg(3)
        CartStore.add(captured)
        pendingCheckout(captured, "cs_paid")
        CartStore.add(laterSameNumericPackage)
        CartStore.add(laterOther)

        assertTrue(commitVerifiedPaidCheckout("cs_paid", ownerA))
        reload(ownerA)

        assertEquals(
            setOf(laterSameNumericPackage.key, laterOther.key),
            CartStore.items.value.map(CartStore.CartLine::key).toSet(),
        )
        assertNull(CheckoutFlowStore.pending(ownerA))
    }

    @Test
    fun terminalNotPaidReleaseSurvivesReloadAndPermitsRetry() {
        val captured = sale(4, packageId = 904)
        pendingCheckout(captured, "cs_cancelled")

        assertTrue(CheckoutFlowStore.releaseTerminalNotPaid("cs_cancelled", ownerA))
        reload(ownerA)

        assertNull(CheckoutFlowStore.pending(ownerA))
        assertTrue(CheckoutFlowStore.start(ownerA, listOf(sale(5, 905))) != null)
    }

    @Test
    fun replacementOwnerCannotReloadOrConsumePriorAuthority() {
        val captured = sale(6, packageId = 906)
        CartStore.add(captured)
        pendingCheckout(captured, "cs_old_owner")

        reload(ownerB)

        assertNull(CheckoutFlowStore.current(ownerB))
        assertNull(CheckoutFlowStore.pending(ownerB))
        assertFalse(commitVerifiedPaidCheckout("cs_old_owner", ownerB))
        assertTrue(CartStore.items.value.isEmpty())
    }

    private fun pendingCheckout(line: CartStore.CartLine, sessionId: String) {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(line)))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, expectedFlowId = flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        val creation = requireNotNull(CheckoutFlowStore.beginHostedCheckoutCreation(ownerA))
        requireNotNull(CheckoutFlowStore.recordHostedCheckout(ownerA, creation.id, sessionId))
    }

    private fun reload(owner: AuthenticatedSessionOwner) {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        CartStore.restoreForTests(cartPrefs, owner)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
    }

    private fun sale(id: Int, packageId: Int): CartStore.CartLine = CartStore.CartLine(
        id = id,
        packageId = packageId,
        title = "Sale $id",
        priceUsd = 10.0,
        kind = CartStore.CartLineKind.AUCTION,
        isAuction = true,
    )

    private fun pkg(id: Int): CartStore.CartLine = CartStore.CartLine(
        id = id,
        packageId = id,
        title = "Package $id",
        kind = CartStore.CartLineKind.PACKAGE,
        statusCode = 7,
        serverConfirmed = true,
    )

    private companion object {
        const val CART_PREFS = "issue138_cart_platform_binding_test"
        const val CHECKOUT_PREFS = "issue138_checkout_platform_binding_test"
    }
}
