package com.ga.airdrop.feature.more2

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsersEnvelope
import com.ga.airdrop.data.model.CurrentUserResponse
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.DeactivateAccountRequest
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.FaqItem
import com.ga.airdrop.data.model.LoginRequest
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.data.model.ReferFriendRequest
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.data.model.ShippingRates
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferAFriendParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heroCarouselUsesBareSwiftIllustrationAssets() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

        assertSwiftHeroAsset(resources, R.drawable.img_more2_refer_friends, 500, 500, "Friends")
        assertSwiftHeroAsset(resources, R.drawable.img_more2_refer_cash, 80, 80, "Cash")
        assertSwiftHeroAsset(resources, R.drawable.img_more2_refer_cap, 500, 500, "Cap")
    }

    @Test
    fun initialEntryMatchesSwiftOneProfileLoadAndOneReferralsLoad() {
        val api = FakeMore2Api()
        lateinit var viewModel: ReferAFriendViewModel
        val inviteClicks = AtomicInteger()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = ReferAFriendViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = { inviteClicks.incrementAndGet() },
                    viewModel = viewModel,
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.profileCalls.get() == 1 &&
                api.referredFriendsCalls.get() == 1 &&
                !viewModel.state.value.loadingReferrals
        }
        compose.waitForIdle()

        compose.onNodeWithTag("refer-hero-carousel").assertIsDisplayed()
        val inviteCardBounds = compose.onNodeWithTag("refer-hero-card-invite")
            .getUnclippedBoundsInRoot()
        assertEquals(
            "Swift active carousel cards are 238dp wide",
            238f,
            boundsWidth(inviteCardBounds),
            1f,
        )
        assertEquals(
            "Swift active carousel cards are 220dp tall",
            220f,
            boundsHeight(inviteCardBounds),
            1f,
        )
        assertEquals(
            "Swift viewDidLoad equivalent fetches profile/account number once",
            1,
            api.profileCalls.get(),
        )
        assertEquals(
            "Swift viewWillAppear equivalent loads referred friends once on entry",
            1,
            api.referredFriendsCalls.get(),
        )
        assertEquals(
            "https://airdropja.com/refer/GA-4242",
            viewModel.state.value.referralLink,
        )
        assertTrue(
            "Rendered referral card should use the account-number link",
            compose.onAllNodesWithText("https://airdropja.com/refer/GA-4242")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithText("Earn AirCoins for every friend you invite").assertIsDisplayed()
        compose.onNodeWithText("Your Referral Link").assertIsDisplayed()
        compose.onNodeWithText("Invite Friends").assertIsDisplayed()
        compose.onNodeWithText("Your Referrals").assertIsDisplayed()
        assertEquals(
            "Stale Figma $2 copy must not replace Swift AirCoins copy",
            0,
            compose.onAllNodesWithText("Earn $2 USD Per Invite").fetchSemanticsNodes().size,
        )

        compose.onNodeWithTag("refer-copy-button").performClick()
        compose.onNodeWithTag("refer-link-copied-toast").assertIsDisplayed()

        compose.onNodeWithTag("refer-invite-button").performClick()
        compose.runOnIdle {
            assertEquals("Invite Friends button should open Swift Invite flow", 1, inviteClicks.get())
        }
        saveRootScreenshot("refer_friend_swift_precedence_light.png")
    }

    @Test
    fun backendReferralRowsRenderInsteadOfFakeStaticEmptyPage() {
        val api = FakeMore2Api(
            referrals = listOf(
                ReferredFriend(
                    id = 42,
                    friendFirstName = "Chase",
                    friendLastName = "Campbell",
                    friendEmail = "chase@example.com",
                    status = 1,
                    statusText = "Completed",
                ),
                ReferredFriend(
                    id = 43,
                    friendFirstName = "Morgan",
                    friendLastName = "Lee",
                    friendEmail = "morgan@example.com",
                    status = 2,
                    statusText = null,
                ),
                ReferredFriend(
                    id = 44,
                    friendName = "Taylor Morgan",
                    friendEmail = "taylor@example.com",
                    status = 0,
                    statusText = null,
                ),
            ),
        )
        lateinit var viewModel: ReferAFriendViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = ReferAFriendViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = {},
                    viewModel = viewModel,
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.referredFriendsCalls.get() == 1 &&
                !viewModel.state.value.loadingReferrals &&
                viewModel.state.value.referrals.size == 3
        }
        compose.waitForIdle()

        assertEquals("Swift asks the referred-friends endpoint for limit 20", 20, api.lastLimit.get())
        assertEquals(
            "Fake/static empty copy must disappear when backend rows exist",
            0,
            compose.onAllNodesWithText("referred anyone", substring = true)
                .fetchSemanticsNodes().size,
        )
        compose.onNodeWithText("Chase Campbell").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("chase@example.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Morgan Lee").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("morgan@example.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancelled").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Taylor Morgan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("taylor@example.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pending").performScrollTo().assertIsDisplayed()
        saveRootScreenshot("refer_friend_backend_rows_light.png")
    }

    @Test
    fun inviteCompletionRefreshesReferredFriendsOnce() {
        val api = FakeMore2Api()
        lateinit var viewModel: ReferAFriendViewModel
        lateinit var triggerRefresh: () -> Unit
        var consumed = 0

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = ReferAFriendViewModel(More2Repository(api))
        }

        compose.setContent {
            var refreshAfterInvite by remember { mutableStateOf(false) }
            triggerRefresh = { refreshAfterInvite = true }
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = {},
                    refreshAfterInvite = refreshAfterInvite,
                    onRefreshAfterInviteConsumed = {
                        consumed += 1
                        refreshAfterInvite = false
                    },
                    viewModel = viewModel,
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.referredFriendsCalls.get() == 1 && !viewModel.state.value.loadingReferrals
        }
        compose.waitForIdle()

        compose.runOnIdle { triggerRefresh() }
        compose.waitUntil(timeoutMillis = 5_000) {
            api.referredFriendsCalls.get() == 2 &&
                consumed == 1 &&
                !viewModel.state.value.loadingReferrals
        }
        compose.waitForIdle()

        assertEquals(
            "Resetting the Invite completion flag must not trigger a second reload",
            2,
            api.referredFriendsCalls.get(),
        )
    }

    private class FakeMore2Api(
        private val referrals: List<ReferredFriend> = emptyList(),
    ) : More2Api {
        val profileCalls = AtomicInteger()
        val referredFriendsCalls = AtomicInteger()
        val lastLimit = AtomicInteger()

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> {
            referredFriendsCalls.incrementAndGet()
            lastLimit.set(limit)
            return Paginated(referrals)
        }

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun profile(): CurrentUserResponse {
            profileCalls.incrementAndGet()
            return CurrentUserResponse(user = AirdropUser(accountNumber = "GA-4242"))
        }

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in ReferAFriendParityTest")
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertSwiftHeroAsset(
        resources: Resources,
        resId: Int,
        expectedWidth: Int,
        expectedHeight: Int,
        label: String,
    ) {
        val bitmap = BitmapFactory.decodeResource(resources, resId)
        assertEquals("$label Swift asset width", expectedWidth, bitmap.width)
        assertEquals("$label Swift asset height", expectedHeight, bitmap.height)
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }
}
