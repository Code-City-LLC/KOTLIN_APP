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
class AuthorizedUserDetailParityTest {

    @get:Rule
    val compose = createComposeRule()

    private var backClicks = 0

    @Test
    fun detailLoadsOnceAndKeepsSwiftReadOnlyHeaderLight() {
        val api = FakeMore2Api(status = "Active")
        setDetail(api, ThemeController.Mode.LIGHT)

        assertEquals(
            "Swift FigmaAuthorizedUserDetailViewController.viewDidLoad calls loadUser once",
            1,
            api.authorizedUserCalls.get(),
        )
        compose.onNodeWithText("User Details").assertIsDisplayed()
        compose.onNodeWithText("Chase Camp").assertIsDisplayed()
        compose.onNodeWithText("Email Address").assertIsDisplayed()
        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Deactivate User").assertIsDisplayed()
        compose.onNodeWithText("Delete User").assertIsDisplayed()
        assertNoText("Edit User")
        assertSwiftDetailGeometry()
        saveRootScreenshot("authorized_user_detail_swift_light.png")
    }

    @Test
    fun detailActionRailsRefreshAndDeleteLikeSwiftDark() {
        val api = FakeMore2Api(status = "Active")
        setDetail(api, ThemeController.Mode.DARK)

        compose.onNodeWithText("Deactivate User").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            api.deactivateCalls.get() == 1 &&
                api.authorizedUserCalls.get() == 2 &&
                api.status == "Inactive"
        }
        compose.onNodeWithText("Activate User").assertIsDisplayed()

        compose.onNodeWithText("Activate User").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            api.activateCalls.get() == 1 &&
                api.authorizedUserCalls.get() == 3 &&
                api.status == "Active"
        }
        compose.onNodeWithText("Deactivate User").assertIsDisplayed()

        compose.onNodeWithText("Delete User").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            api.deleteCalls.get() == 1 && backClicks == 1
        }
        saveRootScreenshot("authorized_user_detail_swift_dark.png")
    }

    private fun setDetail(
        api: FakeMore2Api,
        mode: ThemeController.Mode,
    ): AuthorizedUserDetailViewModel {
        backClicks = 0
        lateinit var viewModel: AuthorizedUserDetailViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = AuthorizedUserDetailViewModel(101, More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    AuthorizedUserDetailScreen(
                        userId = 101,
                        onBack = { backClicks += 1 },
                        viewModel = viewModel,
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.authorizedUserCalls.get() >= 1 &&
                !viewModel.state.value.loading &&
                viewModel.state.value.user != null
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun assertSwiftDetailGeometry() {
        val root = bounds("authorized-user-detail-root")
        val card = bounds("authorized-user-detail-card")
        val primary = bounds("authorized-user-detail-primary")
        val delete = bounds("authorized-user-detail-delete")

        assertClose(375f, boundsWidth(root), "Authorized User Detail frame width")
        assertClose(20f, card.left.value, "Detail card left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(card), "Detail card width")
        assertClose(52f, boundsHeight(primary), "Primary action height")
        assertClose(52f, boundsHeight(delete), "Delete action height")
        assertClose(10f, delete.top.value - primary.bottom.value, "Action button gap")
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should not render in the Swift read-only Authorized User Detail flow",
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
            "screenshots/authorized_user_detail",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/authorized_user_detail")
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

    private class FakeMore2Api(status: String) : More2Api {
        val authorizedUserCalls = AtomicInteger()
        val activateCalls = AtomicInteger()
        val deactivateCalls = AtomicInteger()
        val deleteCalls = AtomicInteger()
        var status = status

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope {
            authorizedUserCalls.incrementAndGet()
            return AuthorizedUserEnvelope(sampleUser(status))
        }

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse {
            activateCalls.incrementAndGet()
            status = "Active"
            return MutationResponse(success = true)
        }

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse {
            deactivateCalls.incrementAndGet()
            status = "Inactive"
            return MutationResponse(success = true)
        }

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse {
            deleteCalls.incrementAndGet()
            return MutationResponse(success = true)
        }

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun profile(): CurrentUserResponse =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in AuthorizedUserDetailParityTest")

        private fun sampleUser(status: String) = AuthorizedUser(
            id = 101,
            firstName = "Chase",
            lastName = "Camp",
            identificationType = "National ID",
            identificationIdNumber = "9849w749r8w04r0",
            email = "Chasec@devcity.com",
            countryCode = "+1",
            mobileNumber = "876 87 548 50",
            trnNumber = "123456789",
            status = status,
            activeTimes = if (status == "Inactive") 2 else null,
        )
    }
}
