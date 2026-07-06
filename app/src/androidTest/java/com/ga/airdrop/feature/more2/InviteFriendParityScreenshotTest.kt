package com.ga.airdrop.feature.more2

import android.content.Intent
import android.graphics.Bitmap
import android.provider.ContactsContract
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.ReferFriendRequest
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InviteFriendParityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inviteFormMatchesCurrentFigmaLight() {
        setInviteFriend(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("invite-friend-screen").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-contacts-row").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-first-name-input").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-last-name-input").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-email-input").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-email-chevron").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-description-input").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-info-body").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-save").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-history-link").assertIsDisplayed()
        compose.onNodeWithText("View Referral History  \u2192").assertIsDisplayed()
        compose.onNodeWithText("Description").assertIsDisplayed()
        saveRootScreenshot("invite_friend_figma_light.png")
    }

    @Test
    fun historyEntryUsesExistingReferredFriendsRouteCallback() {
        val historyClicks = AtomicInteger()
        setInviteFriend(
            mode = ThemeController.Mode.LIGHT,
            onViewReferralHistory = { historyClicks.incrementAndGet() },
        )

        compose.onNodeWithTag("invite-friend-history-link").performClick()

        compose.runOnIdle {
            assertEquals(
                "History link routes to existing Referred Friends screen",
                1,
                historyClicks.get(),
            )
        }
    }

    @Test
    fun contactsIconUsesSwiftPreferencesDuotoneDark() {
        setInviteFriend(mode = ThemeController.Mode.DARK)

        val bitmap = compose.onNodeWithTag(
            "invite-friend-contacts-icon",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        assertTrue("orange controls in dark mode", bitmap.hasPixelNear(ORANGE))
        assertTrue("white rails in dark mode", bitmap.hasPixelNear(WHITE))
        val infoCard = compose.onNodeWithTag(
            "invite-friend-info-card",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        assertTrue("dark info notice uses Swift cyan wash", infoCard.hasPixelNear(DARK_INFO_NOTICE_BORDER, 36))
        assertFalse("dark info notice must not reuse the light blue fill", infoCard.hasPixelNear(LIGHT_INFO_NOTICE, 12))
        saveRootScreenshot("invite_friend_figma_dark.png")
    }

    @Test
    fun contactsRowLaunchesContactsPickerAndCanUseContactInForm() {
        val api = FakeInviteFriendRepository()
        val launchedIntent = AtomicReference<Intent?>()
        lateinit var viewModel: InviteFriendViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = InviteFriendViewModel(api)
        }
        compose.setContent {
            AirdropTheme {
                InviteFriendScreen(
                    onBack = {},
                    viewModel = viewModel,
                    onContactPickerIntent = { intent ->
                        launchedIntent.set(intent)
                        viewModel.onContactPicked(
                            displayName = "Jordan Marie Smith",
                            email = "jordan@example.com",
                            phone = "+1 876 555 0101",
                        )
                    },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("invite-friend-contacts-row").performClick()

        compose.runOnIdle {
            val intent = launchedIntent.get()
            assertEquals(Intent.ACTION_PICK, intent?.action)
            assertEquals(ContactsContract.Contacts.CONTENT_URI, intent?.data)
        }
        compose.onNodeWithText("Invite Jordan Marie Smith").assertIsDisplayed()
        compose.onNodeWithText("Use in form").performClick()
        compose.onNodeWithText("Jordan").assertIsDisplayed()
        compose.onNodeWithText("Marie Smith").assertIsDisplayed()
        compose.onNodeWithText("jordan@example.com").assertIsDisplayed()
    }

    @Test
    fun smsHandoffShowsResultAfterSuccessfulHandoff() {
        assertSuccessfulExternalHandoff(
            optionText = "Send by text message",
            expectedHandoff = InviteFriendHandoff.Sms,
        )
    }

    @Test
    fun whatsappHandoffShowsResultAfterSuccessfulHandoff() {
        assertSuccessfulExternalHandoff(
            optionText = "Send by WhatsApp",
            expectedHandoff = InviteFriendHandoff.WhatsApp,
        )
    }

    @Test
    fun shareHandoffShowsResultAfterSuccessfulHandoff() {
        assertSuccessfulExternalHandoff(
            optionText = "Share referral link",
            expectedHandoff = InviteFriendHandoff.Share,
        )
    }

    @Test
    fun failedExternalHandoffDoesNotShowFalseSuccess() {
        setInviteFriendWithPickedContact(
            externalInviteHandoff = { _, _, _ -> false },
        )
        compose.onNodeWithText("Share referral link").performClick()
        compose.waitForIdle()
        assertAbsent("Invitation Sent")
    }

    @Test
    fun saveValidatesRequiredFirstNameBeforePosting() {
        val api = FakeInviteFriendRepository()
        setInviteFriend(api = api, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("invite-friend-save").performClick()
        compose.onNodeWithText("First name is required.").assertIsDisplayed()

        compose.runOnIdle {
            assertEquals("Swift validation blocks empty first-name POST", 0, api.referFriendCalls.get())
        }
    }

    @Test
    fun saveValidatesEmailBeforePosting() {
        val api = FakeInviteFriendRepository()
        setInviteFriend(api = api, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("invite-friend-first-name-input").performTextInput("Chase")
        compose.onNodeWithTag("invite-friend-last-name-input").performTextInput("Campbell")
        compose.onNodeWithTag("invite-friend-email-input").performTextInput("bad email")
        compose.onNodeWithTag("invite-friend-save").performClick()
        compose.onNodeWithText("Please enter a valid email address.").assertIsDisplayed()

        compose.runOnIdle {
            assertEquals("Swift validation blocks invalid-email POST", 0, api.referFriendCalls.get())
        }
    }

    @Test
    fun savePostsSwiftPayloadShowsSuccessAndCompletes() {
        val api = FakeInviteFriendRepository()
        val saved = AtomicInteger()
        val viewModel = setInviteFriend(
            api = api,
            mode = ThemeController.Mode.LIGHT,
            onSaved = { saved.incrementAndGet() },
        )

        compose.onNodeWithTag("invite-friend-first-name-input").performTextInput(" Chase ")
        compose.onNodeWithTag("invite-friend-last-name-input").performTextInput(" Campbell ")
        compose.onNodeWithTag("invite-friend-email-input").performTextInput(" chase@example.com ")
        compose.onNodeWithTag("invite-friend-description-input")
            .performScrollTo()
            .performTextInput(" Good friend ")
        compose.onNodeWithTag("invite-friend-save").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            api.referFriendCalls.get() == 1 &&
                api.lastReferFriendRequest.get() != null
        }
        compose.waitForIdle()

        val request = api.lastReferFriendRequest.get()!!
        assertEquals("Chase", request.friendFirstName)
        assertEquals("Campbell", request.friendLastName)
        assertEquals("chase@example.com", request.friendEmail)
        assertEquals("Good friend", request.description)
        compose.onNodeWithText("Invitation Sent").assertIsDisplayed()
        compose.onNodeWithText("Referral sent").assertIsDisplayed()
        compose.runOnIdle {
            val state = viewModel.state.value
            assertEquals("Successful Save clears first name", "", state.firstName)
            assertEquals("Successful Save clears last name", "", state.lastName)
            assertEquals("Successful Save clears email", "", state.email)
            assertEquals("Successful Save clears description", "", state.description)
            assertNull("Successful Save clears stale validation", state.validationError)
            assertNull("Successful Save clears stale API error", state.error)
        }

        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            assertEquals("Success acknowledgement returns to Refer flow", 1, saved.get())
        }
    }

    private fun assertSuccessfulExternalHandoff(
        optionText: String,
        expectedHandoff: InviteFriendHandoff,
    ) {
        val saved = AtomicInteger()
        val handoff = AtomicReference<InviteFriendHandoff?>()
        val sentMessage = AtomicReference<String?>()
        setInviteFriendWithPickedContact(
            onSaved = { saved.incrementAndGet() },
            externalInviteHandoff = { type, contact, message ->
                handoff.set(type)
                sentMessage.set(message)
                contact.displayName == "Jordan Marie Smith"
            },
        )

        compose.onNodeWithText(optionText).performClick()
        compose.onNodeWithText("Invitation Sent").assertIsDisplayed()
        compose.onNodeWithText(
            "Your invitation has been shared successfully. Your friend will receive a message with a unique referral link."
        ).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(expectedHandoff, handoff.get())
            assertTrue(sentMessage.get().orEmpty().contains("https://airdropja.com/refer/AD-2048"))
        }

        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            assertEquals("External handoff acknowledgement returns to Refer flow", 1, saved.get())
        }
    }

    private fun setInviteFriendWithPickedContact(
        onSaved: () -> Unit = {},
        externalInviteHandoff: (InviteFriendHandoff, InviteContact, String) -> Boolean,
    ) {
        lateinit var viewModel: InviteFriendViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = InviteFriendViewModel(FakeInviteFriendRepository())
        }
        compose.setContent {
            AirdropTheme {
                InviteFriendScreen(
                    onBack = {},
                    onSaved = onSaved,
                    viewModel = viewModel,
                    onContactPickerIntent = {
                        viewModel.onContactPicked(
                            displayName = "Jordan Marie Smith",
                            email = "jordan@example.com",
                            phone = "+1 876 555 0101",
                        )
                    },
                    externalInviteHandoff = externalInviteHandoff,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("invite-friend-contacts-row").performClick()
        compose.onNodeWithText("Invite Jordan Marie Smith").assertIsDisplayed()
    }

    private fun setInviteFriend(
        mode: ThemeController.Mode,
        onViewReferralHistory: () -> Unit = {},
    ): InviteFriendViewModel =
        setInviteFriend(
            api = FakeInviteFriendRepository(),
            mode = mode,
            onViewReferralHistory = onViewReferralHistory,
        )

    private fun setInviteFriend(
        api: FakeInviteFriendRepository,
        mode: ThemeController.Mode,
        onSaved: () -> Unit = {},
        onViewReferralHistory: () -> Unit = {},
    ): InviteFriendViewModel {
        lateinit var viewModel: InviteFriendViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = InviteFriendViewModel(api)
        }
        compose.setContent {
            AirdropTheme {
                InviteFriendScreen(
                    onBack = {},
                    onSaved = onSaved,
                    onViewReferralHistory = onViewReferralHistory,
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun assertAbsent(text: String) {
        assertEquals(0, compose.onAllNodesWithText(text).fetchSemanticsNodes().size)
    }

    private fun Bitmap.hasPixelNear(target: Int, tolerance: Int = COLOR_TOLERANCE): Boolean {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) <= tolerance &&
                    kotlin.math.abs(green - targetGreen) <= tolerance &&
                    kotlin.math.abs(blue - targetBlue) <= tolerance
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private class FakeInviteFriendRepository : InviteFriendRepository {
        val referFriendCalls = AtomicInteger()
        val lastReferFriendRequest = AtomicReference<ReferFriendRequest?>()

        override suspend fun currentUser(): Result<AirdropUser> =
            Result.success(AirdropUser(accountNumber = "AD-2048"))

        override suspend fun referFriend(
            firstName: String,
            lastName: String,
            email: String,
            description: String?,
        ): Result<MutationResponse> {
            referFriendCalls.incrementAndGet()
            val body = ReferFriendRequest(
                friendFirstName = firstName,
                friendLastName = lastName,
                friendEmail = email,
                description = description,
            )
            lastReferFriendRequest.set(body)
            return Result.success(MutationResponse(success = true, message = "Referral sent"))
        }
    }

    private companion object {
        const val ORANGE = 0xFFF15114.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DARK_INFO_NOTICE_BORDER = 0xFF1E5872.toInt()
        const val LIGHT_INFO_NOTICE = 0xFFE3ECFF.toInt()
        const val COLOR_TOLERANCE = 18
    }
}
