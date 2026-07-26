package com.ga.airdrop.feature.shop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A failed product fetch did THREE separate kinds of damage, all from one line:
 *
 *     val fetched = result.getOrElse { emptyList() }
 *
 * which made "the request failed" and "the catalogue is empty" the same value.
 *
 *   1. the customer was told **"No Products Found"** — AirDrop has nothing for
 *      sale — when the request had simply failed
 *   2. a failed REFRESH replaced the rows they were already reading with
 *      nothing, because `products = fetched` on the !append path
 *   3. `hasMore = fetched.size >= PER_PAGE` went **false** on an empty failure,
 *      so infinite scroll stopped PERMANENTLY for that session — even after the
 *      network recovered
 *
 * Only (1) is visible on screen. (2) and (3) are why a flaky connection left the
 * Shop looking broken until the app was force-killed, and neither would have
 * been found by looking at the empty state alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductListLoadFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private class ScriptedRepo(
        var result: Result<List<ShopProduct>>,
    ) : ShopProductsRepository {
        var calls = 0
        override suspend fun auctionProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> {
            calls++
            return result
        }

        override suspend fun featuredProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> = result

        override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
            Result.failure(UnsupportedOperationException())
    }

    private fun product(id: Int) = ShopProduct(id = id, title = "Item $id")

    @Test
    fun `a failed load is reported as a failure, not as an empty catalogue`() =
        runTest(dispatcher) {
            val repo = ScriptedRepo(Result.failure(java.io.IOException("unreachable")))
            val vm = AuctionViewModel(products = repo)
            vm.loadFirstPage()
            advanceUntilIdle()

            val s = vm.state.value
            assertNotNull("the failure must be recorded, not collapsed to empty", s.loadError)
            assertTrue(s.products.isEmpty())
        }

    /**
     * ⚠️ THE INVISIBLE ONE. A failed refresh used to wipe rows the customer was
     * actively reading, because the !append path assigned `products = fetched`
     * and `fetched` was an empty list standing in for an error.
     */
    @Test
    fun `a failed refresh keeps the rows already on screen`() = runTest(dispatcher) {
        val repo = ScriptedRepo(Result.success((1..10).map(::product)))
        val vm = AuctionViewModel(products = repo)
        vm.loadFirstPage()
        advanceUntilIdle()
        assertEquals(10, vm.state.value.products.size)

        repo.result = Result.failure(java.io.IOException("dropped"))
        vm.loadFirstPage(refreshing = true)
        advanceUntilIdle()

        assertEquals(
            "a dropped refresh must not erase what the customer is reading",
            10,
            vm.state.value.products.size,
        )
        assertNotNull(vm.state.value.loadError)
    }

    /**
     * ⚠️ THE PERMANENT ONE. `hasMore` was derived from the size of a list that
     * an error had emptied, so one failure ended pagination for the session.
     */
    @Test
    fun `a failed page does not permanently stop pagination`() = runTest(dispatcher) {
        val repo = ScriptedRepo(Result.success((1..10).map(::product)))
        val vm = AuctionViewModel(products = repo)
        vm.loadFirstPage()
        advanceUntilIdle()
        assertTrue(vm.state.value.hasMore)

        repo.result = Result.failure(java.io.IOException("dropped"))
        vm.loadNextPage()
        advanceUntilIdle()

        assertTrue(
            "a failed page is not proof the catalogue ended",
            vm.state.value.hasMore,
        )
    }

    /** Recovery clears the error rather than leaving a stale one on screen. */
    @Test
    fun `a successful load after a failure clears the error`() = runTest(dispatcher) {
        val repo = ScriptedRepo(Result.failure(java.io.IOException("dropped")))
        val vm = AuctionViewModel(products = repo)
        vm.loadFirstPage()
        advanceUntilIdle()
        assertNotNull(vm.state.value.loadError)

        repo.result = Result.success((1..10).map(::product))
        vm.loadFirstPage(refreshing = true)
        advanceUntilIdle()

        assertNull(vm.state.value.loadError)
        assertEquals(10, vm.state.value.products.size)
    }

    /** A genuine empty result is still empty — this must not mask that. */
    @Test
    fun `a successful empty response is not an error`() = runTest(dispatcher) {
        val repo = ScriptedRepo(Result.success(emptyList()))
        val vm = AuctionViewModel(products = repo)
        vm.loadFirstPage()
        advanceUntilIdle()

        assertNull("nothing failed, so nothing to report", vm.state.value.loadError)
        assertTrue(vm.state.value.products.isEmpty())
    }
}
