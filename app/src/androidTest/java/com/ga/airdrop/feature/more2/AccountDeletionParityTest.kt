package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
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
import com.ga.airdrop.data.model.ShippingRates
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDeletionParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun entryScreenFollowsSwiftOverStaleFigmaAndVerifiesCredentials() {
        val api = FakeMore2Api()
        val verifiedCallbacks = AtomicInteger()

        setAccountDeletion(
            api = api,
            mode = ThemeController.Mode.LIGHT,
            beforeCreate = {
                AccountDeletionFlow.clear()
            },
            onVerified = { verifiedCallbacks.incrementAndGet() },
        )

        compose.onNodeWithText("Account Deletion").assertIsDisplayed()
        compose.onNodeWithText(
            "To delete your account, please confirm by entering your account details. " +
                "This action is permanent and cannot be undone.",
        ).assertIsDisplayed()
        compose.onNodeWithText("We use this information only to verify account ownership")
            .assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsDisplayed()
        assertTextMissing("Why do you want you want to delete your account?")
        assertTextMissing("Delete Account")
        assertSwiftGeometry()

        compose.onNodeWithTag("account-deletion-email-input", useUnmergedTree = true)
            .performTextInput(" kemar@example.com ")
        compose.onNodeWithTag("account-deletion-password-input", useUnmergedTree = true)
            .performTextInput("secret-password")
        compose.onNodeWithTag("account-deletion-confirm", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) { verifiedCallbacks.get() == 1 }
        compose.waitForIdle()

        assertEquals(1, api.loginCalls.get())
        assertEquals(
            LoginRequest(email = "kemar@example.com", password = "secret-password"),
            api.lastLogin.get(),
        )
        assertEquals("kemar@example.com", AccountDeletionFlow.email)
        assertEquals("secret-password", AccountDeletionFlow.password)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AccountDeletionFlow.clear()
        }
    }

    @Test
    fun emptyEmailValidationMatchesSwiftBeforeCallingBackend() {
        val api = FakeMore2Api()

        setAccountDeletion(api, ThemeController.Mode.DARK) {
            AccountDeletionFlow.clear()
        }

        compose.onNodeWithTag("account-deletion-confirm", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Please enter your email address").assertIsDisplayed()

        assertEquals(
            "Swift validates empty email locally before verifyAccountCredentials.",
            0,
            api.loginCalls.get(),
        )
        assertTextMissing("Delete Account")
        assertSwiftGeometry()
    }

    private fun setAccountDeletion(
        api: FakeMore2Api,
        mode: ThemeController.Mode,
        beforeCreate: () -> Unit = {},
        onVerified: () -> Unit = {},
    ) {
        lateinit var viewModel: AccountDeletionViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            beforeCreate()
            viewModel = AccountDeletionViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    AccountDeletionScreen(
                        onBack = {},
                        onVerified = onVerified,
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftGeometry() {
        val root = bounds("account-deletion-root")
        val emailCard = bounds("account-deletion-email-card")
        val passwordCard = bounds("account-deletion-password-card")
        val bottomBar = bounds("account-deletion-bottom-bar")
        val confirm = bounds("account-deletion-confirm")

        assertClose(375f, boundsWidth(root), "Account Deletion frame width")
        assertClose(20f, emailCard.left.value, "Email card left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(emailCard), "Email card width")
        assertClose(50f, boundsHeight(emailCard), "Swift input card height")
        assertClose(50f, boundsHeight(passwordCard), "Swift password card height")
        assertClose(20f, confirm.left.value, "Confirm CTA left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(confirm), "Confirm CTA width")
        assertClose(52f, boundsHeight(confirm), "Swift Confirm CTA height")
        assertTrue(
            "Swift Account Deletion keeps Confirm in the pinned bottom rail.",
            confirm.top.value >= bottomBar.top.value,
        )
    }

    private fun assertTextMissing(text: String) {
        assertTrue(
            "$text should not exist on the Swift credential-entry Account Deletion screen",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private class FakeMore2Api : More2Api {
        val loginCalls = AtomicInteger()
        val lastLogin = AtomicReference<LoginRequest>()

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse {
            loginCalls.incrementAndGet()
            lastLogin.set(body)
            return LoginResponse(token = "verified-token")
        }

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AccountDeletionParityTest")
        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in AccountDeletionParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in AccountDeletionParityTest")
    }
}
