package com.ga.airdrop.feature.cart

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderSummaryChargesSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AuthTokenStore.clear()
            CheckoutFlowStore.clear()
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AuthTokenStore.clear()
            CheckoutFlowStore.clear()
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
    }

    @Test
    fun bothInfoControlsAre44DpClickableAndOpenTheSameSheet() {
        val backCalls = AtomicInteger()
        val lines = listOf(packageLine(401, 25.0), saleLine(402, 30.0))
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray150),
                ) {
                    OrderSummaryScreen(
                        model = OrderSummaryUiModel(
                            lines = lines,
                            currency = "USD",
                            exchangeUsdToJmd = 160.0,
                            totalCharges = 55.0,
                        ),
                        onBack = { backCalls.incrementAndGet() },
                        onNoteChange = {},
                        onRemoveItem = {},
                        onMakePayment = {},
                    )
                }
            }
        }

        val packageInfo = compose.onNodeWithTag("order-summary-packages-info")
            .assertHasClickAction()
            .assertIsDisplayed()
        val packageBounds = packageInfo.getUnclippedBoundsInRoot()
        assertTrue((packageBounds.right - packageBounds.left).value >= 44f)
        assertTrue((packageBounds.bottom - packageBounds.top).value >= 44f)

        packageInfo.performClick()
        compose.onNodeWithTag("order-summary-charges-sheet").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-shipment-section").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-sale-section").performScrollTo().assertIsDisplayed()
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("order-summary-charges-sheet").fetchSemanticsNodes().isEmpty()
        }

        val chargesInfo = compose.onNodeWithTag("order-summary-charges-info")
            .performScrollTo()
            .assertHasClickAction()
            .assertIsDisplayed()
        val chargesBounds = chargesInfo.getUnclippedBoundsInRoot()
        assertTrue((chargesBounds.right - chargesBounds.left).value >= 44f)
        assertTrue((chargesBounds.bottom - chargesBounds.top).value >= 44f)
        compose.onNodeWithText("Tax").assertDoesNotExist()

        chargesInfo.performClick()
        compose.onNodeWithTag("order-summary-charges-sheet").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-charges-sheet-title")
            .assertTextContains("Charges")
        pressBack()
        compose.waitForIdle()
        assertEquals(0, backCalls.get())
    }

    @Test
    fun compactLargestTextScrollsToCanonicalTotalAndRetry() {
        val retryCalls = AtomicInteger()
        val shipments = (1..5).map { index ->
            packageLine(500 + index, priceUsd = 20.0 + index)
        }
        val snapshots = shipments.take(2).map { line ->
            snapshot(
                line,
                declared = 100.0,
                charges = mapOf(
                    "Freight" to 8.0,
                    "Insurance" to 2.0,
                    "Fuel" to 1.0,
                    "Customs Duty" to 5.0,
                    "Customs GCT" to 3.0,
                ),
            )
        }
        val breakdown = breakdown(
            lines = shipments,
            snapshots = snapshots,
            unavailable = shipments.drop(2).map(CartStore.CartLine::key).toSet(),
        )

        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 2f),
            ) {
                AirdropThemeProvider {
                    Box(
                        Modifier
                            .width(320.dp)
                            .height(568.dp)
                            .background(AirdropTheme.colors.gray150),
                    ) {
                        OrderSummaryChargesSheetContent(
                            breakdown = breakdown,
                            loading = true,
                            failedShipmentCount = 3,
                            selectedCif = null,
                            onRetry = { retryCalls.incrementAndGet() },
                            onCifClick = {},
                            onCifBack = {},
                            onCustomsNoticeClick = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("order-summary-charges-retry")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, retryCalls.get())
        compose.onNodeWithText("View CIF breakdown").assertDoesNotExist()
        compose.onNodeWithTag("order-summary-cif-incomplete")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("order-summary-charges-sheet-total")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("order-summary-captured-total-notice")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("order-summary-charges-sheet-total").assertIsDisplayed()
        captureProof("compact_320x568_2x_tail")
    }

    @Test
    fun largeContainerKeepsDisclosureMismatchSeparateFromCapturedTotal() {
        val shipment = packageLine(601, priceUsd = 100.0)
        val sale = saleLine(602, priceUsd = 60.0)
        val result = breakdown(
            lines = listOf(shipment, sale),
            snapshots = listOf(
                snapshot(
                    shipment,
                    declared = 100.0,
                    charges = mapOf("Freight" to 8.0, "Insurance" to 2.0),
                    backendTotal = 12.0,
                ),
            ),
            total = 160.0,
        )
        val openedCif = AtomicReference<ShipmentCifBreakdown?>()

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1f)) {
                AirdropThemeProvider {
                    Box(
                        Modifier
                            .width(700.dp)
                            .height(1_000.dp)
                            .background(AirdropTheme.colors.gray150),
                    ) {
                        OrderSummaryChargesSheetContent(
                            breakdown = result,
                            loading = false,
                            failedShipmentCount = 0,
                            selectedCif = null,
                            onRetry = {},
                            onCifClick = openedCif::set,
                            onCifBack = {},
                            onCustomsNoticeClick = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Itemized charge subtotal").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-charge-mismatch-601").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-captured-charge-mismatch-601").assertIsDisplayed()
        compose.onNodeWithText("USD 160.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("order-summary-sale-subtotal").assertIsDisplayed()
        captureProof("mixed_shipment_sale_overview")
        compose.onNodeWithTag("order-summary-cif-open-601")
            .assertHasClickAction()
            .performClick()
        assertEquals(shipment.key, openedCif.get()?.cartKey)
    }

    @Test
    fun modalSupportsCustomsCifDrilldownAndTwoLevelBackDismissal() {
        val shipment = packageLine(701, priceUsd = 25.0)
        val result = breakdown(
            lines = listOf(shipment),
            snapshots = listOf(
                snapshot(
                    shipment,
                    declared = 100.0,
                    charges = linkedMapOf(
                        "Freight" to 10.0,
                        "Insurance" to 2.0,
                        "Fuel" to 3.0,
                        "Customs Duty" to 4.0,
                        "Customs GCT" to 6.0,
                    ),
                ),
            ),
            total = 25.0,
        )
        val dismissed = AtomicBoolean(false)

        compose.setContent {
            AirdropThemeProvider {
                var showing by remember { mutableStateOf(true) }
                if (showing) {
                    OrderSummaryChargesSheet(
                        breakdown = result,
                        loading = false,
                        failedShipmentCount = 0,
                        onRetry = {},
                        onDismiss = {
                            dismissed.set(true)
                            showing = false
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("order-summary-customs-info-701")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("customs-notice-sheet").assertIsDisplayed()
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("customs-notice-sheet").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("order-summary-charges-sheet").assertIsDisplayed()

        compose.onNodeWithTag("order-summary-cif-open-701")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("order-summary-cif-table").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-cif-formula").assertIsDisplayed()
        compose.onNodeWithText("Fuel").assertDoesNotExist()
        compose.onNodeWithText("Customs GCT").assertDoesNotExist()
        captureProof("cif_drilldown_table")

        pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("order-summary-charges-sheet").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-charges-sheet-title").assertIsDisplayed()
        assertFalse(dismissed.get())
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) { dismissed.get() }
        compose.onNodeWithTag("order-summary-charges-sheet").assertDoesNotExist()
    }

    @Test
    fun darkSaleOnlySheetHasNoShipmentOrCifDisclosure() {
        val sale = saleLine(801, priceUsd = 45.0)
        val result = breakdown(listOf(sale), emptyList(), total = 45.0)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.DARK)
        }

        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray150),
                ) {
                    OrderSummaryChargesSheetContent(
                        breakdown = result,
                        loading = false,
                        failedShipmentCount = 0,
                        selectedCif = null,
                        onRetry = {},
                        onCifClick = {},
                        onCifBack = {},
                        onCustomsNoticeClick = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("order-summary-sale-section").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-shipment-section").assertDoesNotExist()
        compose.onNodeWithTag("order-summary-cif-incomplete").assertDoesNotExist()
        compose.onNodeWithText("View CIF breakdown").assertDoesNotExist()
        compose.onNodeWithTag("order-summary-charges-sheet-total")
            .performScrollTo()
            .assertIsDisplayed()
        captureProof("dark_sale_only")
    }

    private fun breakdown(
        lines: List<CartStore.CartLine>,
        snapshots: List<CheckoutShipmentChargeSnapshot>,
        unavailable: Set<CartStore.CartLineKey> = emptySet(),
        total: Double = lines.sumOf { it.priceUsd * it.qty },
    ) = OrderChargeBreakdown.calculate(
        lines = lines,
        snapshots = snapshots,
        paymentCurrency = "USD",
        checkoutExchangeRateUsdToJmd = 160.0,
        deliveryFee = null,
        deliveryFeeCurrency = null,
        canonicalFlowAvailable = true,
        fallbackDisplayTotal = total,
        unavailableShipmentKeys = unavailable,
    )

    private fun packageLine(id: Int, priceUsd: Double) = CartStore.CartLine(
        id = id,
        packageId = id,
        title = "Package $id with a long shipment description",
        priceUsd = priceUsd,
        kind = CartStore.CartLineKind.PACKAGE,
        statusCode = 7,
        serverConfirmed = true,
    )

    private fun saleLine(id: Int, priceUsd: Double) = CartStore.CartLine(
        id = id,
        packageId = id + 1_000,
        title = "Sale $id",
        priceUsd = priceUsd,
        kind = CartStore.CartLineKind.AUCTION,
        isAuction = true,
    )

    private fun snapshot(
        line: CartStore.CartLine,
        declared: Double,
        charges: Map<String, Double>,
        backendTotal: Double = charges.values.sum(),
    ) = CheckoutShipmentChargeSnapshot(
        cartKey = line.key,
        packageId = requireNotNull(line.packageId),
        declaredValueUsd = declared,
        additionalCharges = charges,
        additionalChargesTotalUsd = backendTotal,
        exchangeRateUsdToJmd = 160.0,
    )

    private fun captureProof(stem: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("order-summary-charges-sheet")
            .captureToImage()
            .asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val filename = "${stem}_${metrics.widthPixels}x${metrics.heightPixels}_" +
            "${metrics.densityDpi}dpi.png"
        val resolver = context.contentResolver
        resolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf(filename, PROOF_SCREENSHOT_DIR),
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, PROOF_SCREENSHOT_DIR)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Unable to create charge-sheet proof $filename" }
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Unable to open charge-sheet proof $filename" }
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode charge-sheet proof $filename"
            }
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        check(resolver.update(uri, values, null, null) == 1) {
            "Unable to publish charge-sheet proof $filename"
        }
    }

    private companion object {
        const val PROOF_SCREENSHOT_DIR =
            "Pictures/kotlin_ui_proof/order_summary_charges_cif/"
    }
}
