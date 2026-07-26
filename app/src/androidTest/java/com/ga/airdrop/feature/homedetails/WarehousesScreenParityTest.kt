package com.ga.airdrop.feature.homedetails

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.session.FakeAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.Warehouse
import com.ga.airdrop.data.model.WarehouseSerializer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Warehouses parity.
 *
 * FIXTURE NOTE (2026-07-26): these tests used to render [WarehousesScreen] with
 * its default `viewModel()`, i.e. with no server data at all. That worked only
 * because the screen used to paint hardcoded FALLBACK_ADDRESS_* constants, so an
 * empty fetch still produced a full address card to measure. Those constants
 * were deleted — an unfetched screen is now a spinner or an error, not a
 * confident US address a customer might copy — so the geometry/tab/tint tests
 * below now get their warehouse the way the app does: from the server payload,
 * injected through [WarehousesRepository]. The fixture is the live `GET
 * /warehouse` body verbatim, decoded by the real [WarehouseSerializer], so it
 * cannot drift away from what production sends.
 */
@RunWith(AndroidJUnit4::class)
class WarehousesScreenParityTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        // FakeAuthenticatedSessionBoundary stamps the process-wide SessionStore.
        SessionStore.onAuthenticatedSessionChanged(null)
    }

    @Test
    fun warehouseHeroUsesSwiftGeometryLight() {
        setWarehouseContent(ThemeController.Mode.LIGHT, initialType = "express")

        assertSwiftHeroGeometry()
        assertSwiftNoteGeometry()
        compose.onNodeWithText("Express (Air Express)").assertIsDisplayed()
        saveRootScreenshot("warehouse_express_swift_light.png")
    }

    @Test
    fun warehouseHeroUsesSwiftGeometryDark() {
        setWarehouseContent(ThemeController.Mode.DARK, initialType = "standard")

        assertSwiftHeroGeometry()
        assertSwiftNoteGeometry()
        compose.onNodeWithText("AirDrop (Air Freight)").assertIsDisplayed()
        saveRootScreenshot("warehouse_standard_swift_dark.png")
    }

    @Test
    fun warehouseTabsKeepSwiftSingleScreenFlow() {
        setWarehouseContent(ThemeController.Mode.LIGHT, initialType = "standard")

        compose.onNodeWithTag("warehouse-tab-seadrop").performClick()
        compose.onNodeWithText("SeaDrop (Sea Freight)").assertIsDisplayed()
        assertSwiftHeroGeometry()

        compose.onNodeWithTag("warehouse-tab-express").performClick()
        compose.onNodeWithText("Express (Air Express)").assertIsDisplayed()
        assertSwiftHeroGeometry()
    }

    @Test
    fun activeWarehouseTabsKeepKemarCorrectedTintVisibleDark() {
        setWarehouseContent(ThemeController.Mode.DARK, initialType = "standard")

        val standardFill = fillPixel("warehouse-tab-standard")
        assertTrue(
            "active Standard tab fill should be visibly purple in dark mode",
            android.graphics.Color.blue(standardFill) >= 88,
        )
        assertTrue(
            "active Standard tab fill should no longer read as nearly transparent",
            android.graphics.Color.red(standardFill) >= 60,
        )
        assertTrue(
            "active Standard tab fill should stay softly translucent",
            android.graphics.Color.blue(standardFill) <= 120,
        )

        compose.onNodeWithTag("warehouse-tab-seadrop").performClick()
        compose.onNodeWithText("SeaDrop (Sea Freight)").assertIsDisplayed()
        saveRootScreenshot("warehouse_seadrop_swift_dark.png")

        val seaDropFill = fillPixel("warehouse-tab-seadrop")
        assertTrue(
            "active SeaDrop tab fill should be visibly cyan in dark mode",
            android.graphics.Color.blue(seaDropFill) >= 92,
        )
        assertTrue(
            "active SeaDrop tab fill should carry enough green/cyan tint",
            android.graphics.Color.green(seaDropFill) >= 72,
        )
        assertTrue(
            "active SeaDrop tab fill should stay softly translucent",
            android.graphics.Color.blue(seaDropFill) <= 130,
        )
    }

    @Test
    fun activeWarehouseTabKeepsKemarCorrectedTintVisibleLight() {
        setWarehouseContent(ThemeController.Mode.LIGHT, initialType = "standard")

        val lightStandardFill = fillPixel("warehouse-tab-standard")

        assertTrue(
            "active Standard tab fill should not be too transparent",
            android.graphics.Color.red(lightStandardFill) <= 218,
        )
        assertTrue(
            "active Standard tab fill should carry visible purple tint",
            android.graphics.Color.green(lightStandardFill) <= 206,
        )
        assertTrue(
            "active Standard tab fill should stay softly translucent",
            android.graphics.Color.red(lightStandardFill) >= 190,
        )
    }

    /** The live payload has to reach the card the customer copies from. */
    @Test
    fun addressCardRendersTheLiveWarehousePayload() {
        setWarehouseContent(ThemeController.Mode.LIGHT, initialType = "standard")

        compose.onNodeWithText("6175 NW 167th Street, Unit G36").assertExists()
        compose.onNodeWithText("Unit G36 - AIR – 14823").assertExists()
        compose.onNodeWithText("Hialeah").assertExists()
        compose.onNodeWithText("Florida").assertExists()
        compose.onNodeWithText("33015").assertExists()
        compose.onNodeWithText("+1(954) 508-1797").assertExists()
    }

    // ─── The two states that had no coverage at all ────────────────────────
    //
    // This gap is exactly why deleting FALLBACK_ADDRESS_* was risky: nothing
    // asserted what the screen does when the fetch has not finished or has
    // failed, so nothing would have caught it painting an address it had not
    // been given.

    /**
     * In flight: a spinner, and — the part that matters — NO address. Not a
     * partial one, not a placeholder one, not a hardcoded one.
     *
     * `mainClock.autoAdvance = false` because the indeterminate spinner animates
     * forever; with auto-advance on, `waitForIdle` would pump frames until
     * Espresso gave up.
     */
    @Test
    fun loadInFlightShowsSpinnerAndNeverAnAddress() {
        compose.mainClock.autoAdvance = false
        val stillLoading = CompletableDeferred<Unit>()
        val repository = FixtureWarehousesRepository(gate = stillLoading)

        setWarehouseContent(
            ThemeController.Mode.LIGHT,
            initialType = "standard",
            repository = repository,
        )

        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        ).assertIsDisplayed()

        // No address card, and no error state either — loading is its own state.
        compose.onNodeWithTag("warehouse-hero-wrap").assertDoesNotExist()
        compose.onNodeWithTag("warehouse-error").assertDoesNotExist()
        compose.onNodeWithText("6175 NW 167th Street, Unit G36").assertDoesNotExist()
        compose.onNodeWithText("Unit G36 - AIR – 14823").assertDoesNotExist()
        // The old constants, spelled out, so a reintroduction fails here.
        compose.onNodeWithText("Hialeah").assertDoesNotExist()
        compose.onNodeWithText("33015").assertDoesNotExist()
        saveRootScreenshot("warehouse_loading_no_address.png")

        // Let the fetch land: the spinner must give way to the real address.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            stillLoading.complete(Unit)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        compose.onNodeWithTag("warehouse-hero-wrap").assertExists()
        compose.onNodeWithText("6175 NW 167th Street, Unit G36").assertExists()
    }

    /**
     * Failed: the error copy plus a Try Again that genuinely re-fetches. The
     * screen must never substitute an address of its own for the one it could
     * not load.
     */
    @Test
    fun failedLoadShowsErrorAndARetryThatRefetches() {
        val repository = FixtureWarehousesRepository()
        repository.failWith(IOException("warehouse fetch failed"))

        val viewModel = setWarehouseContent(
            ThemeController.Mode.LIGHT,
            initialType = "standard",
            repository = repository,
        )

        compose.onNodeWithTag("warehouse-error").assertIsDisplayed()
        compose.onNodeWithText(ADDRESS_UNAVAILABLE).assertIsDisplayed()
        compose.onNodeWithTag("warehouse-retry").assertIsDisplayed()
        compose.onNodeWithTag("warehouse-hero-wrap").assertDoesNotExist()
        compose.onNodeWithText("6175 NW 167th Street, Unit G36").assertDoesNotExist()
        compose.onNodeWithText("Hialeah").assertDoesNotExist()
        assertEquals(ADDRESS_UNAVAILABLE, viewModel.state.value.error)
        saveRootScreenshot("warehouse_error_retry.png")

        val callsBeforeRetry = repository.warehouseCalls
        repository.serveLivePayload()
        compose.onNodeWithTag("warehouse-retry").performClick()
        compose.waitForIdle()

        assertEquals(
            "Try Again must actually re-fetch /warehouse",
            callsBeforeRetry + 1,
            repository.warehouseCalls,
        )
        compose.onNodeWithTag("warehouse-error").assertDoesNotExist()
        compose.onNodeWithTag("warehouse-hero-wrap").assertIsDisplayed()
        compose.onNodeWithText("6175 NW 167th Street, Unit G36").assertExists()
        compose.onNodeWithText("Unit G36 - AIR – 14823").assertExists()
        assertFalse(viewModel.state.value.loading)
    }

    private fun fillPixel(tag: String): Int {
        val tab = compose.onNodeWithTag(tag)
            .captureToImage()
            .asAndroidBitmap()
        return tab.getPixel(8, tab.height / 2)
    }

    private fun setWarehouseContent(
        mode: ThemeController.Mode,
        initialType: String,
        repository: WarehousesRepository = FixtureWarehousesRepository(),
    ): WarehousesViewModel {
        lateinit var viewModel: WarehousesViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            // Built on the main thread so the load runs on Dispatchers.Main
            // .immediate and the first composition already sees its result.
            viewModel = WarehousesViewModel(
                repository = repository,
                sessionBoundary = FakeAuthenticatedSessionBoundary(
                    initialSessionId = "warehouse-parity-session",
                    initialAccountId = FIXTURE_ACCOUNT_ID,
                ),
            )
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    WarehousesScreen(
                        onBack = {},
                        initialType = initialType,
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun assertSwiftHeroGeometry() {
        assertClose(240f, boundsHeight("warehouse-hero-wrap"), "Swift hero wrap height")
        assertClose(240f, boundsHeight("warehouse-hero-image"), "Swift hero image height")
        assertClose(60f, boundsWidth("warehouse-hero-badge"), "Swift badge width")
        assertClose(60f, boundsHeight("warehouse-hero-badge"), "Swift badge height")
        assertClose(28f, boundsWidth("warehouse-hero-badge-icon"), "Swift badge icon width")
        assertClose(28f, boundsHeight("warehouse-hero-badge-icon"), "Swift badge icon height")
        compose.onNodeWithTag("warehouse-title").assertIsDisplayed()
    }

    private fun assertSwiftNoteGeometry() {
        assertClose(20f, boundsWidth("warehouse-note-icon"), "Swift note icon width")
        assertClose(20f, boundsHeight("warehouse-note-icon"), "Swift note icon height")
        assertTrue(
            compose.onAllNodesWithTag("warehouse-note-title").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag("warehouse-root").captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/warehouses_swift").also { it.mkdirs() }
    }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/warehouses_swift")
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

    private fun boundsWidth(tag: String): Float =
        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.right - it.left).value }

    private fun boundsHeight(tag: String): Float =
        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }
}

// ─── Fixture ───────────────────────────────────────────────────────────────

/**
 * The live `GET /warehouse` row, byte for byte. Kept as the raw body and run
 * through the production [WarehouseSerializer] so this fixture exercises the
 * same decode the app does — a field the server renames silently breaks these
 * tests instead of silently emptying the screen.
 */
private const val LIVE_WAREHOUSE_JSON = """
{
  "address_line_1": "6175 NW 167th Street, Unit G36",
  "street": "6175 NW 167th Street",
  "unit": "Unit G36",
  "city": "Hialeah",
  "state": "Florida",
  "zip_code": "33015",
  "phone_number": "1(954)508-1797",
  "address_line_2_tokens": {
    "standard": "AIR",
    "express": "EXPRESS",
    "seadrop": "SEA"
  },
  "address_line_2_separator": " - "
}
"""

private val LIVE_WAREHOUSE: Warehouse =
    AirdropJson.decodeFromString(WarehouseSerializer, LIVE_WAREHOUSE_JSON)

/** Account number from the canonical Address Line 2 example: `Unit G36 - AIR – 14823`. */
private const val FIXTURE_ACCOUNT_NUMBER = "14823"
private const val FIXTURE_ACCOUNT_ID = 14823

private val FIXTURE_USER = AirdropUser(
    id = FIXTURE_ACCOUNT_ID,
    accountNumber = FIXTURE_ACCOUNT_NUMBER,
    firstName = "Airdrop",
    lastName = "Customer",
)

/**
 * Serves the live payload by default; [failWith] makes `/warehouse` fail, and a
 * non-null [gate] holds both calls open so the screen can be observed mid-load.
 */
private class FixtureWarehousesRepository(
    private val gate: CompletableDeferred<Unit>? = null,
) : WarehousesRepository {

    private var warehouseResult: Result<List<Warehouse>> = Result.success(listOf(LIVE_WAREHOUSE))

    /** Everything runs on the main thread, so a plain counter is enough. */
    var warehouseCalls: Int = 0
        private set

    fun failWith(cause: Throwable) {
        warehouseResult = Result.failure(cause)
    }

    fun serveLivePayload() {
        warehouseResult = Result.success(listOf(LIVE_WAREHOUSE))
    }

    override suspend fun currentUser(): Result<AirdropUser> {
        gate?.await()
        return Result.success(FIXTURE_USER)
    }

    override suspend fun warehouses(): Result<List<Warehouse>> {
        gate?.await()
        warehouseCalls++
        return warehouseResult
    }
}
