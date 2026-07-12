package com.ga.airdrop.feature.more2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.ReferFriendRequest
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
        compose.onNodeWithTag("invite-friend-glass-header").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-footer").assertIsDisplayed()
        compose.onNodeWithTag("invite-friend-history-link").assertIsDisplayed()
        compose.onNodeWithText("View Referral History  \u2192").assertIsDisplayed()
        compose.onNodeWithText("Description").assertIsDisplayed()
        val save = compose.onNodeWithTag("invite-friend-save").getUnclippedBoundsInRoot()
        assertEquals("Swift Save button height", 50f, (save.bottom - save.top).value, 1f)
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
    fun contactsSheetRowTapPrefillsForm() {
        val api = FakeInviteFriendRepository()
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
                    contactsPermissionOverride = true,
                    contactsProvider = {
                        listOf(
                            contact("Zara Brown", "zara@example.com", ""),
                            contact("Jordan Marie Smith", "jordan@example.com", "+1 876 555 0101"),
                        )
                    },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("invite-friend-contacts-row").performClick()

        compose.onNodeWithTag("invite-friend-contacts-sheet").assertIsDisplayed()
        saveNodeScreenshot("invite-friend-contacts-sheet", "invite_contacts_sheet_light.png")
        compose.onNodeWithText("Jordan Marie Smith").performClick()
        compose.onNodeWithText("Jordan").assertIsDisplayed()
        compose.onNodeWithText("Marie Smith").assertIsDisplayed()
        compose.onNodeWithText("jordan@example.com").assertIsDisplayed()
    }

    @Test
    fun contactsSheetMatchesSwiftDark() {
        setInviteFriendWithContacts(
            contacts = listOf(
                contact("Jordan Marie Smith", "jordan@example.com", "+1 876 555 0101"),
                contact("No Channel", "", ""),
            ),
            mode = ThemeController.Mode.DARK,
        )
        compose.onNodeWithTag("invite-friend-contacts-row").performClick()
        compose.onNodeWithTag("invite-friend-contacts-sheet").assertIsDisplayed()
        compose.onNodeWithText("Invite your contacts").assertIsDisplayed()
        assertTagAbsent("invite-friend-contact-invite-No Channel")
        saveNodeScreenshot("invite-friend-contacts-sheet", "invite_contacts_sheet_dark.png")
    }

    @Test
    fun emptyContactsStateIsRendered() {
        setInviteFriendWithContacts(contacts = emptyList())
        compose.onNodeWithTag("invite-friend-contacts-row").performClick()
        compose.onNodeWithText("No contacts available.").assertIsDisplayed()
    }

    @Test
    fun deniedContactsStateOpensSettings() {
        val settingsClicks = AtomicInteger()
        setInviteFriendWithContacts(
            contacts = emptyList(),
            permissionGranted = false,
            onOpenSettings = { settingsClicks.incrementAndGet() },
        )
        compose.onNodeWithTag("invite-friend-contacts-row").performClick()
        compose.onNodeWithText("Contacts Permission").assertIsDisplayed()
        compose.onNodeWithText("Open Settings").performClick()
        compose.runOnIdle { assertEquals(1, settingsClicks.get()) }
    }

    @Test
    fun directInviteWithEmailPostsReferral() {
        val emailApi = FakeInviteFriendRepository()
        setInviteFriendWithContacts(
            contacts = listOf(contact("Email Friend", "email@example.com", "")),
            api = emailApi,
        )
        compose.onNodeWithTag("invite-friend-contacts-row").performClick()
        compose.onNodeWithTag("invite-friend-contact-invite-Email Friend").performClick()
        compose.waitUntil(5_000) { emailApi.referFriendCalls.get() == 1 }
        compose.onNodeWithText("Invitation Sent").assertIsDisplayed()
    }

    @Test
    fun directInviteWithPhoneUsesWhatsApp() {
        val handoff = AtomicReference<InviteFriendHandoff?>()
        setInviteFriendWithContacts(
            contacts = listOf(contact("Phone Friend", "", "+1 876 555 0101")),
            externalInviteHandoff = { type, _, _ -> handoff.set(type); true },
        )
        compose.onNodeWithTag("invite-friend-contacts-row").performClick()
        compose.onNodeWithTag("invite-friend-contact-invite-Phone Friend").performClick()
        compose.runOnIdle { assertEquals(InviteFriendHandoff.WhatsApp, handoff.get()) }
    }

    @Test
    fun contactWithoutChannelHasNoInviteAction() {
        val handoff = AtomicReference<InviteFriendHandoff?>()
        setInviteFriendWithContacts(
            contacts = listOf(contact("Share Friend", "", "")),
            externalInviteHandoff = { type, _, _ -> handoff.set(type); true },
        )
        compose.onNodeWithTag("invite-friend-contacts-row").performClick()
        compose.onNodeWithText("Share Friend").assertIsDisplayed()
        assertTagAbsent("invite-friend-contact-invite-Share Friend")
        compose.runOnIdle { assertNull(handoff.get()) }
    }

    @Test
    fun manifestDeclaresReadContactsPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS,
        )
        assertTrue(info.requestedPermissions.orEmpty().contains(android.Manifest.permission.READ_CONTACTS))
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
    fun sharingBlocksUntilAccountReferralCodeLoads() {
        val handoffCalls = AtomicInteger()
        setInviteFriendWithPickedContact(
            api = FakeInviteFriendRepository(accountNumber = null),
            externalInviteHandoff = { _, _, _ ->
                handoffCalls.incrementAndGet()
                true
            },
        )
        compose.onNodeWithText("Share referral link").performClick()
        compose.onNodeWithText(
            "Your referral code is still loading. Please try again in a moment."
        ).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, handoffCalls.get()) }
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
        compose.onNodeWithTag("invite-friend-description-input").performTextInput(" Good friend ")
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
        api: FakeInviteFriendRepository = FakeInviteFriendRepository(),
        externalInviteHandoff: (InviteFriendHandoff, InviteContact, String) -> Boolean,
    ) {
        lateinit var viewModel: InviteFriendViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = InviteFriendViewModel(api)
        }
        compose.setContent {
            AirdropTheme {
                InviteFriendScreen(
                    onBack = {},
                    onSaved = onSaved,
                    viewModel = viewModel,
                    externalInviteHandoff = externalInviteHandoff,
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            viewModel.onContactPicked(
                displayName = "Jordan Marie Smith",
                email = "jordan@example.com",
                phone = "+1 876 555 0101",
            )
        }
        compose.onNodeWithText("Invite Jordan Marie Smith").assertIsDisplayed()
    }

    private fun setInviteFriendWithContacts(
        contacts: List<InviteContact>,
        mode: ThemeController.Mode = ThemeController.Mode.LIGHT,
        permissionGranted: Boolean = true,
        api: FakeInviteFriendRepository = FakeInviteFriendRepository(),
        onOpenSettings: () -> Unit = {},
        externalInviteHandoff: (InviteFriendHandoff, InviteContact, String) -> Boolean = { _, _, _ -> true },
    ) {
        lateinit var viewModel: InviteFriendViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = InviteFriendViewModel(api)
        }
        compose.setContent {
            AirdropTheme {
                InviteFriendScreen(
                    onBack = {},
                    viewModel = viewModel,
                    contactsPermissionOverride = permissionGranted,
                    onRequestContactsPermission = { complete -> complete(false) },
                    contactsProvider = { contacts },
                    onOpenContactsSettings = onOpenSettings,
                    externalInviteHandoff = externalInviteHandoff,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun contact(name: String, email: String, phone: String): InviteContact {
        val parts = name.split(" ")
        return InviteContact(name, parts.first(), parts.drop(1).joinToString(" "), email, phone)
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

    private fun assertTagAbsent(tag: String) {
        assertEquals(0, compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
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
        saveScreenshot(bitmap, filename)
    }

    private fun saveNodeScreenshot(tag: String, filename: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        saveScreenshot(bitmap, filename)
    }

    private fun saveScreenshot(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/refer_invite/"
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(filename, "%kotlin_ui_proof/refer_invite%"),
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private class FakeInviteFriendRepository(
        private val accountNumber: String? = "AD-2048",
    ) : InviteFriendRepository {
        val referFriendCalls = AtomicInteger()
        val lastReferFriendRequest = AtomicReference<ReferFriendRequest?>()

        override suspend fun currentUser(): Result<AirdropUser> =
            Result.success(AirdropUser(accountNumber = accountNumber))

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
