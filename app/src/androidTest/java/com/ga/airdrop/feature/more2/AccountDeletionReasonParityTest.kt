package com.ga.airdrop.feature.more2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.session.SessionStore
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
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.more.BackgroundStore
import com.ga.airdrop.feature.shop.ShopCheckoutStore
import com.ga.airdrop.feature.shop.ShopProduct
import com.ga.airdrop.feature.shop.ShopProductHandoffStore
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDeletionReasonParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirmationSheetFollowsSwiftRuntimeWithoutFigmaGrabHandle() {
        val viewModel = setReasonScreen(verifiedCredentials = true)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.selectReason("Other")
            viewModel.requestDelete()
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("account-deletion-confirm-sheet", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val sheet = compose.onNodeWithTag("account-deletion-confirm-sheet", useUnmergedTree = true)
        val warning = compose.onNodeWithTag("account-deletion-confirm-warning", useUnmergedTree = true)
        val sheetBounds = sheet.getUnclippedBoundsInRoot()
        val warningBounds = warning.getUnclippedBoundsInRoot()

        assertEquals(
            "Swift confirmation sheet starts the warning graphic 28dp below the sheet top",
            28f,
            (warningBounds.top - sheetBounds.top).value,
            0.75f,
        )
        assertNoFigmaHandleInTopBand(sheet.captureToImage().asAndroidBitmap())
        saveSheetScreenshot("account_deletion_reason_confirm_swift_light.png")
    }

    @Test
    fun directReasonRouteWithoutVerifiedPasswordReturnsToVerification() {
        val api = FakeMore2Api()
        lateinit var viewModel: AccountDeletionReasonViewModel
        val missingVerificationCallbacks = AtomicInteger()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            AccountDeletionFlow.clear()
            viewModel = AccountDeletionReasonViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                AccountDeletionReasonScreen(
                    onBack = {},
                    onDeleted = {},
                    onVerificationMissing = { missingVerificationCallbacks.incrementAndGet() },
                    viewModel = viewModel,
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            missingVerificationCallbacks.get() == 1
        }
        compose.waitForIdle()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.selectReason("Other")
            viewModel.requestDelete()
            viewModel.confirmDelete(InstrumentationRegistry.getInstrumentation().targetContext)
        }
        compose.waitForIdle()

        assertEquals(
            "Direct/process-restored reason route must return to credential verification once",
            1,
            missingVerificationCallbacks.get(),
        )
        assertEquals(
            "Swift initializer parity: deletion must not POST without verified password handoff",
            0,
            api.deactivateCalls.get(),
        )
        assertEquals(
            "Please verify your email and password again to delete your account.",
            viewModel.state.value.error,
        )
    }

    @Test
    fun successfulDeletionClearsSwiftLogoutLocalState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = FakeMore2Api()
        lateinit var viewModel: AccountDeletionReasonViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            AuthTokenStore.init(context)
            AuthTokenStore.save("token-before-delete")
            SessionStore.update {
                it.copy(greeting = "Hi", firstName = "Kemar", airCoins = "42", cartCount = 1)
            }
            CartStore.init(context)
            CartStore.clear()
            CartStore.add(CartStore.CartLine(id = 9001, title = "Seeded cart line"))
            ShopCheckoutStore.product = ShopProduct(id = 77, slug = "delete-checkout", title = "Delete Checkout")
            ShopCheckoutStore.pendingRef = "delete-pending-ref"
            ShopProductHandoffStore.put(ShopProduct(id = 78, slug = "delete-details", title = "Delete Details"))
            BackgroundStore.save(context, 4)
            AccountDeletionFlow.set("k@example.com", "secret-password")
            viewModel = AccountDeletionReasonViewModel(More2Repository(api))
            viewModel.selectReason("Other")
            viewModel.requestDelete()
            viewModel.confirmDelete(context)
        }

        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.deleted }

        assertEquals("secret-password", api.lastPassword.get())
        assertNull("Swift parity: account deletion clears bearer token", AuthTokenStore.token)
        assertEquals(SessionStore.HeaderInfo(), SessionStore.header.value)
        assertEquals(0, CartStore.count)
        assertNull(ShopCheckoutStore.product)
        assertNull(ShopCheckoutStore.pendingRef)
        assertNull(ShopProductHandoffStore.consume("delete-details"))
        assertEquals(BackgroundStore.DEFAULT_ID, BackgroundStore.selectedId(context))
        assertEquals("", AccountDeletionFlow.email)
        assertEquals("", AccountDeletionFlow.password)
    }

    private fun setReasonScreen(
        verifiedCredentials: Boolean = false,
    ): AccountDeletionReasonViewModel {
        val api = FakeMore2Api()
        lateinit var viewModel: AccountDeletionReasonViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            if (verifiedCredentials) {
                AccountDeletionFlow.set("k@example.com", "secret-password")
            } else {
                AccountDeletionFlow.clear()
            }
            viewModel = AccountDeletionReasonViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                AccountDeletionReasonScreen(
                    onBack = {},
                    onDeleted = {},
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun assertNoFigmaHandleInTopBand(bitmap: Bitmap) {
        val density = bitmap.width / 375f
        val yStart = (10f * density).toInt().coerceAtLeast(0)
        val yEnd = (22f * density).toInt().coerceAtMost(bitmap.height - 1)
        val xStart = (bitmap.width / 2f - 65f * density).toInt().coerceAtLeast(0)
        val xEnd = (bitmap.width / 2f + 65f * density).toInt().coerceAtMost(bitmap.width - 1)

        var handlePixels = 0
        for (y in yStart..yEnd) {
            for (x in xStart..xEnd) {
                if (bitmap.getPixel(x, y).isNearGrayHandle()) {
                    handlePixels += 1
                }
            }
        }

        assertTrue(
            "Swift runtime sheet has no Figma grab handle in the top band",
            handlePixels < (density * density * 12f).toInt().coerceAtLeast(8),
        )
    }

    private fun Int.isNearGrayHandle(): Boolean {
        val alpha = (this ushr 24) and 0xFF
        if (alpha < 220) return false
        val red = (this shr 16) and 0xFF
        val green = (this shr 8) and 0xFF
        val blue = this and 0xFF
        return near(red, 0xE5) && near(green, 0xE5) && near(blue, 0xE5) ||
            near(red, 0xEB) && near(green, 0xEB) && near(blue, 0xEB)
    }

    private fun near(value: Int, target: Int): Boolean =
        kotlin.math.abs(value - target) <= 12

    private fun saveSheetScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag(
            "account-deletion-confirm-sheet",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/account_deletion_reason",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/account_deletion_reason")
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
        val lastPassword = AtomicReference<String>()
        val deactivateCalls = AtomicInteger()

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun profile(): CurrentUserResponse =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in AccountDeletionReasonParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse {
            deactivateCalls.incrementAndGet()
            lastPassword.set(body.password)
            return MutationResponse(success = true, message = "deleted")
        }
    }
}
