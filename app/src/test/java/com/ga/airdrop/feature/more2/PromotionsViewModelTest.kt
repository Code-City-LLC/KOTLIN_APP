package com.ga.airdrop.feature.more2

import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.feature.shop.ShopProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromotionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `one successful feed is partial success and keeps eligible content`() = runTest(dispatcher) {
        val feeds = FakeFeeds(
            bannersResults = queue(Result.failure(IllegalStateException("banner down"))),
            featuredResults = queue(Result.success(listOf(macBook(id = 9)))),
            saleResults = queue(Result.failure(IllegalStateException("sale down"))),
        )

        val viewModel = PromotionsViewModel(feeds)
        assertTrue(viewModel.state.value.loading)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertTrue(state.hasLoaded)
        assertNull(state.error)
        assertEquals(listOf(9), state.amazonFinds.map(ShopProduct::id))
        assertTrue(state.banners.isEmpty())
        assertTrue(state.saleHighlights.isEmpty())
        assertEquals(1, feeds.bannerCalls)
        assertEquals(1, feeds.featuredCalls)
        assertEquals(1, feeds.saleCalls)
    }

    @Test
    fun `successful empty feed beats two failures and renders empty not error`() = runTest(dispatcher) {
        val viewModel = PromotionsViewModel(
            FakeFeeds(
                bannersResults = queue(Result.success(emptyList())),
                featuredResults = queue(Result.failure(IllegalStateException("featured down"))),
                saleResults = queue(Result.failure(IllegalStateException("sale down"))),
            ),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasLoaded)
        assertNull(state.error)
        assertTrue(state.banners.isEmpty())
        assertTrue(state.amazonFinds.isEmpty())
        assertTrue(state.saleHighlights.isEmpty())
    }

    @Test
    fun `all feed failures expose exact retryable error and retry can recover`() = runTest(dispatcher) {
        val feeds = FakeFeeds(
            bannersResults = queue(
                Result.failure(IllegalStateException("banner down")),
                Result.success(listOf(PromotionalBanner(id = 22, title = "Recovered"))),
            ),
            featuredResults = queue(
                Result.failure(IllegalStateException("featured down")),
                Result.success(emptyList()),
            ),
            saleResults = queue(
                Result.failure(IllegalStateException("sale down")),
                Result.success(emptyList()),
            ),
        )
        val viewModel = PromotionsViewModel(feeds)
        advanceUntilIdle()

        assertEquals(PROMOTIONS_ALL_FEEDS_ERROR, viewModel.state.value.error)
        assertTrue(viewModel.state.value.hasLoaded)

        viewModel.load()
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(listOf(22), viewModel.state.value.banners.map(PromotionalBanner::id))
        assertEquals(2, feeds.bannerCalls)
        assertEquals(2, feeds.featuredCalls)
        assertEquals(2, feeds.saleCalls)
    }

    @Test
    fun `failed feed on refresh preserves its previously successful section`() = runTest(dispatcher) {
        val feeds = FakeFeeds(
            bannersResults = queue(
                Result.success(listOf(PromotionalBanner(id = 31, title = "Keep me"))),
                Result.failure(IllegalStateException("temporary banner failure")),
            ),
            featuredResults = queue(
                Result.success(emptyList()),
                Result.success(listOf(macBook(id = 32))),
            ),
            saleResults = queue(Result.success(emptyList()), Result.success(emptyList())),
        )
        val viewModel = PromotionsViewModel(feeds)
        advanceUntilIdle()

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf(31), viewModel.state.value.banners.map(PromotionalBanner::id))
        assertEquals(listOf(32), viewModel.state.value.amazonFinds.map(ShopProduct::id))
        assertNull(viewModel.state.value.error)
    }

    private class FakeFeeds(
        private val bannersResults: ArrayDeque<Result<List<PromotionalBanner>>>,
        private val featuredResults: ArrayDeque<Result<List<ShopProduct>>>,
        private val saleResults: ArrayDeque<Result<List<ShopProduct>>>,
    ) : PromotionsFeeds {
        var bannerCalls = 0
        var featuredCalls = 0
        var saleCalls = 0

        override suspend fun banners(): Result<List<PromotionalBanner>> {
            bannerCalls += 1
            return bannersResults.removeFirst()
        }

        override suspend fun featuredProducts(): Result<List<ShopProduct>> {
            featuredCalls += 1
            return featuredResults.removeFirst()
        }

        override suspend fun saleProducts(): Result<List<ShopProduct>> {
            saleCalls += 1
            return saleResults.removeFirst()
        }
    }

    private fun macBook(id: Int) = ShopProduct(
        id = id,
        title = "MacBook Pro",
        imageUrl = "https://cdn.test/$id.png",
        priceUsd = 1_999.0,
        amazonUrl = "https://www.amazon.com/dp/$id?tag=partner",
    )

    @SafeVarargs
    private fun <T> queue(vararg values: T): ArrayDeque<T> = ArrayDeque(values.toList())
}
