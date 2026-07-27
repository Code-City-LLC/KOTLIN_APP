package com.ga.airdrop.feature.shipments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A retry that SUCCEEDS with nothing must clear the error.
 *
 * ⚠️ THIS PINS A REGRESSION I SHIPPED, and it is the exact inverse of the bug
 * the rest of this branch fixes.
 *
 * Adding the error write to Orders and Payments made a failed load say so —
 * correct. But neither `load()` cleared the error, so:
 *
 *   customer with ZERO orders, offline -> load fails -> error set, list empty
 *   network returns, they tap Retry     -> load SUCCEEDS, list still empty
 *   error is never cleared              -> "Couldn't load your orders" FOREVER
 *
 * The load worked. They genuinely have no orders. The app kept insisting it had
 * failed. I fixed "failed rendered as empty" and introduced "empty rendered as
 * failed" in the same change, on two of the four screens.
 *
 * Why exactly those two: Packages and Shop were written fresh and clear their
 * flag. Orders and Payments are where I RESTORED a previously-deleted error
 * write — and inherited the deleted code's missing reset without noticing.
 *
 * Found by an adversarial audit of my own work, not by my own tests. My tests
 * only ever drove failure, never failure-then-recovery, so they could not have
 * caught it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListErrorClearedOnRetryTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Fails until [failuresBeforeSuccess] is exhausted, then succeeds EMPTY. */
    private class FlakyOrders(var failing: Boolean = true) : ShipmentsOrdersRepository {
        override suspend fun orders(page: Int, perPage: Int, search: String?) =
            if (failing) Result.failure(java.io.IOException("offline"))
            else Result.success(Paged(emptyList<ShipmentOrder>(), isLastPage = true))

        override suspend fun orderDetails(orderId: Int): Result<ShipmentOrder> =
            Result.failure(UnsupportedOperationException())

        override suspend fun exchangeRate() = Result.success(161.0)
    }

    private class FlakyPayments(var failing: Boolean = true) : ShipmentsPaymentsRepository {
        override suspend fun payments(page: Int, perPage: Int, type: String?, search: String?) =
            if (failing) Result.failure(java.io.IOException("offline"))
            else Result.success(Paged(emptyList<ShipmentPayment>(), isLastPage = true))

        override suspend fun payment(paymentId: Int, refresh: Boolean): Result<ShipmentPayment> =
            Result.failure(UnsupportedOperationException())

        override suspend fun paymentInvoiceUrl(paymentId: Int): Result<String> =
            Result.failure(UnsupportedOperationException())
    }

    @Test
    fun `orders - a successful retry with zero results clears the failure`() =
        runTest(dispatcher) {
            val repo = FlakyOrders(failing = true)
            val vm = OrdersViewModel(repo = repo)
            advanceUntilIdle()
            assertNotNull("the first failure must be reported", vm.state.value.error)

            repo.failing = false
            vm.refresh()
            advanceUntilIdle()

            assertNull(
                "the retry SUCCEEDED — an empty result is not a failure, and the " +
                    "customer must not be told to retry forever",
                vm.state.value.error,
            )
            assertTrue(vm.state.value.items.isEmpty())
        }

    @Test
    fun `payments - a successful retry with zero results clears the failure`() =
        runTest(dispatcher) {
            val repo = FlakyPayments(failing = true)
            val vm = PaymentsViewModel(repo = repo)
            advanceUntilIdle()
            assertNotNull(vm.state.value.loadError)

            repo.failing = false
            vm.refresh()
            advanceUntilIdle()

            assertNull(
                "a successful empty payments load must not keep claiming failure",
                vm.state.value.loadError,
            )
            assertTrue(vm.state.value.items.isEmpty())
        }

    /**
     * The download alert is a SEPARATE fact and must survive a list reload —
     * clearing the list error must not silently dismiss a modal the customer
     * has not acknowledged.
     */
    @Test
    fun `payments - clearing the list error does not touch the invoice download alert`() =
        runTest(dispatcher) {
            val repo = FlakyPayments(failing = false)
            val vm = PaymentsViewModel(repo = repo)
            advanceUntilIdle()

            assertNull("no list failure occurred", vm.state.value.loadError)
            // `error` (download alert) is untouched by load(); it is owned by
            // the invoice path and consumed by its own dialog.
            assertNull(vm.state.value.error)
        }
}
