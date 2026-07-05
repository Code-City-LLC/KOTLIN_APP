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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuthorizedUser
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsers
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
class AuthorizedUsersParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val openedDetails = mutableListOf<Int>()
    private var addUserClicks = 0

    @Test
    fun authorizedUsersKeepsSwiftFigmaListAndPullRefreshRailLight() {
        val viewModel = setAuthorizedUsers(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("authorized-users-pull-refresh").assertIsDisplayed()
        assertSwiftFigmaListGeometry()
        assertSwiftFigmaContentAndTaps()
        assertEquals(false, viewModel.state.value.refreshing)
        saveRootScreenshot("authorized_users_swift_light.png")
    }

    @Test
    fun authorizedUsersKeepsSwiftFigmaListAndPullRefreshRailDark() {
        setAuthorizedUsers(mode = ThemeController.Mode.DARK)

        compose.onNodeWithTag("authorized-users-pull-refresh").assertIsDisplayed()
        assertSwiftFigmaListGeometry()
        assertAnyText("Active")
        assertAnyText("Inactive")
        saveRootScreenshot("authorized_users_swift_dark.png")
    }

    @Test
    fun manualRefreshUsesSwiftRefreshControlRepositoryPath() {
        val api = FakeMore2Api()
        lateinit var viewModel: AuthorizedUsersViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = AuthorizedUsersViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                AuthorizedUsersScreen(
                    onBack = {},
                    onAddUser = {},
                    onOpenDetail = {},
                    viewModel = viewModel,
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.authorizedUsersCalls.get() >= 1 && !viewModel.state.value.loading
        }
        val callsBeforeManualRefresh = api.authorizedUsersCalls.get()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.refresh()
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.authorizedUsersCalls.get() >= callsBeforeManualRefresh + 1 &&
                !viewModel.state.value.refreshing
        }
    }

    private fun setAuthorizedUsers(mode: ThemeController.Mode): AuthorizedUsersViewModel {
        openedDetails.clear()
        addUserClicks = 0
        val api = FakeMore2Api()
        lateinit var viewModel: AuthorizedUsersViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = AuthorizedUsersViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                AuthorizedUsersScreen(
                    onBack = {},
                    onAddUser = { addUserClicks += 1 },
                    onOpenDetail = { openedDetails += it },
                    viewModel = viewModel,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            api.authorizedUsersCalls.get() >= 1 && !viewModel.state.value.loading
        }
        return viewModel
    }

    private fun assertSwiftFigmaListGeometry() {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val activeCard = compose.onNodeWithTag("authorized-users-card-101")
            .getUnclippedBoundsInRoot()
        val activeHeader = compose.onNodeWithTag(
            "authorized-users-card-header-101",
            useUnmergedTree = true,
        )
            .getUnclippedBoundsInRoot()

        assertClose(20f, activeCard.left.value, "Authorized Users left gutter")
        assertClose(
            boundsWidth(root) - 40f,
            boundsWidth(activeCard),
            "Authorized Users card width follows Swift/Figma 20dp side gutters",
        )
        assertClose(56f, boundsHeight(activeHeader), "Authorized Users card header height")
    }

    private fun assertSwiftFigmaContentAndTaps() {
        assertAnyText("Chase Camp")
        assertAnyText("ID Type")
        assertAnyText("National ID")
        assertAnyText("Tax Registration Number")
        assertAnyText("Active")

        compose.onNodeWithTag("authorized-users-card-101").performClick()
        compose.runOnIdle {
            assertEquals(listOf(101), openedDetails)
        }

        compose.onNodeWithTag("authorized-users-add-user").performClick()
        compose.runOnIdle {
            assertEquals(1, addUserClicks)
        }
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/authorized_users").also { it.mkdirs() }
    }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/authorized_users")
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

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun assertAnyText(text: String) {
        assertTrue(
            "$text should render in Authorized Users",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private class FakeMore2Api : More2Api {
        val authorizedUsersCalls = AtomicInteger()

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope {
            authorizedUsersCalls.incrementAndGet()
            return AuthorizedUsersEnvelope(
                AuthorizedUsers(
                    active = listOf(sampleUser(101, "Active")),
                    inactive = listOf(sampleUser(202, "Inactive")),
                )
            )
        }

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope = throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun profile(): DataEnvelope<AirdropUser> =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in AuthorizedUsersParityTest")

        private fun sampleUser(id: Int, status: String) = AuthorizedUser(
            id = id,
            firstName = "Chase",
            lastName = "Camp",
            identificationType = "National ID",
            identificationIdNumber = "9849w749r8w04r0",
            email = "Chasec@devcity.com",
            countryCode = "+1",
            mobileNumber = "876 87 548 50",
            trnNumber = "9843759384759378459",
            status = status,
        )
    }
}
