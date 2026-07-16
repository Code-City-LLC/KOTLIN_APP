package com.ga.airdrop.feature.shop

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.feature.cart.CartNoteStore
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.cart.CheckoutFlowStore
import com.ga.airdrop.feature.cart.TestSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuctionCheckoutRetryTest {

    private val owner = AuthenticatedSessionOwner("auction-retry-owner", 73)
    private lateinit var checkoutPrefs: TestSharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        CartStore.clear()
        checkoutPrefs = TestSharedPreferences()
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
        CartNoteStore.restoreForTests(TestSharedPreferences())
        ShopCheckoutStore.product = product()
        ShopCheckoutStore.pendingRef = null
    }

    @After
    fun tearDown() {
        CheckoutFlowStore.dropProcessStateForTests()
        CartNoteStore.dropProcessStateForTests()
        ShopCheckoutStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `failed launch retries same auction session with one create call`() = runTest {
        assertTrue(CartNoteStore.save(owner, "  call on arrival  "))
        val checkout = FakeCheckout()
        val viewModel = AuctionCheckoutViewModel(checkout, FakeProducts, FakeBoundary(owner))

        viewModel.pay()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
        assertEquals("call on arrival", checkout.lastUserNote)
        assertEquals("https://checkout.airdropja.test/auction", viewModel.state.value.checkoutUrl)
        assertEquals("cs_auction_retry", viewModel.state.value.checkoutSessionId)
        assertEquals(1L, viewModel.state.value.checkoutLaunchAttempt)

        viewModel.pay()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
        assertEquals(2L, viewModel.state.value.checkoutLaunchAttempt)
        assertEquals("https://checkout.airdropja.test/auction", viewModel.state.value.checkoutUrl)

        viewModel.consumeCheckoutUrl()
        viewModel.pay()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
        assertEquals("Payment still pending", viewModel.state.value.errorTitle)
    }

    @Test
    fun `known session with insecure hosted URL is pending and never exposed or posted twice`() = runTest {
        val checkout = FakeCheckout(url = "http://checkout.airdropja.test/auction")
        val viewModel = AuctionCheckoutViewModel(checkout, FakeProducts, FakeBoundary(owner))

        viewModel.pay()
        advanceUntilIdle()
        viewModel.pay()
        advanceUntilIdle()

        assertEquals(1, checkout.calls)
        assertNull(viewModel.state.value.checkoutUrl)
        assertEquals("cs_auction_retry", CheckoutFlowStore.pending(owner)?.checkoutSessionId)
        assertNull(CheckoutFlowStore.creating(owner))
        assertEquals("Payment still pending", viewModel.state.value.errorTitle)
    }

    @Test
    fun `ambiguous auction failure survives recreation and blocks second POST`() = runTest {
        val checkout = FakeCheckout(failure = IllegalStateException("connection dropped"))
        val boundary = FakeBoundary(owner)
        val first = AuctionCheckoutViewModel(checkout, FakeProducts, boundary)

        first.pay()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
        assertTrue(CheckoutFlowStore.creating(owner) != null)

        CheckoutFlowStore.dropProcessStateForTests()
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
        val recreated = AuctionCheckoutViewModel(checkout, FakeProducts, boundary)
        recreated.pay()
        advanceUntilIdle()

        assertEquals(1, checkout.calls)
        assertTrue(CheckoutFlowStore.creating(owner) != null)
        assertEquals("Payment status unknown", recreated.state.value.errorTitle)
    }

    @Test
    fun `auction creation commit failure sends zero POSTs`() = runTest {
        checkoutPrefs.failNextCommit = true
        val checkout = FakeCheckout()
        val viewModel = AuctionCheckoutViewModel(checkout, FakeProducts, FakeBoundary(owner))

        viewModel.pay()
        advanceUntilIdle()

        assertEquals(0, checkout.calls)
        assertNull(CheckoutFlowStore.creating(owner))
    }

    private fun product() = ShopProduct(
        id = 17,
        packageId = 917,
        title = "Auction item",
        priceUsd = 20.0,
    )

    private class FakeCheckout(
        private val url: String = "https://checkout.airdropja.test/auction",
        private val failure: Throwable? = null,
    ) : ShopCheckoutRepository {
        var calls = 0
        var lastUserNote: String? = null

        override suspend fun createCheckout(
            packageIds: List<Int>,
            currency: String,
            isAuction: Boolean,
            userNote: String?,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<CheckoutResponse> {
            calls++
            lastUserNote = userNote
            failure?.let { return Result.failure(it) }
            return Result.success(CheckoutResponse(url, "cs_auction_retry"))
        }

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)
        override suspend fun billingProfile(): Result<ShopBillingProfile> = Result.success(ShopBillingProfile())
    }

    private object FakeProducts : ShopProductsRepository {
        override suspend fun auctionProducts(page: Int, perPage: Int, search: String?) =
            Result.success(emptyList<ShopProduct>())
        override suspend fun featuredProducts(page: Int, perPage: Int, search: String?) =
            Result.success(emptyList<ShopProduct>())
        override suspend fun productBySlug(slug: String, featured: Boolean) =
            Result.failure<ShopProduct>(IllegalStateException("not used"))
    }

    private class FakeBoundary(initial: AuthenticatedSessionOwner?) : AuthenticatedSessionBoundary {
        private val current = MutableStateFlow(initial)
        override val changes = current
        override fun capture(): AuthenticatedSessionOwner? = current.value
        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = current.value == owner
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (!isCurrent(owner)) return false
            action()
            return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean =
            isCurrent(owner) && action()
        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? =
            owner.takeIf(::isCurrent)?.let {
                AuthenticatedRequestOwner(
                    it,
                    AuthTokenStore.RequestProvenance(81, it.sessionId, it.accountId),
                )
            }
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && owner.accountId == accountId
    }
}
