package com.ga.airdrop.feature.homedetails

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.session.*
import com.ga.airdrop.data.model.*
import com.ga.airdrop.data.repo.BusinessRateInquirySender
import com.ga.airdrop.feature.contacts.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusinessInquirySheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightFormConsentAndChatHandoff() = exercise(ThemeController.Mode.LIGHT, "light")
    @Test fun darkFormConsentAndChatHandoff() = exercise(ThemeController.Mode.DARK, "dark")

    @Test fun corporateContactOpensTheBusinessWalkthrough() {
        compose.setContent {
            AirdropThemeProvider {
                GoldPriorityContent(onBack = {}, initialPage = tierPages.indexOfFirst { it.id == "corporate" },
                    resolutionStatus = TierResolutionStatus.Failed)
            }
        }
        compose.onNodeWithTag("gold-priority-cta").assertIsEnabled().performClick()
        compose.onNodeWithTag("business-inquiry-sheet").assertIsDisplayed()
        compose.onNodeWithText("Business", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("business-secondary").performClick()
        compose.onNodeWithTag("business-inquiry-sheet").assertDoesNotExist()
    }

    private fun exercise(theme: ThemeController.Mode, label: String) {
        val customer = AirdropUser(id = 1, firstName = "Jordan", lastName = "Customer", accountNumber = "QA-1")
        val sent = mutableListOf<BusinessRateInquiryRequest>()
        var chatSends = 0
        var destination: String? = null
        val owner = AuthenticatedSessionOwner("qa-session", 1)
        val boundary = object : AuthenticatedSessionBoundary {
            override val changes = MutableStateFlow<AuthenticatedSessionOwner?>(owner)
            override fun capture() = owner
            override fun isCurrent(owner: AuthenticatedSessionOwner) = true
            override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean { action(); return true }
            override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) = action()
            override fun requestOwner(owner: AuthenticatedSessionOwner) =
                AuthenticatedRequestOwner(owner, AuthTokenStore.RequestProvenance(1, owner.sessionId, 1))
            override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = true
        }
        val chat = object : BusinessInquiryChat {
            override suspend fun currentUser() = customer
            override suspend fun startSession(user: AirdropUser) = AutoPilotAppChatSession("qa-chat", null, null, null, emptyList())
            override suspend fun messages(conversationId: String) = emptyList<AutoPilotAppChatMessage>()
            override suspend fun sendMessage(conversationId: String, body: String, user: AirdropUser): AutoPilotAppChatSendResult = error("Use inquiry path")
            override suspend fun sendInquiry(conversationId: String, body: String, user: AirdropUser,
                                             metadata: Map<String, String>): AutoPilotAppChatSendResult {
                chatSends++; return AutoPilotAppChatSendResult("qa-confirmed-chat", null, null)
            }
        }
        lateinit var model: BusinessInquiryViewModel
        compose.runOnUiThread {
            ThemeController.set(theme)
            LiveAgentChatConsentStore.clear(compose.activity)
            model = BusinessInquiryViewModel({ customer }, BusinessRateInquirySender { request, _ ->
                sent += request; Result.success(BusinessRateInquiryResult(123, "created"))
            }, { chat }, boundary)
        }
        compose.setContent {
            AirdropThemeProvider {
                BusinessInquirySheet(onDismiss = {}, onOpenChat = { destination = it }, model = model)
            }
        }
        compose.onNodeWithText("Business", useUnmergedTree = true).assertIsDisplayed()
        capture("$label-overview")
        compose.onNodeWithTag("business-primary").performClick()
        compose.waitUntil { model.state.value.phase == BusinessPhase.Ready }
        compose.onNodeWithTag("business-name").performScrollTo().assertTextContains("Jordan Customer")
        compose.onNodeWithTag("business-pounds").performScrollTo().performTextReplacement("399.9")
        compose.onNodeWithTag("business-primary").performClick()
        compose.onNodeWithTag("business-error").performScrollTo().assertIsDisplayed()
        assertEquals(0, sent.size)
        compose.onNodeWithTag("business-pounds").performScrollTo().performTextReplacement("400")
        compose.onNodeWithTag("business-pounds").performImeAction()
        compose.onNodeWithText("Business rate request").performScrollTo()
        capture("$label-form")
        compose.onNodeWithTag("business-primary").performClick()
        compose.onNodeWithText("Chat with Nirvana").assertIsDisplayed()
        compose.onNodeWithText("Not Now").performClick()
        assertEquals(0, sent.size); assertEquals(0, chatSends)
        compose.onNodeWithTag("business-primary").performClick()
        compose.onNodeWithText("Continue", useUnmergedTree = true).performClick()
        compose.waitUntil { model.state.value.stage == BusinessStage.Success }
        compose.onNodeWithText("Business request sent").assertIsDisplayed()
        capture("$label-success")
        compose.waitUntil(timeoutMillis = 5000) { destination != null }
        assertEquals("qa-confirmed-chat", destination)
        assertEquals(1, sent.size); assertEquals(1, chatSends)
        compose.runOnUiThread { model.close(); LiveAgentChatConsentStore.clear(compose.activity) }
    }

    private fun capture(label: String) {
        compose.waitForIdle()
        // Compose idleness does not wait for the platform dialog/IME surface to draw.
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        android.os.SystemClock.sleep(600)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "business-inquiry-proof").apply { mkdirs() }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(folder, "$label.png")))
    }
}
