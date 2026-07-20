package com.ga.airdrop.feature.shop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Swift 89fbb11 failure-card contract on the product lists: a failed fetch
 * must NOT clear already-loaded products (cached rows win), the failed flag
 * only drives the card when the grid is empty, and Retry re-enters the
 * loading state and recovers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductListLoadFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class ScriptedProductsRepo : ShopProductsRepository {
        val responses = ArrayDeque<Result<List<ShopProduct>>>()

        /** When set, requests suspend here so mid-flight state is observable. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun auctionProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> {
            gate?.await()
            return responses.removeFirst()
        }

        override suspend fun featuredProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> = responses.removeFirst()

        override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
            Result.failure(IllegalStateException("not used"))
    }

    private fun products(vararg ids: Int): List<ShopProduct> =
        ids.map { ShopProduct(id = it, title = "P$it") }

    @Test
    fun initialFailureSetsFailedThenRetryRecovers() = runTest(dispatcher) {
        val repo = ScriptedProductsRepo()
        repo.responses += Result.failure(IllegalStateException("offline"))
        val vm = AuctionViewModel(products = repo)
        advanceUntilIdle()

        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.products.isEmpty())

        repo.responses += Result.success(products(1, 2))
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        vm.loadFirstPage()
        runCurrent() // run up to the gated fetch
        assertTrue(vm.state.value.loading) // retry re-enters loading/skeleton
        assertFalse(vm.state.value.failed)
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.state.value.failed)
        assertEquals(listOf(1, 2), vm.state.value.products.map(ShopProduct::id))
    }

    @Test
    fun refreshFailureKeepsLoadedProducts() = runTest(dispatcher) {
        val repo = ScriptedProductsRepo()
        repo.responses += Result.success(products(1, 2, 3))
        val vm = AuctionViewModel(products = repo)
        advanceUntilIdle()
        assertEquals(3, vm.state.value.products.size)

        repo.responses += Result.failure(IllegalStateException("offline"))
        vm.loadFirstPage(refreshing = true)
        advanceUntilIdle()

        // Cached rows win: the failed refresh must not clear the grid.
        assertEquals(listOf(1, 2, 3), vm.state.value.products.map(ShopProduct::id))
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.refreshing)
    }
}
