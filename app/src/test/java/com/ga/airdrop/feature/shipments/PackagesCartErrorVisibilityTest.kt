package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.feature.cart.CartServerGateway
import com.ga.airdrop.feature.cart.CartStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * "Add to cart" could fail completely silently on the Packages list.
 *
 * The failure WAS captured — `toggleCart`'s onFailure wrote it to
 * `PackagesUiState.error`. But `error` means "we could not read your packages",
 * and PackagesScreen renders it only when `items.isEmpty()`. That gate is right
 * for load failures (a failed next page must not blank rows the customer is
 * reading) and exactly wrong for cart failures, which can ONLY happen when the
 * list is populated — you have to see a package to tap its cart button.
 *
 * So the message was written into the one field guaranteed not to be shown in
 * the only situation that produces it. The tap did nothing, said nothing, and
 * the customer went to checkout believing the package was in their cart.
 *
 * Two kinds of failure needed two channels, not one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackagesCartErrorVisibilityTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        CartStore.dropProcessStateForTests()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        CartStore.dropProcessStateForTests()
    }

    @Test
    fun `a failed add-to-cart surfaces on its own channel, not the load-error field`() = runTest(dispatcher) {
        val viewModel = PackagesViewModel(
            repo = FakePackagesRepo(),
            hubRepo = FakeHubRepo(),
            cartServer = FailingCartServer(),
            sessionBoundary = TestSessionBoundary(),
        )
        advanceUntilIdle()

        viewModel.toggleCart(packageRow())
        advanceUntilIdle()

        assertNotNull(
            "the customer must be told the cart update failed",
            viewModel.state.value.cartErrorMessage,
        )
        assertNull(
            "a cart failure must NOT be written to the load-error field — the screen " +
                "only renders that on an empty list, so it would never be seen",
            viewModel.state.value.error,
        )
    }

    @Test
    fun `dismissing the cart error clears it`() = runTest(dispatcher) {
        val viewModel = PackagesViewModel(
            repo = FakePackagesRepo(),
            hubRepo = FakeHubRepo(),
            cartServer = FailingCartServer(),
            sessionBoundary = TestSessionBoundary(),
        )
        advanceUntilIdle()
        viewModel.toggleCart(packageRow())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.cartErrorMessage)

        viewModel.consumeCartError()

        assertNull(viewModel.state.value.cartErrorMessage)
    }

    private fun packageRow() = ShipmentPackage(
        id = 401,
        trackingCode = "DROP-401",
        description = "Scrubber",
        weightLbs = 1.3,
        statusName = "Ready for Pickup",
        shippingMethod = "Standard",
    )

    private class FailingCartServer : CartServerGateway {
        override suspend fun cart(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(emptyList<CartStore.CartLine>())

        override suspend fun addPackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.failure<Unit>(IllegalStateException("Couldn't add that package to your cart."))

        override suspend fun removePackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.failure<Unit>(IllegalStateException("Couldn't remove that package."))
    }

    private class FakePackagesRepo : ShipmentsPackagesRepository {
        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ) = Result.success(Paged(listOf(ShipmentPackage(id = 401, description = "Scrubber"))))

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException())

        override suspend fun packageStatuses() = Result.success(ShipmentStatusCatalog.defaults)

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(UnsupportedOperationException())

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(UnsupportedOperationException())
    }

    private class FakeHubRepo : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)
        override suspend fun summary() = Result.success(ShipmentsSummary())
        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())
        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())
        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    private class TestSessionBoundary : AuthenticatedSessionBoundary {
        private val owner = AuthenticatedSessionOwner(sessionId = "session-a", accountId = 1)
        private val ownerFlow = MutableStateFlow<AuthenticatedSessionOwner?>(owner)
        override val changes: Flow<AuthenticatedSessionOwner?> = ownerFlow
        override fun capture(): AuthenticatedSessionOwner? = owner
        override fun isCurrent(owner: AuthenticatedSessionOwner) = owner.sessionId == this.owner.sessionId
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            action(); return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) = action()
        override fun requestOwner(owner: AuthenticatedSessionOwner) =
            AuthenticatedRequestOwner(
                session = owner,
                provenance = AuthTokenStore.RequestProvenance(revision = 1L, sessionId = owner.sessionId),
            )
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = true
    }
}
