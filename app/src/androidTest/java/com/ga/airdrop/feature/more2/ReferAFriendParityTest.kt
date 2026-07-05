package com.ga.airdrop.feature.more2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsersEnvelope
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
    fun initialEntryMatchesSwiftOneProfileLoadAndOneReferralsLoad() {
        val api = FakeMore2Api()
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
            api.profileCalls.get() == 1 &&
                api.referredFriendsCalls.get() == 1 &&
                !viewModel.state.value.loadingReferrals
        }
        compose.waitForIdle()

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
    }

    private class FakeMore2Api : More2Api {
        val profileCalls = AtomicInteger()
        val referredFriendsCalls = AtomicInteger()

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
            return Paginated(emptyList())
        }

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in ReferAFriendParityTest")

        override suspend fun profile(): DataEnvelope<AirdropUser> {
            profileCalls.incrementAndGet()
            return DataEnvelope(data = AirdropUser(accountNumber = "GA-4242"))
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
}
