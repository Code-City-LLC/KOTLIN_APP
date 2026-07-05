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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AdditionalFees
import com.ga.airdrop.data.model.AirdropStandardRates
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
import com.ga.airdrop.data.model.ShippingRate
import com.ga.airdrop.data.model.ShippingRates
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShippingRatesParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val routes = mutableListOf<String>()
    private var backClicks = 0

    @Test
    fun fallbackKeepsSwiftRuntimeRatesAndCalculatorRailLight() {
        val api = FakeMore2Api(failShippingRates = true)
        setShippingRates(api, ThemeController.Mode.LIGHT)

        compose.onNodeWithText("AirDrop Standard Rates").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
        assertTextExists("\$5.00")
        assertNoText("0.5")
        assertNoText("\$4.50")

        assertSwiftTopGeometry()
        compose.onNodeWithTag("shipping-rates-scroll")
            .performScrollToNode(hasTestTag("shipping-rates-standard-row-over-20"))
        assertTextExists("21 & Up")
        assertTextExists("\$3.00 each additional lbs.")

        compose.onNodeWithTag("more2-inner-header-back", useUnmergedTree = true).performClick()
        assertEquals("Back button should dispatch through the shared Swift header rail", 1, backClicks)

        compose.onNodeWithTag("shipping-rates-calculate", useUnmergedTree = true).performClick()
        assertEquals(listOf(Routes.CALCULATOR), routes)
        saveRootScreenshot("shipping_rates_swift_fallback_light.png")
    }

    @Test
    fun liveRatesUseBackendDataAndDarkThemeChrome() {
        val api = FakeMore2Api(
            shippingRates = ShippingRates(
                airdropStandard = AirdropStandardRates(
                    rates = listOf(ShippingRate(weightLbs = 0.5, rateUsd = 4.5)),
                    overTwentyLbRate = 3.25,
                ),
                additionalFees = AdditionalFees(
                    fuelSurcharge = 2.0,
                    customsThreshold = 100.0,
                ),
            ),
        )
        setShippingRates(api, ThemeController.Mode.DARK)

        assertEquals("Shipping Rates should make one backend request on entry", 1, api.shippingRatesCalls.get())
        compose.onNodeWithText("0.5").assertIsDisplayed()
        compose.onNodeWithText("\$4.50").assertIsDisplayed()
        compose.onNodeWithText("\$3.25 each additional lbs.").assertIsDisplayed()
        compose.onNodeWithTag("shipping-rates-scroll")
            .performScrollToNode(hasTestTag("shipping-rates-estimate-card"))
        compose.onNodeWithText("fuel surcharge of \$2.00", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("shipping-rates-scroll")
            .performScrollToNode(hasTestTag("shipping-rates-customs-card"))
        compose.onNodeWithText("Customs Fees").assertIsDisplayed()
        assertSwiftBottomGeometry()
        saveRootScreenshot("shipping_rates_live_dark.png")
    }

    private fun setShippingRates(api: FakeMore2Api, mode: ThemeController.Mode) {
        routes.clear()
        backClicks = 0
        lateinit var viewModel: ShippingRatesViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = ShippingRatesViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    ShippingRatesScreen(
                        onBack = { backClicks += 1 },
                        onNavigate = { routes += it },
                        viewModel = viewModel,
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.shippingRatesCalls.get() == 1 && !viewModel.state.value.loading
        }
        compose.waitForIdle()
    }

    private fun assertSwiftTopGeometry() {
        val root = bounds("shipping-rates-root")
        val title = bounds("shipping-rates-standard-title")
        val table = bounds("shipping-rates-standard-card")
        val firstRow = bounds("shipping-rates-standard-row-0")
        val inOut = bounds("shipping-rates-inout-card")
        val cta = bounds("shipping-rates-calculate")

        assertClose(375f, boundsWidth(root), "Shipping Rates frame width")
        assertClose(20f, title.left.value, "Section title left gutter")
        assertClose(20f, table.left.value, "Standard table left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(table), "Standard table width")
        assertClose(44f, boundsHeight(firstRow), "Swift standard row height")
        assertClose(20f, inOut.top.value - table.bottom.value, "Table-to-In & Out gap")
        assertClose(20f, cta.left.value, "Calculate CTA left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(cta), "Calculate CTA width")
        assertClose(52f, boundsHeight(cta), "Calculate CTA height")
    }

    private fun assertSwiftBottomGeometry() {
        val root = bounds("shipping-rates-root")
        val customs = bounds("shipping-rates-customs-card")
        val cta = bounds("shipping-rates-calculate")

        assertClose(20f, customs.left.value, "Customs card left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(customs), "Customs card width")
        assertClose(20f, cta.left.value, "Dark Calculate CTA left gutter")
        assertClose(52f, boundsHeight(cta), "Dark Calculate CTA height")
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should not render in the Swift fallback Shipping Rates flow",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertTextExists(text: String) {
        assertTrue(
            "$text should exist somewhere in the rendered Shipping Rates tree",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
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
            "screenshots/shipping_rates",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/shipping_rates")
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

    private class FakeMore2Api(
        private val shippingRates: ShippingRates = ShippingRates(),
        private val failShippingRates: Boolean = false,
    ) : More2Api {
        val shippingRatesCalls = AtomicInteger()

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> {
            shippingRatesCalls.incrementAndGet()
            if (failShippingRates) throw IOException("offline")
            return DataEnvelope(data = shippingRates)
        }

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun profile(): DataEnvelope<AirdropUser> =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in ShippingRatesParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in ShippingRatesParityTest")
    }
}
