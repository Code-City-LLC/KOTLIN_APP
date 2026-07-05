package com.ga.airdrop.feature.more

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
import com.ga.airdrop.core.navigation.Routes
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentMethodsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigatedRoutes = mutableListOf<String>()
    private var backClicks = 0

    @Test
    fun paymentMethodsKeepsSwiftEmptyCheckoutRailLight() {
        setPaymentMethods(ThemeController.Mode.LIGHT)

        assertSwiftContent()
        assertNoStaleFigmaSavedCardContent()
        assertSwiftGeometry()
        saveRootScreenshot("payment_methods_swift_light.png")
    }

    @Test
    fun paymentMethodsKeepsSwiftEmptyCheckoutRailDark() {
        setPaymentMethods(ThemeController.Mode.DARK)

        assertSwiftContent()
        assertNoStaleFigmaSavedCardContent()
        assertSwiftGeometry()
        saveRootScreenshot("payment_methods_swift_dark.png")
    }

    @Test
    fun checkoutCardNavigatesToSwiftCartRoute() {
        setPaymentMethods(ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("payment-methods-checkout-card").performClick()
        compose.runOnIdle {
            assertEquals(listOf(Routes.CART), navigatedRoutes)
        }
    }

    private fun setPaymentMethods(mode: ThemeController.Mode) {
        navigatedRoutes.clear()
        backClicks = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    PaymentMethodsScreen(
                        onBack = { backClicks += 1 },
                        onNavigate = navigatedRoutes::add,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Payment Methods").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertSwiftContent() {
        compose.onNodeWithText("Payment Methods").assertIsDisplayed()
        compose.onNodeWithText("No saved payment methods").assertIsDisplayed()
        compose.onNodeWithText("Cards and other payment methods are added during checkout.")
            .assertIsDisplayed()
        compose.onNodeWithText("Go to Checkout").assertIsDisplayed()
    }

    private fun assertNoStaleFigmaSavedCardContent() {
        listOf(
            "Select the payment method you want to use.",
            "Add New Card",
            "Number",
        ).forEach(::assertNoText)
    }

    private fun assertSwiftGeometry() {
        val root = bounds("payment-methods-root")
        val status = bounds("payment-methods-status-card")
        val checkout = bounds("payment-methods-checkout-card")

        assertClose(375f, boundsWidth(root), "Payment Methods frame width")
        assertClose(20f, status.left.value, "Status card left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(status), "Status card width")
        assertTrue(
            "Swift status card has minimum 94dp height",
            boundsHeight(status) >= 94f,
        )
        assertClose(14f, checkout.top.value - status.bottom.value, "Status-to-checkout gap")
        assertClose(59f, boundsHeight(checkout), "Checkout card height")
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text is stale Figma content and should not render in Swift Payment Methods",
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
            "screenshots/payment_methods",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/payment_methods")
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
}
