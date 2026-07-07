package com.ga.airdrop.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuctionProduct
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeLiveDataParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeScreenRefreshesLiveDataOnResumeLikeSwiftViewDidAppear() {
        val repository = FakeHomeRepository()
        val viewModel = HomeViewModel(repository)

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.currentUserCalls.get() == 1 &&
                repository.airCoinsCalls.get() == 1 &&
                repository.auctionCalls.get() == 1 &&
                viewModel.state.value.firstName == "Kemar" &&
                viewModel.state.value.airCoins == "42" &&
                viewModel.state.value.auctionHighlights.size == 1
        }

        val lifecycleOwner = TestLifecycleOwner()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
        }

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AirdropTheme {
                    HomeScreen(onNavigate = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Services").fetchSemanticsNodes().isNotEmpty()
        }

        instrumentation.runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_START)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.currentUserCalls.get() >= 2 &&
                repository.airCoinsCalls.get() >= 2 &&
                repository.auctionCalls.get() >= 2
        }
    }

    @Test
    fun pullToRefreshReloadsLiveDataLikeSwiftHomeRefreshControl() {
        val repository = FakeHomeRepository()
        val viewModel = HomeViewModel(repository)

        // Let the init{} cold load settle so the pull isn't a no-op behind an
        // active refresh job.
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.currentUserCalls.get() >= 1 &&
                viewModel.state.value.auctionHighlights.size == 1 &&
                !viewModel.state.value.loading
        }

        val lifecycleOwner = TestLifecycleOwner()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AirdropTheme {
                    HomeScreen(onNavigate = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Services").fetchSemanticsNodes().isNotEmpty()
        }

        val callsBefore = repository.currentUserCalls.get()

        // Swift homeRefreshControl: pulling the Home scroll fires onPullToRefresh.
        compose.onNodeWithTag("home-pull-refresh").performTouchInput { swipeDown() }

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.currentUserCalls.get() > callsBefore &&
                repository.airCoinsCalls.get() > callsBefore &&
                repository.auctionCalls.get() > callsBefore
        }
        assertTrue(repository.currentUserCalls.get() > callsBefore)
    }

    @Test
    fun auctionReloadFailureClearsStaleHighlightsLikeSwiftEmptyCard() {
        val repository = FakeHomeRepository()
        val viewModel = HomeViewModel(repository)

        compose.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.auctionHighlights.size == 1 &&
                !viewModel.state.value.loading
        }

        repository.auctionResult = Result.failure(IllegalStateException("offline"))
        viewModel.refresh()

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.auctionCalls.get() >= 2 &&
                viewModel.state.value.auctionHighlights.isEmpty() &&
                !viewModel.state.value.loading
        }

        assertTrue(viewModel.state.value.auctionHighlights.isEmpty())
        assertEquals(2, repository.auctionCalls.get())
    }

    private class FakeHomeRepository : HomeRepository {
        val currentUserCalls = AtomicInteger()
        val airCoinsCalls = AtomicInteger()
        val auctionCalls = AtomicInteger()
        var auctionResult: Result<List<AuctionProduct>> = Result.success(
            listOf(
                AuctionProduct(
                    id = 17,
                    name = "Swift Product",
                    currentPrice = "12.00",
                    imageUrl = "https://example.com/product.png",
                )
            )
        )

        override suspend fun currentUser(): Result<AirdropUser> {
            currentUserCalls.incrementAndGet()
            return Result.success(
                AirdropUser(
                    firstName = "Kemar",
                    customerTierName = "Gold Standard",
                )
            )
        }

        override suspend fun airCoinsStatus(): Result<AirCoinsStatus> {
            airCoinsCalls.incrementAndGet()
            return Result.success(AirCoinsStatus(available = 42, balance = 7))
        }

        override suspend fun auctionProductsShortlist(): Result<List<AuctionProduct>> {
            auctionCalls.incrementAndGet()
            return auctionResult
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun handle(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}
