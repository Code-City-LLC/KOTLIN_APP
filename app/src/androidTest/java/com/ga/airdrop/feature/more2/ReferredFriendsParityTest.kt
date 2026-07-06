package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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

    @Test
    fun historyLoadsWithSwiftLimit200() {
        val repository = FakeReferRepository(
            referrals = listOf(sampleFriend(id = 12)),
        )
        val viewModel = setReferredFriends(repository)

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.referredFriendsCalls.get() == 1 && !viewModel.state.value.loading
        }
        compose.runOnIdle {
            assertEquals(200, repository.lastLimit)
            assertEquals(listOf("maya@example.com"), viewModel.state.value.referrals.map { it.friendEmail })
        }
    }

    @Test
    fun historyEmptyStateUsesDedicatedReferredFriendsScreen() {
        setReferredFriends(FakeReferRepository(referrals = emptyList()))

        compose.onNodeWithTag("referred-friends-screen").assertIsDisplayed()
        compose.onNodeWithText("Referred Friends").assertIsDisplayed()
        compose.onNodeWithText("You haven't referred any friends yet.").assertIsDisplayed()
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

    private fun setReferredFriends(repository: FakeReferRepository): ReferredFriendsViewModel {
        lateinit var viewModel: ReferredFriendsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = ReferredFriendsViewModel(repository)
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
        compose.waitUntil(timeoutMillis = 5_000) {
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

        override suspend fun referredFriends(limit: Int): Result<List<ReferredFriend>> {
            referredFriendsCalls.incrementAndGet()
            lastLimit = limit
            return Result.success(referrals)
        }
    }
}
