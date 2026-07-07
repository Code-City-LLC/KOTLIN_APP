package com.ga.airdrop.feature.more2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuthorizedUser
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
class AddAuthorizedUserParityTest {

    @get:Rule
    val compose = createComposeRule()

    private var backClicks = 0

    @Test
    fun addFormKeepsSwiftEmailAndFigmaGeometryLight() {
        val api = FakeMore2Api()
        setAddUser(api, editId = null, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithText("Add Authorized User").assertIsDisplayed()
        compose.onNodeWithText("Add User").assertIsDisplayed()
        compose.onNodeWithText("Email Address").assertIsDisplayed()
        compose.onNodeWithText("National ID").assertIsDisplayed()
        assertNoText("Edit User")
        assertNoText("Save Changes")
        assertSwiftAddGeometry()
        saveRootScreenshot("add_authorized_user_swift_light.png")
    }

    @Test
    fun idTypeFieldUsesSwiftPickerSheet() {
        val api = FakeMore2Api()
        setAddUser(api, editId = null, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("add-authorized-user-id-type-card").performClick()

        compose.onNodeWithText("Drivers License").assertIsDisplayed()
        compose.onNodeWithText("Passport").assertIsDisplayed().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Passport").assertIsDisplayed()
    }

    @Test
    fun addModePostsSwiftPayloadAndPops() {
        val api = FakeMore2Api()
        setAddUser(api, editId = null, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("add-authorized-user-first-name-input").performTextInput("Ada")
        compose.onNodeWithTag("add-authorized-user-last-name-input").performTextInput("Lovelace")
        compose.onNodeWithTag("add-authorized-user-id-number-input").performTextInput("ABC123")
        compose.onNodeWithTag("add-authorized-user-email-input").performTextInput("ada@example.com")
        compose.onNodeWithTag("add-authorized-user-mobile-input").performTextInput("+1 876-5290736")
        compose.onNodeWithTag("add-authorized-user-trn-input").performTextInput("123456789")
        compose.onNodeWithTag("add-authorized-user-primary").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            api.addCalls.get() == 1 && backClicks == 1
        }
        val payload = api.lastAddRequest
        assertEquals("Ada", payload?.userFirstName)
        assertEquals("Lovelace", payload?.userLastName)
        assertEquals("National ID", payload?.identificationType)
        assertEquals("ABC123", payload?.identificationIdNumber)
        assertEquals("ada@example.com", payload?.userEmail)
        assertEquals("+1", payload?.userCountryCode)
        assertEquals("8765290736", payload?.userMobileNumber)
        assertEquals("123456789", payload?.trnNo)
    }

    @Test
    fun invalidEmailShowsSwiftValidationAndBlocksAddRequest() {
        val api = FakeMore2Api()
        setAddUser(api, editId = null, mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("add-authorized-user-first-name-input").performTextInput("Ada")
        compose.onNodeWithTag("add-authorized-user-last-name-input").performTextInput("Lovelace")
        compose.onNodeWithTag("add-authorized-user-id-number-input").performTextInput("ABC123")
        compose.onNodeWithTag("add-authorized-user-email-input").performTextInput("bad ada@example.com text")
        compose.onNodeWithTag("add-authorized-user-mobile-input").performTextInput("+1 876-5290736")
        compose.onNodeWithTag("add-authorized-user-trn-input").performTextInput("123456789")
        compose.onNodeWithTag("add-authorized-user-primary").performClick()

        compose.onNodeWithText("Validation Error").assertIsDisplayed()
        compose.onNodeWithText("Please enter a valid Email Address").assertIsDisplayed()
        assertEquals(0, api.addCalls.get())
        assertEquals(0, backClicks)
    }

    @Test
    fun editModePrefillsAndUpdatesLikeSwiftDark() {
        val api = FakeMore2Api()
        setAddUser(api, editId = 101, mode = ThemeController.Mode.DARK)

        compose.waitUntil(timeoutMillis = 5_000) {
            api.authorizedUserCalls.get() == 1
        }
        compose.onNodeWithText("Edit User").assertIsDisplayed()
        compose.onNodeWithText("Save Changes").assertIsDisplayed()
        compose.onNodeWithText("Chase").assertIsDisplayed()
        compose.onNodeWithText("Camp").assertIsDisplayed()
        compose.onNodeWithText("Chasec@devcity.com").assertIsDisplayed()
        assertNoText("Add Authorized User")
        saveRootScreenshot("add_authorized_user_edit_swift_dark.png")

        compose.onNodeWithTag("add-authorized-user-last-name-input").performTextClearance()
        compose.onNodeWithTag("add-authorized-user-last-name-input").performTextInput("Updated")
        compose.onNodeWithTag("add-authorized-user-primary").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            api.updateCalls.get() == 1 && backClicks == 1
        }
        val payload = api.lastUpdateRequest
        assertEquals(101, api.lastUpdateId)
        assertEquals("Chase", payload?.userFirstName)
        assertEquals("Updated", payload?.userLastName)
        assertEquals("National ID", payload?.identificationType)
        assertEquals("9849w749r8w04r0", payload?.identificationIdNumber)
        assertEquals("Chasec@devcity.com", payload?.userEmail)
        assertEquals("+1", payload?.userCountryCode)
        assertEquals("8768754850", payload?.userMobileNumber)
        assertEquals("123456789", payload?.trnNo)
    }

    private fun setAddUser(
        api: FakeMore2Api,
        editId: Int?,
        mode: ThemeController.Mode,
    ): AddAuthorizedUserViewModel {
        backClicks = 0
        lateinit var viewModel: AddAuthorizedUserViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = AddAuthorizedUserViewModel(editId, More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    AddAuthorizedUserScreen(
                        editId = editId,
                        onBack = { backClicks += 1 },
                        viewModel = viewModel,
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            !viewModel.state.value.loadingUser
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun assertSwiftAddGeometry() {
        val root = bounds("add-authorized-user-root")
        val firstCard = bounds("add-authorized-user-first-name-card")
        val lastCard = bounds("add-authorized-user-last-name-card")
        val idCard = bounds("add-authorized-user-id-type-card")
        val primary = bounds("add-authorized-user-primary")

        assertClose(375f, boundsWidth(root), "Add Authorized User frame width")
        assertClose(20f, firstCard.left.value, "First-name left gutter")
        assertClose(12f, lastCard.left.value - firstCard.right.value, "Name row gap")
        assertClose((boundsWidth(root) - 40f - 12f) / 2f, boundsWidth(firstCard), "Name field width")
        assertClose(50f, boundsHeight(firstCard), "First-name field height")
        assertClose(50f, boundsHeight(idCard), "Identity field height")
        assertClose(20f, primary.left.value, "Primary CTA left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(primary), "Primary CTA width")
        assertClose(52f, boundsHeight(primary), "Primary CTA height")
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should not render in this Add Authorized User state",
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

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/add_authorized_user",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/add_authorized_user")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return
        outputStream.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private class FakeMore2Api : More2Api {
        val authorizedUserCalls = AtomicInteger()
        val addCalls = AtomicInteger()
        val updateCalls = AtomicInteger()
        var lastAddRequest: AuthorizedUserRequest? = null
        var lastUpdateRequest: AuthorizedUserRequest? = null
        var lastUpdateId: Int? = null

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope {
            authorizedUserCalls.incrementAndGet()
            return AuthorizedUserEnvelope(sampleUser())
        }

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope {
            addCalls.incrementAndGet()
            lastAddRequest = body
            return AuthorizedUserEnvelope(
                sampleUser(
                    firstName = body.userFirstName,
                    lastName = body.userLastName,
                    email = body.userEmail,
                    countryCode = body.userCountryCode,
                    mobileNumber = body.userMobileNumber,
                    trnNumber = body.trnNo,
                )
            )
        }

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope {
            updateCalls.incrementAndGet()
            lastUpdateId = id
            lastUpdateRequest = body
            return AuthorizedUserEnvelope(
                sampleUser(
                    firstName = body.userFirstName,
                    lastName = body.userLastName,
                    email = body.userEmail,
                    countryCode = body.userCountryCode,
                    mobileNumber = body.userMobileNumber,
                    trnNumber = body.trnNo,
                )
            )
        }

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun profile(): CurrentUserResponse =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in AddAuthorizedUserParityTest")

        private fun sampleUser(
            firstName: String = "Chase",
            lastName: String = "Camp",
            email: String = "Chasec@devcity.com",
            countryCode: String = "+1",
            mobileNumber: String = "876 87 548 50",
            trnNumber: String = "123456789",
        ) = AuthorizedUser(
            id = 101,
            firstName = firstName,
            lastName = lastName,
            identificationType = "National ID",
            identificationIdNumber = "9849w749r8w04r0",
            email = email,
            countryCode = countryCode,
            mobileNumber = mobileNumber,
            trnNumber = trnNumber,
            status = "Active",
        )
    }
}
