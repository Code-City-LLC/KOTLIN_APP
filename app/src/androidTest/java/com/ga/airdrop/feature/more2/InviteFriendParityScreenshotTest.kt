package com.ga.airdrop.feature.more2

import android.content.Intent
import android.graphics.Bitmap
import android.provider.ContactsContract
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
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
import java.util.concurrent.atomic.AtomicReference
import okhttp3.ResponseBody
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
    fun contactsIconUsesSwiftDuotoneLight() {
        setInviteFriend(mode = ThemeController.Mode.LIGHT)

        assertContactsRowGeometry()
        assertIconContainsColor(ORANGE, "orange signal arcs in light mode")
        assertIconContainsColor(DARK_HANDSET, "dark handset in light mode")
        saveRootScreenshot("invite_friend_contacts_icon_light.png")
    }

    @Test
    fun contactsIconUsesSwiftDuotoneDark() {
        setInviteFriend(mode = ThemeController.Mode.DARK)

        assertContactsRowGeometry()
        assertIconContainsColor(ORANGE, "orange signal arcs in dark mode")
        assertIconContainsColor(WHITE_HANDSET, "white handset in app dark mode")
        saveRootScreenshot("invite_friend_contacts_icon_dark.png")
    }

    @Test
    fun infoCardBodyUsesSwiftTextDarkTitleLight() {
        setInviteFriend(mode = ThemeController.Mode.LIGHT)

        assertInfoBodyContainsColor(DARK_HANDSET, "Swift textDarkTitle info body in light mode")
        assertInfoBodyDoesNotContainColor(BLUE_MAIN, "Info body must not use BlueMain in light mode")
    }

    @Test
    fun infoCardBodyUsesSwiftTextDarkTitleDark() {
        setInviteFriend(mode = ThemeController.Mode.DARK)

        assertInfoBodyContainsColor(WHITE_HANDSET, "Swift textDarkTitle info body in app dark mode")
        assertInfoBodyDoesNotContainColor(BLUE_MAIN, "Info body must not use BlueMain in dark mode")
    }

    @Test
    fun contactsRowLaunchesEmailPickerAndPrefillsReturnedContact() {
        val api = FakeMore2Api()
        val launchedIntent = AtomicReference<Intent?>()
        lateinit var viewModel: InviteFriendViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = InviteFriendViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                InviteFriendScreen(
                    onBack = {},
                    viewModel = viewModel,
                    onContactPickerIntent = { intent ->
                        launchedIntent.set(intent)
                        viewModel.prefillContact(
                            displayName = "Jordan Marie Smith",
                            email = "jordan@example.com",
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
            assertEquals(ContactsContract.CommonDataKinds.Email.CONTENT_URI, intent?.data)
        }
        compose.onNodeWithText("Jordan").assertIsDisplayed()
        compose.onNodeWithText("Marie Smith").assertIsDisplayed()
        compose.onNodeWithText("jordan@example.com").assertIsDisplayed()
    }

    @Test
    fun saveValidatesRequiredFirstNameBeforePosting() {
        val api = FakeMore2Api()
        setInviteFriend(api = api, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("invite-friend-save").performClick()
        compose.onNodeWithText("First name is required.").assertIsDisplayed()

        compose.runOnIdle {
            assertEquals("Swift validation must block an empty first-name POST", 0, api.referFriendCalls.get())
        }
    }

    @Test
    fun saveValidatesEmailBeforePosting() {
        val api = FakeMore2Api()
        setInviteFriend(api = api, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("invite-friend-first-name-input").performTextInput("Chase")
        compose.onNodeWithTag("invite-friend-last-name-input").performTextInput("Campbell")
        compose.onNodeWithTag("invite-friend-email-input").performTextInput("bad email")
        compose.onNodeWithTag("invite-friend-save").performClick()
        compose.onNodeWithText("Please enter a valid email address.").assertIsDisplayed()

        compose.runOnIdle {
            assertEquals("Swift validation must block an invalid-email POST", 0, api.referFriendCalls.get())
        }
    }

    @Test
    fun savePostsSwiftPayloadShowsSuccessAndCompletes() {
        val api = FakeMore2Api()
        val saved = AtomicInteger()
        setInviteFriend(api = api, mode = ThemeController.Mode.LIGHT) {
            saved.incrementAndGet()
        }

        compose.onNodeWithTag("invite-friend-first-name-input").performTextInput(" Chase ")
        compose.onNodeWithTag("invite-friend-last-name-input").performTextInput(" Campbell ")
        compose.onNodeWithTag("invite-friend-email-input").performTextInput(" chase@example.com ")
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
        assertNull("Swift omits a blank description from the POST body", request.description)
        compose.onNodeWithText("Invitation Sent").assertIsDisplayed()
        compose.onNodeWithText("Referral sent").assertIsDisplayed()

        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            assertEquals("Success acknowledgement should return to the Refer flow", 1, saved.get())
        }
    }

    private fun setInviteFriend(mode: ThemeController.Mode) {
        setInviteFriend(api = null, mode = mode)
    }

    private fun setInviteFriend(
        api: FakeMore2Api?,
        mode: ThemeController.Mode,
        onSaved: () -> Unit = {},
    ) {
        lateinit var viewModel: InviteFriendViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            if (api != null) {
                viewModel = InviteFriendViewModel(More2Repository(api))
            }
        }
        compose.setContent {
            AirdropTheme {
                if (api == null) {
                    InviteFriendScreen(onBack = {})
                } else {
                    InviteFriendScreen(
                        onBack = {},
                        onSaved = onSaved,
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertContactsRowGeometry() {
        val row = compose.onNodeWithTag("invite-friend-contacts-row")
            .getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithTag(
            "invite-friend-contacts-icon",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        assertClose(59f, boundsHeight(row), "Contacts row height")
        assertClose(24f, boundsWidth(icon), "Contacts icon width")
        assertClose(24f, boundsHeight(icon), "Contacts icon height")
    }

    private fun assertIconContainsColor(target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(
            "invite-friend-contacts-icon",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()

        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun assertInfoBodyContainsColor(target: Int, label: String) {
        val bitmap = captureInfoBody()

        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun assertInfoBodyDoesNotContainColor(target: Int, label: String) {
        val bitmap = captureInfoBody()

        assertFalse(label, bitmap.hasPixelNear(target))
    }

    private fun captureInfoBody(): Bitmap {
        val node = compose.onNodeWithTag("invite-friend-info-body", useUnmergedTree = true)
        node.performScrollTo()
        compose.waitForIdle()
        return node.captureToImage().asAndroidBitmap()
    }

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
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
                    kotlin.math.abs(red - targetRed) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(green - targetGreen) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(blue - targetBlue) <= COLOR_TOLERANCE
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

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private class FakeMore2Api : More2Api {
        val referFriendCalls = AtomicInteger()
        val lastReferFriendRequest = AtomicReference<ReferFriendRequest?>()

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse {
            referFriendCalls.incrementAndGet()
            lastReferFriendRequest.set(body)
            return MutationResponse(success = true, message = "Referral sent")
        }

        override suspend fun profile(): CurrentUserResponse =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in InviteFriendParityScreenshotTest")
    }

    private companion object {
        const val ORANGE = 0xFFF15114.toInt()
        const val DARK_HANDSET = 0xFF292929.toInt()
        const val WHITE_HANDSET = 0xFFFFFFFF.toInt()
        const val BLUE_MAIN = 0xFF2A2367.toInt()
        const val COLOR_TOLERANCE = 18
    }
}
