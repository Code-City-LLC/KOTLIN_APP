package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.feature.more.MoreProfileRepository
import com.ga.airdrop.feature.more.MoreUser
import com.ga.airdrop.feature.more.ProfileAsset
import com.ga.airdrop.feature.shop.ShopBillingProfile
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class CartViewModelCheckoutTest {

    private val ownerA = AuthenticatedSessionOwner("cart-vm-owner-a", 41)
    private lateinit var cartPrefs: TestSharedPreferences
    private lateinit var checkoutPrefs: TestSharedPreferences
    private lateinit var notePrefs: TestSharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        cartPrefs = TestSharedPreferences()
        checkoutPrefs = TestSharedPreferences()
        notePrefs = TestSharedPreferences()
        CartStore.restoreForTests(cartPrefs, ownerA)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, ownerA)
        CartNoteStore.restoreForTests(notePrefs)
    }

    @After
    fun tearDown() {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        CartNoteStore.dropProcessStateForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun `failed launcher retries exact recorded session and never creates twice`() = runTest {
        val line = sale(1, 901)
        CartStore.add(line)
        orderSummaryFlow(line)
        assertTrue(CartNoteStore.save(ownerA, "  edited on summary  "))
        val checkout = FakeCheckout()
        val boundary = FakeBoundary(ownerA)
        val viewModel = viewModel(checkout, boundary, line)

        viewModel.payOrderSummary()
        advanceUntilIdle()

        assertEquals(1, checkout.calls)
        assertEquals("edited on summary", checkout.lastUserNote)
        assertEquals("https://checkout.airdropja.test/session", viewModel.state.value.checkoutUrl)
        assertEquals(1L, viewModel.state.value.checkoutLaunchAttempt)

        // Launcher returned false: transient URL remains. A button retry emits
        // a new attempt for the same durable session, without a second POST.
        viewModel.payOrderSummary()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
        assertEquals(2L, viewModel.state.value.checkoutLaunchAttempt)
        assertEquals("cs_cart_vm", viewModel.state.value.checkoutSessionId)

        // Launcher later succeeded and consumed only transient state. Durable
        // pending authority still blocks a duplicate Stripe session.
        viewModel.consumeCheckoutUrl()
        viewModel.payOrderSummary()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
        assertEquals("Payment still pending", viewModel.state.value.errorTitle)
    }

    @Test
    fun `restored pending with empty UI state creates zero new sessions`() = runTest {
        val line = sale(2, 902)
        CartStore.add(line)
        orderSummaryFlow(line)
        val checkout = FakeCheckout()
        val boundary = FakeBoundary(ownerA)
        val first = viewModel(checkout, boundary, line)
        first.payOrderSummary()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)

        CheckoutFlowStore.dropProcessStateForTests()
        CheckoutFlowStore.restoreForTests(checkoutPrefs, ownerA)
        val recreated = viewModel(checkout, boundary, line)
        recreated.payOrderSummary()
        advanceUntilIdle()

        assertEquals(1, checkout.calls)
        assertNull(recreated.state.value.checkoutUrl)
        assertEquals("Payment still pending", recreated.state.value.errorTitle)
    }

    @Test
    fun `blank note is omitted and replacement account cannot dispatch old note`() = runTest {
        val line = sale(3, 903)
        CartStore.add(line)
        orderSummaryFlow(line)
        assertTrue(CartNoteStore.save(ownerA, "  "))
        val checkout = FakeCheckout()
        val boundary = FakeBoundary(ownerA)
        val viewModel = viewModel(checkout, boundary, line)

        viewModel.payOrderSummary()
        advanceUntilIdle()
        assertNull(checkout.lastUserNote)

        val ownerB = AuthenticatedSessionOwner("cart-vm-owner-b", 42)
        boundary.current.value = ownerB
        viewModel.consumeCheckoutUrl()
        viewModel.payOrderSummary()
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
    }

    @Test
    fun `summary back is blocked while checkout create is in flight`() = runTest {
        val line = sale(4, 904)
        CartStore.add(line)
        orderSummaryFlow(line)
        val checkout = DelayedCheckout()
        val boundary = FakeBoundary(ownerA)
        val viewModel = viewModel(checkout, boundary, line)

        viewModel.payOrderSummary()
        runCurrent()
        assertEquals(1, checkout.calls)
        assertTrue(viewModel.state.value.orderPaying)

        assertFalse(viewModel.rewindOrderSummary())
        assertEquals(CheckoutPhase.ORDER_SUMMARY, CheckoutFlowStore.current(ownerA)?.phase)
        assertEquals("Checkout in progress", viewModel.state.value.errorTitle)

        checkout.response.complete(
            Result.success(
                CheckoutResponse(
                    checkoutUrl = "https://checkout.airdropja.test/delayed",
                    sessionId = "cs_delayed",
                ),
            ),
        )
        advanceUntilIdle()
        assertEquals(1, checkout.calls)
        assertEquals("cs_delayed", viewModel.state.value.checkoutSessionId)
    }

    @Test
    fun `creation commit failure sends zero checkout requests`() = runTest {
        val line = sale(5, 905)
        CartStore.add(line)
        orderSummaryFlow(line)
        checkoutPrefs.failNextCommit = true
        val checkout = FakeCheckout()
        val viewModel = viewModel(checkout, FakeBoundary(ownerA), line)

        viewModel.payOrderSummary()
        advanceUntilIdle()

        assertEquals(0, checkout.calls)
        assertNull(CheckoutFlowStore.creating(ownerA))
        assertEquals("Checkout unavailable", viewModel.state.value.errorTitle)
    }

    @Test
    fun `cancelled dispatched request stays durable and recreated view model sends zero retries`() = runTest {
        val line = sale(6, 906)
        CartStore.add(line)
        orderSummaryFlow(line)
        val checkout = DelayedCheckout()
        val boundary = FakeBoundary(ownerA)
        val first = viewModel(checkout, boundary, line)

        first.payOrderSummary()
        runCurrent()
        assertEquals(1, checkout.calls)
        assertTrue(CheckoutFlowStore.creating(ownerA) != null)

        checkout.response.cancel(CancellationException("transport cancelled after dispatch"))
        advanceUntilIdle()
        CheckoutFlowStore.dropProcessStateForTests()
        CheckoutFlowStore.restoreForTests(checkoutPrefs, ownerA)

        val recreated = viewModel(checkout, boundary, line)
        recreated.payOrderSummary()
        advanceUntilIdle()

        assertEquals(1, checkout.calls)
        assertTrue(CheckoutFlowStore.creating(ownerA) != null)
        assertEquals("Payment status unknown", recreated.state.value.errorTitle)
    }

    @Test
    fun `known response survives cart mutation while request is in flight`() = runTest {
        val line = sale(7, 907)
        CartStore.add(line)
        orderSummaryFlow(line)
        val checkout = DelayedCheckout()
        val viewModel = viewModel(checkout, FakeBoundary(ownerA), line)

        viewModel.payOrderSummary()
        runCurrent()
        val concurrentPackage = CartStore.CartLine(
            id = 77,
            packageId = 77,
            title = "Concurrent package",
            kind = CartStore.CartLineKind.PACKAGE,
            statusCode = 7,
            serverConfirmed = true,
        )
        CartStore.add(concurrentPackage)
        requireNotNull(CartStore.beginPackageMutation(concurrentPackage, adding = false))
        checkout.response.complete(
            Result.success(
                CheckoutResponse(
                    checkoutUrl = "https://checkout.airdropja.test/known",
                    sessionId = "cs_known_after_cart_change",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(1, checkout.calls)
        assertEquals("https://checkout.airdropja.test/known", viewModel.state.value.checkoutUrl)
        assertTrue(CheckoutFlowStore.pending("cs_known_after_cart_change", ownerA) != null)
        assertNull(CheckoutFlowStore.creating(ownerA))
    }

    @Test
    fun `known session with insecure URL becomes pending and never posts twice`() = runTest {
        val line = sale(8, 908)
        CartStore.add(line)
        orderSummaryFlow(line)
        val checkout = FakeCheckout(
            response = CheckoutResponse(
                checkoutUrl = "http://checkout.airdropja.test/insecure",
                sessionId = "cs_known_insecure",
            ),
        )
        val viewModel = viewModel(checkout, FakeBoundary(ownerA), line)

        viewModel.payOrderSummary()
        advanceUntilIdle()
        viewModel.payOrderSummary()
        advanceUntilIdle()

        assertEquals(1, checkout.calls)
        assertNull(viewModel.state.value.checkoutUrl)
        assertTrue(CheckoutFlowStore.pending("cs_known_insecure", ownerA) != null)
        assertNull(CheckoutFlowStore.creating(ownerA))
        assertEquals("Payment still pending", viewModel.state.value.errorTitle)
    }

    @Test
    fun `profile GET carries captured provenance and replacement owner cannot apply response`() = runTest {
        val line = sale(9, 909)
        CartStore.add(line)
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(line)))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "JMD", phase = CheckoutPhase.PROFILE_INFORMATION)
            },
        )
        val boundary = FakeBoundary(ownerA)
        val profile = DelayedProfileRepository()
        val viewModel = CartViewModel(
            checkout = FakeCheckout(),
            cartServer = FakeCartGateway(line),
            sessionBoundary = boundary,
            profileRepository = profile,
        )

        viewModel.loadCheckoutProfile()
        runCurrent()
        assertEquals(
            AuthTokenStore.RequestProvenance(91, ownerA.sessionId, ownerA.accountId),
            profile.expectedSession,
        )

        boundary.current.value = AuthenticatedSessionOwner("replacement-owner", 42)
        runCurrent()
        profile.response.complete(
            Result.success(MoreUser(id = 41, firstName = "Stale", lastName = "Profile")),
        )
        advanceUntilIdle()

        assertEquals(listOf(ADD_NEW_CHECKOUT_PROFILE), viewModel.state.value.profileOptions)
        assertEquals(ADD_NEW_CHECKOUT_PROFILE, viewModel.state.value.selectedProfile)
        assertEquals("", viewModel.state.value.form.firstName)
    }

    private fun viewModel(
        checkout: ShopCheckoutRepository,
        boundary: FakeBoundary,
        line: CartStore.CartLine,
    ) = CartViewModel(
        checkout = checkout,
        cartServer = FakeCartGateway(line),
        sessionBoundary = boundary,
        profileRepository = FakeProfileRepository,
    )

    private fun orderSummaryFlow(line: CartStore.CartLine) {
        val flow = requireNotNull(CheckoutFlowStore.start(ownerA, listOf(line)))
        requireNotNull(
            CheckoutFlowStore.update(ownerA, flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
    }

    private fun sale(id: Int, packageId: Int) = CartStore.CartLine(
        id = id,
        packageId = packageId,
        title = "Sale $id",
        priceUsd = 12.0,
        kind = CartStore.CartLineKind.AUCTION,
        isAuction = true,
    )

    private class FakeCheckout(
        private val response: CheckoutResponse = CheckoutResponse(
            checkoutUrl = "https://checkout.airdropja.test/session",
            sessionId = "cs_cart_vm",
        ),
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
            return Result.success(response)
        }

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)
        override suspend fun billingProfile(): Result<ShopBillingProfile> = Result.success(ShopBillingProfile())
    }

    private class DelayedCheckout : ShopCheckoutRepository {
        var calls = 0
        val response = CompletableDeferred<Result<CheckoutResponse>>()

        override suspend fun createCheckout(
            packageIds: List<Int>,
            currency: String,
            isAuction: Boolean,
            userNote: String?,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<CheckoutResponse> {
            calls++
            return response.await()
        }

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)
        override suspend fun billingProfile(): Result<ShopBillingProfile> = Result.success(ShopBillingProfile())
    }

    private class FakeCartGateway(private val line: CartStore.CartLine) : CartServerGateway {
        override suspend fun cart(expectedSession: AuthTokenStore.RequestProvenance) = Result.success(listOf(line))
        override suspend fun addPackage(packageId: Int, expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(Unit)
        override suspend fun removePackage(packageId: Int, expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(Unit)
    }

    private class FakeBoundary(initial: AuthenticatedSessionOwner?) : AuthenticatedSessionBoundary {
        val current = MutableStateFlow(initial)
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
                    AuthTokenStore.RequestProvenance(91, it.sessionId, it.accountId),
                )
            }
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && owner.accountId == accountId
    }

    private object FakeProfileRepository : MoreProfileRepository {
        override suspend fun currentUser(
            expectedSession: AuthTokenStore.RequestProvenance?,
        ): Result<MoreUser> = Result.success(MoreUser())
        override suspend fun updateProfile(
            fields: Map<String, String?>,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<String?> = Result.success(null)
        override suspend fun profileImage(): Result<ProfileAsset> = Result.success(ProfileAsset(null, null))
        override suspend fun uploadProfileImage(
            bytes: ByteArray,
            fileName: String,
            mimeType: String,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<ProfileAsset> = Result.success(ProfileAsset(null, null))
        override suspend fun deleteProfileImage(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(Unit)
        override suspend fun fetchImage(url: String): Result<ByteArray> = Result.success(byteArrayOf())
    }

    private class DelayedProfileRepository : MoreProfileRepository {
        var expectedSession: AuthTokenStore.RequestProvenance? = null
        val response = CompletableDeferred<Result<MoreUser>>()

        override suspend fun currentUser(
            expectedSession: AuthTokenStore.RequestProvenance?,
        ): Result<MoreUser> {
            this.expectedSession = expectedSession
            return response.await()
        }

        override suspend fun updateProfile(
            fields: Map<String, String?>,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<String?> = Result.success(null)
        override suspend fun profileImage(): Result<ProfileAsset> = Result.success(ProfileAsset(null, null))
        override suspend fun uploadProfileImage(
            bytes: ByteArray,
            fileName: String,
            mimeType: String,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<ProfileAsset> = Result.success(ProfileAsset(null, null))
        override suspend fun deleteProfileImage(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(Unit)
        override suspend fun fetchImage(url: String): Result<ByteArray> = Result.success(byteArrayOf())
    }
}
