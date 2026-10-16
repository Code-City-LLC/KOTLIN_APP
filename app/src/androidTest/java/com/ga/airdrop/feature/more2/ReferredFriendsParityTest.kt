package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performScrollToIndex
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.*
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Pagination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import androidx.test.uiautomator.UiDevice
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.ReferredFriend
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferredFriendsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private var originalTheme = ThemeController.Mode.SYSTEM
    @Before fun saveTheme() { originalTheme = ThemeController.mode }
    @After fun restoreTheme() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { ThemeController.set(originalTheme) }
    }

    @Test
    fun historyLoadsWithCanonicalPageTwenty() {
        val repository = FakeReferRepository(
            referrals = listOf(sampleFriend(id = 12)),
        )
        val viewModel = setReferredFriends(repository)

        compose.waitUntil(timeoutMillis = 20_000) {
            repository.referredFriendsCalls.get() == 1 && !viewModel.state.value.loading
        }
        compose.runOnIdle {
            assertEquals(20, repository.lastLimit)
            assertEquals(1, repository.pages.single())
            assertEquals(listOf("maya@example.com"), viewModel.state.value.referrals.map { it.friendEmail })
        }
    }

    @Test
    fun historyEmptyStateUsesDedicatedReferredFriendsScreen() {
        setReferredFriends(FakeReferRepository(referrals = emptyList()))

        compose.onNodeWithTag("referred-friends-screen").assertIsDisplayed()
        compose.onNodeWithText("Referred Friends").assertIsDisplayed()
        compose.onNodeWithText("You haven't referred any friends yet.").assertIsDisplayed()
        capture("light-empty")
    }

    @Test fun darkHistoryEmptyState() {
        setReferredFriends(FakeReferRepository(emptyList()), dark = true)
        compose.onNodeWithTag("referred-friends-empty").assertIsDisplayed()
        capture("dark-empty")
    }

    @Test
    fun historyRowsRenderSwiftFields() {
        setReferredFriends(
            FakeReferRepository(
                referrals = listOf(
                    sampleFriend(
                        id = 24,
                        firstName = "Maya",
                        lastName = "Lee",
                        email = "maya@example.com",
                        date = "2024-12-15T09:30:00Z",
                        status = 1,
                        statusText = "Completed",
                    ),
                ),
            ),
        )

        compose.onNodeWithTag("referred-friends-row-24").assertIsDisplayed()
        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.onNodeWithText("Maya Lee").assertIsDisplayed()
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("maya@example.com").assertIsDisplayed()
        compose.onNodeWithText("Refer Date").assertIsDisplayed()
        compose.onNodeWithText("Dec 15, 2024").assertIsDisplayed()
        compose.onNodeWithText("Status").assertIsDisplayed()
        compose.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test fun lightPagingRetainsRowsAndRetriesWithoutLoop() = exercisePaging(false)
    @Test fun darkPagingRetainsRowsAndRetriesWithoutLoop() = exercisePaging(true)

    private fun exercisePaging(dark: Boolean) {
        val next = CompletableDeferred<Result<Paginated<ReferredFriend>>>()
        val repo = FakeReferRepository(emptyList())
        repo.responder = { page ->
            if (page == 1) Result.success(Paginated((1..20).map { sampleFriend(it) }, Pagination(currentPage=1,lastPage=2)))
            else next.await()
        }
        val vm = setReferredFriends(repo, dark)
        val label = if (dark) "dark" else "light"
        capture("$label-populated")
        compose.onNodeWithTag("referred-friends-list").performScrollToIndex(19)
        compose.waitUntil { vm.state.value.loadingMore }
        capture("$label-loading-more")
        compose.runOnIdle { next.complete(Result.failure(IllegalStateException("offline"))) }
        compose.waitUntil { vm.state.value.loadMoreError }
        compose.onNodeWithTag("referred-friends-list").performScrollToIndex(20)
        compose.onNodeWithTag("referred-friends-more-retry").assertIsDisplayed()
        capture("$label-more-error")
        compose.runOnIdle {
            assertEquals(listOf(1,2), repo.pages)
            repo.responder = { page -> Result.success(Paginated(listOf(sampleFriend(21)), Pagination(currentPage=page,lastPage=2))) }
        }
        compose.onNodeWithTag("referred-friends-more-retry").performClick()
        compose.waitUntil { vm.state.value.referrals.size == 21 }
        compose.onNodeWithTag("referred-friends-list").performScrollToIndex(20)
        compose.onNodeWithTag("referred-friends-row-21").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(1,2,2), repo.pages) }
    }

    @Test fun initialAndRefreshFailureHaveRetryAndPreserveHistory() = exerciseRefresh(false)
    @Test fun darkInitialAndRefreshFailureHaveRetryAndPreserveHistory() = exerciseRefresh(true)

    private fun exerciseRefresh(dark: Boolean) {
        val label = if (dark) "dark" else "light"
        val repo = FakeReferRepository(emptyList())
        repo.responder = { Result.failure(IllegalStateException("offline")) }
        val vm = setReferredFriends(repo, dark)
        compose.onNodeWithTag("referred-friends-load-failed").assertIsDisplayed()
        compose.onNodeWithTag("referred-friends-empty").assertDoesNotExist()
        capture("$label-initial-error")
        compose.runOnIdle { repo.responder = { Result.success(Paginated(listOf(sampleFriend(1)))) } }
        compose.onNodeWithTag("referred-friends-retry").performClick()
        compose.waitUntil { vm.state.value.referrals.size == 1 && !vm.state.value.loading }
        val refresh = CompletableDeferred<Result<Paginated<ReferredFriend>>>()
        compose.runOnIdle { repo.responder = { refresh.await() } }
        compose.onNodeWithTag("referred-friends-refresh").performTouchInput { swipeDown() }
        compose.waitUntil { vm.state.value.loading }
        compose.onNodeWithTag("referred-friends-row-1").assertIsDisplayed()
        capture("$label-refreshing")
        compose.runOnIdle { refresh.complete(Result.failure(IllegalStateException("offline"))) }
        compose.waitUntil { !vm.state.value.loading }
        compose.onNodeWithTag("referred-friends-refresh-retry").assertIsDisplayed()
        compose.onNodeWithTag("referred-friends-row-1").assertIsDisplayed()
        capture("$label-refresh-error")
    }

    private fun capture(label: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(600)
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "referral-proof").apply { mkdirs() }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(folder, "$label.png")))
    }

    private class TestBoundary : AuthenticatedSessionBoundary {
        private val owner = AuthenticatedSessionOwner("referral-test", 1)
        override val changes = MutableStateFlow<AuthenticatedSessionOwner?>(owner)
        override fun capture() = owner
        override fun isCurrent(owner: AuthenticatedSessionOwner) = owner == this.owner
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean { if (!isCurrent(owner)) return false; action(); return true }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) = isCurrent(owner) && action()
        override fun requestOwner(owner: AuthenticatedSessionOwner) = AuthenticatedRequestOwner(owner, AuthTokenStore.RequestProvenance(1,owner.sessionId,1))
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = isCurrent(owner)
    }

    private fun setReferredFriends(repository: FakeReferRepository, dark: Boolean = false): ReferredFriendsViewModel {
        lateinit var viewModel: ReferredFriendsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(if (dark) ThemeController.Mode.DARK else ThemeController.Mode.LIGHT)
            viewModel = ReferredFriendsViewModel(repository, TestBoundary())
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AirdropTheme.colors.gray150),
                ) {
                    ReferredFriendsScreen(
                        onBack = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            repository.referredFriendsCalls.get() == 1 && !viewModel.state.value.loading
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun sampleFriend(
        id: Int,
        firstName: String = "Maya",
        lastName: String = "Lee",
        email: String = "maya@example.com",
        date: String = "2024-12-15",
        status: Int = 1,
        statusText: String = "Completed",
    ): ReferredFriend =
        ReferredFriend(
            id = id,
            friendFirstName = firstName,
            friendLastName = lastName,
            friendEmail = email,
            referDate = date,
            status = status,
            statusText = statusText,
        )

    private class FakeReferRepository(
        private val referrals: List<ReferredFriend>,
    ) : ReferAFriendRepository {
        val referredFriendsCalls = AtomicInteger()
        var lastLimit: Int? = null

        override suspend fun currentUser(): Result<AirdropUser> =
            Result.success(AirdropUser(accountNumber = "AD-2048"))

        val pages = mutableListOf<Int>()
        var responder: suspend (Int) -> Result<Paginated<ReferredFriend>> = { page ->
            Result.success(Paginated(referrals, Pagination(currentPage = page, lastPage = 1)))
        }
        override suspend fun referredFriends(expectedSession: AuthTokenStore.RequestProvenance, page: Int, limit: Int): Result<Paginated<ReferredFriend>> {
            referredFriendsCalls.incrementAndGet()
            pages += page
            lastLimit = limit
            return responder(page)
        }
    }
}
