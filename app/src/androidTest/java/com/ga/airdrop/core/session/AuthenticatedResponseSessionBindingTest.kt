package com.ga.airdrop.core.session

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.Warehouse
import com.ga.airdrop.data.repo.CustomerTierReader
import com.ga.airdrop.feature.home.HomeRepository
import com.ga.airdrop.feature.home.HomeViewModel
import com.ga.airdrop.feature.homedetails.CurrentTierNameReader
import com.ga.airdrop.feature.homedetails.GoldPriorityViewModel
import com.ga.airdrop.feature.homedetails.TierCatalogStatus
import com.ga.airdrop.feature.homedetails.WarehousesRepository
import com.ga.airdrop.feature.homedetails.WarehousesViewModel
import com.ga.airdrop.feature.more.MoreHubRepository
import com.ga.airdrop.feature.more.MoreUser
import com.ga.airdrop.feature.more.MoreViewModel
import com.ga.airdrop.feature.more.ProfileAsset
import com.ga.airdrop.feature.more.ProfileViewModel
import com.ga.airdrop.feature.more.PreferencesViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticatedResponseSessionBindingTest {

    @Test
    fun authoritativeCurrentUserBindsMissingAccountIdentityForSameSession() {
        val boundary = FakeAuthenticatedSessionBoundary("session-without-account", initialAccountId = null)
        val currentUserCalls = AtomicInteger()
        val repository = object : HomeRepository {
            override suspend fun currentUser(): Result<AirdropUser> {
                currentUserCalls.incrementAndGet()
                return Result.success(AirdropUser(id = 101, firstName = "Kemar"))
            }
            override suspend fun airCoinsStatus() = Result.success(AirCoinsStatus(available = 22))
            override suspend fun auctionProductsShortlist() = Result.success(emptyList<AuctionProduct>())
        }

        val viewModel = onMain { HomeViewModel(repository, boundary) }
        waitUntil { boundary.capture()?.accountId == 101 && viewModel.state.value.firstName == "Kemar" }
        waitForIdle()

        assertEquals(101, boundary.capture()?.accountId)
        assertEquals("Binding account identity must not restart same-session jobs", 1, currentUserCalls.get())
    }

    @Test
    fun homeRejectsLateAccountAAndPublishesAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val accountA = BlockingResult<AirdropUser>()
        val calls = AtomicInteger()
        val repository = object : HomeRepository {
            override suspend fun currentUser(): Result<AirdropUser> =
                if (calls.incrementAndGet() == 1) accountA.awaitResult()
                else Result.success(AirdropUser(firstName = "Account B"))

            override suspend fun airCoinsStatus() = Result.success(AirCoinsStatus(available = 22))
            override suspend fun auctionProductsShortlist() =
                Result.success(listOf(AuctionProduct(id = 2, name = "Account B item")))
        }
        val viewModel = onMain { HomeViewModel(repository, boundary) }
        accountA.awaitEntered()

        boundary.replace("account-b", accountId = 2)
        accountA.complete(Result.success(AirdropUser(firstName = "Account A")))
        waitUntil { calls.get() == 2 && viewModel.state.value.firstName == "Account B" }
        waitForIdle()

        assertEquals("Account B", viewModel.state.value.firstName)
        assertEquals("Session replacement must trigger exactly one reload", 2, calls.get())
        assertEquals("Account B", SessionStore.header.value.firstName)
        assertFalse(viewModel.state.value.loading)
        assertTrue(boundary.rejectedApplyAttempts.get() > 0)
    }

    @Test
    fun moreRejectsLateAccountAAndPublishesAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val accountA = BlockingResult<MoreUser>()
        val repository = FakeMoreHubRepository(accountA)
        val viewModel = onMain { MoreViewModel(repository, boundary) }
        accountA.awaitEntered()

        boundary.replace("account-b", accountId = 2)
        accountA.complete(Result.success(MoreUser(firstName = "Account", lastName = "A")))
        waitUntil {
            repository.currentUserCalls.get() == 2 && viewModel.state.value.name == "Account B"
        }
        waitForIdle()

        assertEquals("Account B", viewModel.state.value.name)
        assertEquals("Account", SessionStore.header.value.firstName)
        assertTrue(boundary.rejectedApplyAttempts.get() > 0)
    }

    @Test
    fun profileRejectsLateAccountAAndPublishesAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val accountA = BlockingResult<MoreUser>()
        val repository = FakeMoreHubRepository(accountA)
        val viewModel = onMain { ProfileViewModel(repository, boundary) }
        accountA.awaitEntered()

        boundary.replace("account-b")
        accountA.complete(Result.success(MoreUser(email = "a@example.com")))
        waitUntil {
            repository.currentUserCalls.get() == 2 && viewModel.state.value.email == "b@example.com"
        }
        waitForIdle()

        assertEquals("b@example.com", viewModel.state.value.email)
        assertFalse(viewModel.state.value.saving)
        assertTrue(boundary.rejectedApplyAttempts.get() > 0)
    }

    @Test
    fun warehousesRejectLateAccountAAndPublishesAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val accountA = BlockingResult<AirdropUser>()
        val calls = AtomicInteger()
        val repository = object : WarehousesRepository {
            override suspend fun currentUser(): Result<AirdropUser> =
                if (calls.incrementAndGet() == 1) accountA.awaitResult()
                else Result.success(AirdropUser(firstName = "Account B"))

            override suspend fun warehouses(): Result<List<Warehouse>> =
                Result.success(listOf(Warehouse(id = calls.get(), name = "Warehouse B")))
        }
        val viewModel = onMain { WarehousesViewModel(repository, boundary) }
        accountA.awaitEntered()

        boundary.replace("account-b")
        accountA.complete(Result.success(AirdropUser(firstName = "Account A")))
        waitUntil { calls.get() == 2 && viewModel.state.value.user?.firstName == "Account B" }
        waitForIdle()

        assertEquals("Account B", viewModel.state.value.user?.firstName)
        assertEquals("Warehouse B", viewModel.state.value.warehouses.single().name)
        assertFalse(viewModel.state.value.loading)
        assertTrue(boundary.rejectedApplyAttempts.get() > 0)
    }

    @Test
    fun tierFallbackRejectsLateAccountAAndPublishesAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val accountA = BlockingResult<List<ServiceTier>>()
        val calls = AtomicInteger()
        val reader = object : CustomerTierReader {
            override suspend fun serviceTiers(): Result<List<ServiceTier>> =
                if (calls.incrementAndGet() == 1) accountA.awaitResult()
                else Result.success(listOf(ServiceTier(code = "PLAT", benefitsSummary = listOf("B"))))

            override suspend fun customerTier(): Result<CustomerTier> =
                Result.success(CustomerTier(currentTier = "PLAT"))
        }
        val viewModel = onMain {
            GoldPriorityViewModel(reader, CurrentTierNameReader { "Platinum Priority" }, boundary)
        }
        accountA.awaitEntered()

        boundary.replace("account-b")
        accountA.complete(
            Result.success(listOf(ServiceTier(code = "GOLD", benefitsSummary = listOf("A")))),
        )
        waitUntil {
            calls.get() == 2 &&
                viewModel.state.value.catalogStatus == TierCatalogStatus.Ready &&
                viewModel.state.value.benefitRowsByCode["PLAT"] == listOf("B")
        }
        waitForIdle()

        assertEquals(listOf("B"), viewModel.state.value.benefitRowsByCode["PLAT"])
        assertEquals(null, viewModel.state.value.benefitRowsByCode["GOLD"])
        assertTrue(boundary.rejectedApplyAttempts.get() > 0)
    }

    @Test
    fun preferencesRejectLateAccountAAndPersistAccountB() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(PreferencesViewModel.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val accountA = BlockingResult<MoreUser>()
        val repository = FakeMoreHubRepository(accountA)
        val viewModel = onMain {
            PreferencesViewModel(repository, boundary).also { it.start(context) }
        }
        accountA.awaitEntered()

        boundary.replace("account-b", accountId = 2)
        accountA.complete(
            Result.success(
                MoreUser(
                    email = "a@example.com",
                    pickupLocation = "Kingston",
                    paymentCurrency = "USD",
                ),
            ),
        )
        waitUntil {
            repository.currentUserCalls.get() == 2 && viewModel.state.value.email == "b@example.com"
        }

        val prefs = context.getSharedPreferences(PreferencesViewModel.PREFS, Context.MODE_PRIVATE)
        assertEquals("b@example.com", viewModel.state.value.email)
        assertEquals(
            "Montego Bay",
            prefs.getString(PreferencesViewModel.KEY_PICKUP, null),
        )
        assertEquals(
            "JMD",
            prefs.getString(PreferencesViewModel.KEY_CURRENCY, null),
        )
        assertTrue(boundary.rejectedApplyAttempts.get() > 0)
    }

    @Test
    fun preferencesClearOnReplacementAndRepopulateFromCurrentUser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(PreferencesViewModel.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val repository = GuardedMutationMoreRepository(boundary)
        val viewModel = onMain {
            PreferencesViewModel(repository, boundary).also { it.start(context) }
        }
        waitUntil { viewModel.state.value.email == "a@example.com" }

        boundary.replaceCurrent("account-b", accountId = 2)
        onMain {
            viewModel.start(context)
            viewModel.applyPickup(context, "Kingston")
            viewModel.applyCurrency(context, "USD")
        }

        assertEquals(
            "Kingston",
            prefs.getString(PreferencesViewModel.KEY_PICKUP, null),
        )
        assertEquals(
            "USD",
            prefs.getString(PreferencesViewModel.KEY_CURRENCY, null),
        )

        boundary.emitCurrent()
        waitUntil { viewModel.state.value.email == "b@example.com" }
        assertEquals(
            "Montego Bay",
            prefs.getString(PreferencesViewModel.KEY_PICKUP, null),
        )
        assertEquals(
            "JMD",
            prefs.getString(PreferencesViewModel.KEY_CURRENCY, null),
        )
    }

    @Test
    fun oldMoreViewModelCannotDispatchMutationBeforeCollectorObservesReplacement() {
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val repository = GuardedMutationMoreRepository(boundary)
        val viewModel = onMain { MoreViewModel(repository, boundary) }
        waitUntil { viewModel.state.value.name == "Account A" }

        boundary.replaceCurrent("account-b")
        onMain { viewModel.deleteAvatar() }
        assertEquals(0, repository.serverDeleteCalls.get())
        assertEquals(0, repository.deleteAttempts.get())

        boundary.emitCurrent()
        waitUntil { viewModel.state.value.name == "Account B" }
        assertEquals(0, repository.serverDeleteCalls.get())
    }

    @Test
    fun mutationClaimFromAccountAIsRejectedBeforeDispatchAfterAccountBReplacesIt() {
        val boundary = FakeAuthenticatedSessionBoundary("account-a")
        val repository = GuardedMutationMoreRepository(boundary)
        val viewModel = onMain { MoreViewModel(repository, boundary) }
        waitUntil { viewModel.state.value.name == "Account A" }

        onMain { viewModel.deleteAvatar() }
        repository.awaitDeleteClaim()
        boundary.replaceCurrent("account-b")
        repository.releaseDelete()
        waitUntil { repository.deleteAttempts.get() == 1 }

        assertEquals(0, repository.serverDeleteCalls.get())
        boundary.emitCurrent()
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(50)
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate() && System.currentTimeMillis() < deadline) waitForIdle()
        assertEquals("Timed out waiting for asynchronous state", true, predicate())
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value.set(block()) }
        return value.get()
    }
}

private class FakeMoreHubRepository(
    private val firstUser: BlockingResult<MoreUser>,
) : MoreHubRepository {
    val currentUserCalls = AtomicInteger()
    val deleteProfileImageCalls = AtomicInteger()

    override suspend fun currentUser(): Result<MoreUser> =
        if (currentUserCalls.incrementAndGet() == 1) firstUser.awaitResult()
        else Result.success(
            MoreUser(
                firstName = "Account",
                lastName = "B",
                email = "b@example.com",
                pickupLocation = "Montego Bay",
                paymentCurrency = "JMD",
                tierName = "Platinum Priority",
            ),
        )

    override suspend fun airCoinsBalance(): Result<Int> = Result.success(22)
    override suspend fun profileImage(): Result<ProfileAsset> = Result.success(ProfileAsset(null, null))
    override suspend fun updateProfile(
        fields: Map<String, String?>,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<String?> = Result.success("OK")
    override suspend fun uploadProfileImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ) =
        Result.success(ProfileAsset(null, null))
    override suspend fun deleteProfileImage(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> {
        deleteProfileImageCalls.incrementAndGet()
        return Result.success(Unit)
    }
    override suspend fun fetchImage(url: String): Result<ByteArray> = Result.success(byteArrayOf())
}

private class GuardedMutationMoreRepository(
    private val boundary: FakeAuthenticatedSessionBoundary,
) : MoreHubRepository {
    private val deleteClaimed = CountDownLatch(1)
    private val deleteGate = CompletableDeferred<Unit>()
    val deleteAttempts = AtomicInteger()
    val serverDeleteCalls = AtomicInteger()

    override suspend fun currentUser(): Result<MoreUser> = Result.success(
        if (boundary.currentSessionId() == "account-a") {
            MoreUser(
                firstName = "Account",
                lastName = "A",
                email = "a@example.com",
                pickupLocation = "Kingston",
                paymentCurrency = "USD",
            )
        } else {
            MoreUser(
                firstName = "Account",
                lastName = "B",
                email = "b@example.com",
                pickupLocation = "Montego Bay",
                paymentCurrency = "JMD",
            )
        },
    )

    override suspend fun airCoinsBalance(): Result<Int> = Result.success(0)
    override suspend fun profileImage(): Result<ProfileAsset> = Result.success(ProfileAsset(null, null))
    override suspend fun updateProfile(
        fields: Map<String, String?>,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<String?> = Result.success("OK")

    override suspend fun uploadProfileImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<ProfileAsset> = Result.success(ProfileAsset(null, null))

    override suspend fun deleteProfileImage(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> {
        deleteClaimed.countDown()
        deleteGate.await()
        deleteAttempts.incrementAndGet()
        val request = AuthenticatedRequestOwner(
            session = AuthenticatedSessionOwner(expectedSession.sessionId),
            provenance = expectedSession,
        )
        return if (boundary.isCurrent(request)) {
            serverDeleteCalls.incrementAndGet()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("stale session"))
        }
    }

    override suspend fun fetchImage(url: String): Result<ByteArray> = Result.success(byteArrayOf())

    fun awaitDeleteClaim() {
        assertTrue("Timed out waiting for mutation claim", deleteClaimed.await(5, TimeUnit.SECONDS))
    }

    fun releaseDelete() {
        deleteGate.complete(Unit)
    }
}

private class BlockingResult<T> {
    private val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)
    private val result = AtomicReference<Result<T>>()

    suspend fun awaitResult(): Result<T> {
        yield()
        entered.countDown()
        check(released.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release response" }
        return result.get()
    }

    fun awaitEntered() {
        assertTrue("Timed out waiting for request", entered.await(5, TimeUnit.SECONDS))
    }

    fun complete(value: Result<T>) {
        result.set(value)
        released.countDown()
    }
}
