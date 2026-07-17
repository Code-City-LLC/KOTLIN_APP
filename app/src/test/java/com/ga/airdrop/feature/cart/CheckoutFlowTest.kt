package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckoutFlowTest {

    private val ownerA = AuthenticatedSessionOwner("session-a", 10)
    private val ownerB = AuthenticatedSessionOwner("session-b", 20)
    private lateinit var cartPrefs: TestSharedPreferences
    private lateinit var checkoutPrefs: TestSharedPreferences
    private lateinit var notePrefs: TestSharedPreferences

    @Before
    fun setUp() {
        CartStore.synchronousCommitOverrideForTests = null
        CheckoutFlowStore.synchronousCommitOverrideForTests = null
        cartPrefs = TestSharedPreferences()
        checkoutPrefs = TestSharedPreferences()
        notePrefs = TestSharedPreferences()
        CartStore.restoreForTests(cartPrefs, ownerA)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, ownerA)
        CartNoteStore.restoreForTests(notePrefs)
    }

    @After
    fun tearDown() {
        CartStore.synchronousCommitOverrideForTests = null
        CheckoutFlowStore.synchronousCommitOverrideForTests = null
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        CartNoteStore.dropProcessStateForTests()
    }

    private fun reload(owner: AuthenticatedSessionOwner? = ownerA) {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        CartNoteStore.dropProcessStateForTests()
        CartStore.restoreForTests(cartPrefs, owner)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
        CartNoteStore.restoreForTests(notePrefs)
    }

    private fun sale(id: Int, packageId: Int = id + 100): CartStore.CartLine =
        CartStore.CartLine(
            id = id,
            packageId = packageId,
            title = "Sale $id",
            priceUsd = 12.0,
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

    private fun pendingCheckout(line: CartStore.CartLine, sessionId: String = "cs_paid") {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(line)))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, expectedFlowId = flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        val creation = requireNotNull(CheckoutFlowStore.beginHostedCheckoutCreation(ownerA))
        requireNotNull(CheckoutFlowStore.recordHostedCheckout(ownerA, creation.id, sessionId))
    }

    @Test
    fun `hosted url authority is not exposed when synchronous record fails`() {
        val line = sale(1)
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(line)))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, expectedFlowId = flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        val creation = requireNotNull(CheckoutFlowStore.beginHostedCheckoutCreation(ownerA))
        checkoutPrefs.failNextCommit = true

        assertNull(CheckoutFlowStore.recordHostedCheckout(ownerA, creation.id, "cs_fail"))
        assertNull(CheckoutFlowStore.pending("cs_fail", ownerA))
        assertEquals(creation.id, CheckoutFlowStore.creating(ownerA)?.id)
        reload()
        assertNull(CheckoutFlowStore.pending("cs_fail", ownerA))
        assertEquals(creation.id, CheckoutFlowStore.creating(ownerA)?.id)
    }

    @Test
    fun `creation authority commit failure permits zero dispatch and leaves no memory guard`() {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(sale(30))))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        checkoutPrefs.failNextCommit = true

        assertNull(CheckoutFlowStore.beginHostedCheckoutCreation(ownerA))
        assertNull(CheckoutFlowStore.creating(ownerA))
        reload()
        assertNull(CheckoutFlowStore.creating(ownerA))
        assertEquals(CheckoutPhase.ORDER_SUMMARY, CheckoutFlowStore.current(ownerA)?.phase)
    }

    @Test
    fun `creation authority survives process death and blocks replacement flow and back`() {
        val line = sale(31, packageId = 931)
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(line)))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        val creation = requireNotNull(CheckoutFlowStore.beginHostedCheckoutCreation(ownerA))

        reload()

        val restored = requireNotNull(CheckoutFlowStore.creating(ownerA))
        assertEquals(creation.id, restored.id)
        assertEquals(flow.id, restored.flowId)
        assertEquals(listOf(line.key), restored.cartKeys)
        assertEquals(listOf(931), restored.packageIds)
        assertTrue(restored.isAuction)
        assertNull(CheckoutFlowStore.start(ownerA, listOf(sale(32))))
        assertNull(CheckoutFlowStore.rewindOrderSummary(ownerA, flow.id))
        assertEquals(CheckoutPhase.ORDER_SUMMARY, CheckoutFlowStore.current(ownerA)?.phase)
    }

    @Test
    fun `replacement owner cannot bind restored creation authority`() {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(sale(33))))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        requireNotNull(CheckoutFlowStore.beginHostedCheckoutCreation(ownerA))

        reload(ownerB)

        assertNull(CheckoutFlowStore.current(ownerB))
        assertNull(CheckoutFlowStore.creating(ownerB))
        assertTrue(CheckoutFlowStore.start(ownerB, listOf(sale(34))) != null)
    }

    @Test
    fun `paid commit failure keeps cart and pending recoverable`() {
        val line = sale(2)
        CartStore.add(line)
        pendingCheckout(line)
        cartPrefs.failNextCommit = true

        assertFalse(commitVerifiedPaidCheckout("cs_paid", ownerA))
        assertTrue(CartStore.contains(line.key))
        assertTrue(CheckoutFlowStore.pending("cs_paid", ownerA) != null)
        reload()
        assertTrue(CartStore.contains(line.key))
        assertTrue(CheckoutFlowStore.pending("cs_paid", ownerA) != null)
    }

    @Test
    fun `pending consume failure leaves durable cart removal replayable`() {
        val line = sale(3)
        CartStore.add(line)
        pendingCheckout(line)
        checkoutPrefs.failNextCommit = true

        assertFalse(commitVerifiedPaidCheckout("cs_paid", ownerA))
        assertFalse(CartStore.contains(line.key))
        assertTrue(CheckoutFlowStore.pending("cs_paid", ownerA) != null)

        reload()
        assertFalse(CartStore.contains(line.key))
        assertTrue(CheckoutFlowStore.pending("cs_paid", ownerA) != null)
        assertTrue(commitVerifiedPaidCheckout("cs_paid", ownerA))
        reload()
        assertNull(CheckoutFlowStore.pending("cs_paid", ownerA))
    }

    @Test
    fun `paid removes captured key only and later equal-id package survives`() {
        val captured = sale(42, packageId = 900)
        val laterEqualId = pkg(42)
        val laterOther = pkg(99)
        CartStore.add(captured)
        pendingCheckout(captured)
        CartStore.add(laterEqualId)
        CartStore.add(laterOther)

        assertTrue(commitVerifiedPaidCheckout("cs_paid", ownerA))

        assertEquals(
            setOf(laterEqualId.key, laterOther.key),
            CartStore.items.value.map(CartStore.CartLine::key).toSet(),
        )
        assertNull(CheckoutFlowStore.pending("cs_paid", ownerA))
        reload()
        assertEquals(
            setOf(laterEqualId.key, laterOther.key),
            CartStore.items.value.map(CartStore.CartLine::key).toSet(),
        )
        assertNull(CheckoutFlowStore.pending("cs_paid", ownerA))
    }

    @Test
    fun `owner replacement cannot inspect or consume old pending`() {
        val line = sale(5)
        CartStore.add(line)
        pendingCheckout(line)

        assertNull(CheckoutFlowStore.pending("cs_paid", ownerB))
        assertFalse(commitVerifiedPaidCheckout("cs_paid", ownerB))
        assertTrue(CartStore.contains(line.key))
        assertTrue(CheckoutFlowStore.pending("cs_paid", ownerA) != null)

        checkoutPrefs.failNextCommit = true
        CheckoutFlowStore.onAuthenticatedSessionChanged(ownerB)
        CartStore.onAuthenticatedSessionChanged(ownerB)
        reload(ownerB)
        assertNull(CheckoutFlowStore.pending(ownerB))
        assertTrue(CartStore.items.value.isEmpty())
    }

    @Test
    fun `terminal not-paid commit failure keeps retry blocked until durable release`() {
        val line = sale(6)
        pendingCheckout(line, sessionId = "cs_terminal")
        checkoutPrefs.failNextCommit = true

        assertFalse(CheckoutFlowStore.releaseTerminalNotPaid("cs_terminal", ownerA))
        assertTrue(CheckoutFlowStore.pending("cs_terminal", ownerA) != null)

        reload()
        assertTrue(CheckoutFlowStore.pending("cs_terminal", ownerA) != null)
        assertTrue(CheckoutFlowStore.releaseTerminalNotPaid("cs_terminal", ownerA))
        reload()
        assertNull(CheckoutFlowStore.pending("cs_terminal", ownerA))
    }

    @Test
    fun `generic cancel cannot release a pending checkout`() {
        val line = sale(7)
        pendingCheckout(line, sessionId = "cs_bound")

        assertFalse(CheckoutFlowStore.releaseTerminalNotPaid("", ownerA))
        assertFalse(CheckoutFlowStore.releaseTerminalNotPaid("cs_other", ownerA))
        assertTrue(CheckoutFlowStore.pending("cs_bound", ownerA) != null)
        assertNull(CheckoutFlowStore.start(ownerA, listOf(sale(8))))
    }

    @Test
    fun `authoritative terminal cancel releases exact pending and permits retry`() {
        val line = sale(8)
        pendingCheckout(line, sessionId = "cs_cancelled")

        assertTrue(CheckoutFlowStore.releaseTerminalNotPaid("cs_cancelled", ownerA))
        reload()
        assertNull(CheckoutFlowStore.pending(ownerA))
        assertTrue(CheckoutFlowStore.start(ownerA, listOf(sale(9))) != null)
    }

    @Test
    fun `account note saved before flow survives recreation and flow is immutable snapshot`() {
        assertTrue(CartNoteStore.save(ownerA, "  initial note  "))
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(sale(10))))
        assertTrue(CartNoteStore.save(ownerA, "edited on summary"))

        reload()

        assertEquals("edited on summary", CartNoteStore.note(ownerA))
        assertEquals(flow.id, CheckoutFlowStore.current(ownerA)?.id)
    }

    @Test
    fun `account note is isolated and blank removes only that account`() {
        assertTrue(CartNoteStore.save(ownerA, "account A"))
        assertTrue(CartNoteStore.save(ownerB, "account B"))
        assertEquals("account A", CartNoteStore.note(AuthenticatedSessionOwner("new-session-a", 10)))
        assertEquals("account B", CartNoteStore.note(ownerB))

        assertTrue(CartNoteStore.save(ownerA, "  \n  "))
        reload()

        assertEquals("", CartNoteStore.note(ownerA))
        assertEquals("account B", CartNoteStore.note(ownerB))
    }

    @Test
    fun `verified paid checkout does not clear account note`() {
        val line = sale(11)
        assertTrue(CartNoteStore.save(ownerA, "leave at reception"))
        CartStore.add(line)
        pendingCheckout(line, "cs_note_paid")

        assertTrue(commitVerifiedPaidCheckout("cs_note_paid", ownerA))
        reload()

        assertEquals("leave at reception", CartNoteStore.note(ownerA))
    }

    @Test
    fun `cross-entrypoint pending package mutation blocks checkout snapshot`() {
        val line = pkg(77)
        CartStore.add(line)
        val delayedDelete = requireNotNull(CartStore.beginPackageMutation(line, adding = false))

        assertNull(CheckoutFlowStore.start(ownerA, listOf(line)))

        assertTrue(CartStore.finishPackageMutation(delayedDelete, succeeded = false))
        assertTrue(CheckoutFlowStore.start(ownerA, listOf(line)) != null)
    }

    @Test
    fun `currency parsing and routing fail closed`() {
        assertEquals(CheckoutCurrency.USD, parseCheckoutCurrency(" usd "))
        assertEquals(CheckoutCurrency.JMD, parseCheckoutCurrency("JmD"))
        assertNull(parseCheckoutCurrency("EUR"))
        assertNull(checkoutNextRoute(""))
        assertNull(checkoutPaymentRail("CAD"))
    }

    @Test
    fun `JMD summary rewinds to profile and can advance again`() {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(sale(20))))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "JMD", phase = CheckoutPhase.PROFILE_INFORMATION)
            },
        )
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )

        val rewound = requireNotNull(CheckoutFlowStore.rewindOrderSummary(ownerA, flow.id))
        assertEquals(CheckoutPhase.PROFILE_INFORMATION, rewound.phase)
        assertEquals("JMD", rewound.currency)

        val advanced = requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        assertEquals(CheckoutPhase.ORDER_SUMMARY, advanced.phase)
    }

    @Test
    fun `USD summary rewinds to delivery and clears currency`() {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(sale(21))))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )

        val rewound = requireNotNull(CheckoutFlowStore.rewindOrderSummary(ownerA, flow.id))

        assertEquals(CheckoutPhase.DELIVERY, rewound.phase)
        assertNull(rewound.currency)
    }

    @Test
    fun `pending hosted checkout forbids summary rewind`() {
        val line = sale(22)
        pendingCheckout(line, "cs_no_rewind")
        val flow = requireNotNull(CheckoutFlowStore.current(ownerA))

        assertNull(CheckoutFlowStore.rewindOrderSummary(ownerA, flow.id))
        assertTrue(CheckoutFlowStore.pending("cs_no_rewind", ownerA) != null)
        assertEquals(CheckoutPhase.ORDER_SUMMARY, CheckoutFlowStore.current(ownerA)?.phase)
    }

    @Test
    fun `summary rewind commit failure retains durable order summary`() {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(sale(23))))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        checkoutPrefs.failNextCommit = true

        assertNull(CheckoutFlowStore.rewindOrderSummary(ownerA, flow.id))
        assertEquals(CheckoutPhase.ORDER_SUMMARY, CheckoutFlowStore.current(ownerA)?.phase)
        reload()
        assertEquals(CheckoutPhase.ORDER_SUMMARY, CheckoutFlowStore.current(ownerA)?.phase)
        assertEquals("USD", CheckoutFlowStore.current(ownerA)?.currency)
    }
}
