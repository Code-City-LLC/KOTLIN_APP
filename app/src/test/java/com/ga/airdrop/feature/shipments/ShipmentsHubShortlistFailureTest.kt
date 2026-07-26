package com.ga.airdrop.feature.shipments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Shipments hub loads three INDEPENDENT shortlists. A failure in one must
 * not make the other two claim the customer has nothing.
 *
 * ⚠️ ALL THREE CALLS USED TO HAVE ONLY AN `onSuccess` BRANCH. A failure was
 * dropped on the floor, the state kept its default `emptyList()`, and the hub
 * rendered "No packages found", "No payments found" and "No orders" —
 * **three false statements about the customer's account produced by one dropped
 * request**, with nothing on screen suggesting anything had gone wrong.
 *
 * The flags are deliberately PER SECTION rather than one shared error. A single
 * flag would fix the silence and introduce the mirror bug: a packages failure
 * making the payments row claim a problem it does not have. These tests exist
 * mainly to pin that isolation, because it is the part a later "simplification"
 * would collapse.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShipmentsHubShortlistFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private class Hub(
        val packagesOk: Boolean = true,
        val paymentsOk: Boolean = true,
        val ordersOk: Boolean = true,
    ) : ShipmentsHubRepository {
        private fun <T> answer(ok: Boolean, value: T): Result<T> =
            if (ok) Result.success(value) else Result.failure(java.io.IOException("unreachable"))

        override suspend fun exchangeRate() = Result.success(161.0)
        override suspend fun summary() = Result.success(ShipmentsSummary())
        override suspend fun packagesShortlist() =
            answer(packagesOk, listOf(ShipmentPackage(id = 1)))
        override suspend fun paymentsShortlist() =
            answer(paymentsOk, listOf(ShipmentPayment(id = 1)))
        override suspend fun ordersShortlist() =
            answer(ordersOk, listOf(ShipmentOrder(id = 1)))
    }

    @Test
    fun `a packages failure is reported and does NOT implicate payments or orders`() =
        runTest(dispatcher) {
            val vm = ShipmentsViewModel(Hub(packagesOk = false))
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue("the packages failure must be visible", s.packagesFailed)
            assertFalse("payments loaded fine and must not claim a failure", s.paymentsFailed)
            assertFalse("orders loaded fine and must not claim a failure", s.ordersFailed)
            assertTrue("the sections that succeeded still render", s.payments.isNotEmpty())
            assertTrue(s.orders.isNotEmpty())
        }

    @Test
    fun `a payments failure does not implicate packages or orders`() = runTest(dispatcher) {
        val vm = ShipmentsViewModel(Hub(paymentsOk = false))
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.paymentsFailed)
        assertFalse(s.packagesFailed)
        assertFalse(s.ordersFailed)
        assertTrue(s.packages.isNotEmpty())
    }

    /**
     * The original defect in full: everything down, and the customer used to be
     * told — three separate times — that they simply had nothing.
     */
    @Test
    fun `all three down is three reported failures, not three empty sections`() =
        runTest(dispatcher) {
            val vm = ShipmentsViewModel(
                Hub(packagesOk = false, paymentsOk = false, ordersOk = false),
            )
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.packagesFailed)
            assertTrue(s.paymentsFailed)
            assertTrue(s.ordersFailed)
            assertTrue(s.packages.isEmpty())
            assertTrue(s.payments.isEmpty())
            assertTrue(s.orders.isEmpty())
        }

    /** A healthy load reports no failure anywhere. */
    @Test
    fun `everything succeeding raises no failure flags`() = runTest(dispatcher) {
        val vm = ShipmentsViewModel(Hub())
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.packagesFailed)
        assertFalse(s.paymentsFailed)
        assertFalse(s.ordersFailed)
    }
}
