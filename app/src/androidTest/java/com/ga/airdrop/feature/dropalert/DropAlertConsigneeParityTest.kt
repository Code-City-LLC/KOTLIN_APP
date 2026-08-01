package com.ga.airdrop.feature.dropalert

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.TextSizeController
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DropAlertConsigneeParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun blankProfileAllowsManualConsigneeAndSubmitLight() {
        val repository = RecordingDropAlertRepository(profileName = null)
        val viewModel = setDropAlert(ThemeController.Mode.LIGHT, repository)

        fillRequiredFields()
        assertPackageValueFieldIsFullyVisible()
        saveRootScreenshot("drop_alert_consignee_manual_light.png")
        scrollSubmitIntoView()
        saveRootScreenshot("drop_alert_consignee_actions_light.png")
        submitAndAssertSwiftConsignee(viewModel, repository)
    }

    @Test
    fun blankProfileAllowsManualConsigneeAndSubmitDark() {
        val repository = RecordingDropAlertRepository(profileName = null)
        val viewModel = setDropAlert(ThemeController.Mode.DARK, repository)

        fillRequiredFields()
        assertPackageValueFieldIsFullyVisible()
        saveRootScreenshot("drop_alert_consignee_manual_dark.png")
        scrollSubmitIntoView()
        saveRootScreenshot("drop_alert_consignee_actions_dark.png")
        submitAndAssertSwiftConsignee(viewModel, repository)
    }

    private fun setDropAlert(
        mode: ThemeController.Mode,
        repository: RecordingDropAlertRepository,
    ): DropAlertViewModel {
        clearDropAlertPreset()
        lateinit var viewModel: DropAlertViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Pin the font scale: this test asserts absolute layout geometry and
            // TextSizeController is a process-wide singleton other suites set to
            // LARGEST, so a geometry assertion should own that input rather than
            // inherit it.
            //
            // ⚠️ This is HYGIENE, NOT THE FIX. Pinning it does NOT stop the two
            // full-suite failures — verified by re-running the same selection
            // after adding it. Ruled out so far as the cause: font scale, theme,
            // the synthetic IME inset from DeliveryMethodImeTest, and the auth /
            // delivery / core packages individually. Still open.
            TextSizeController.init(InstrumentationRegistry.getInstrumentation().targetContext)
            TextSizeController.set(TextSizeController.Level.STANDARD)
            ThemeController.set(mode)
            viewModel = DropAlertViewModel(repository)
            viewModel.onShippingMethodSelected("Airdrop standard")
            viewModel.onCourierCompanySelected("FedEx")
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    DropAlertScreen(
                        viewModel = viewModel,
                        onBack = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun clearDropAlertPreset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dropalert_preset", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun fillRequiredFields() {
        compose.onNodeWithTag("drop-alert-courier-number-input", useUnmergedTree = true)
            .performTextInput("3498534580")
        compose.onNodeWithTag("drop-alert-shipper-input", useUnmergedTree = true)
            .performTextInput("Amazon")
        compose.onNodeWithTag("drop-alert-consignee-input", useUnmergedTree = true)
            .performTextInput("Kerry Smith")
        compose.onNodeWithTag("drop-alert-package-value-input", useUnmergedTree = true)
            .performTextInput("84")
        compose.waitForIdle()
        compose.onNodeWithText("Kerry Smith").assertIsDisplayed()
    }

    private fun assertPackageValueFieldIsFullyVisible() {
        // ⚠️ CLOSE THE KEYBOARD FIRST, and this is the actual bug in this test.
        //
        // `drop-alert-root` carries `.imePadding()`, so the root legitimately
        // SHRINKS while a keyboard is up — and this helper runs straight after
        // fillRequiredFields() has typed into three fields. Measured: the field
        // sits at 484.57dp in every run, but the root is 520.0dp with no
        // keyboard and 475.81dp with one. The app was never wrong; the
        // assertion was reading a mid-typing state.
        //
        // Why it looked like flake: in an isolated run the IME never actually
        // materialises (nothing has warmed it), so root stays 520 and the test
        // passes. Run the whole `feature` package first and the IME is warm and
        // really shows — root drops by the 44.19dp inset and the same assertion
        // fails. Deterministic, and it reproduces identically on origin/main.
        dismissKeyboardAndSettle()
        val packageValue = compose.onNodeWithTag("drop-alert-package-value-field", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val root = compose.onNodeWithTag("drop-alert-root", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Package Value field should not be clipped by the screen fold; " +
                "fieldBottom=${packageValue.bottom.value}, rootBottom=${root.bottom.value}",
            packageValue.bottom.value <= root.bottom.value - 8f,
        )
    }

    private fun scrollSubmitIntoView() {
        compose.onNodeWithTag("drop-alert-submit-button", useUnmergedTree = true)
            .performScrollTo()
        compose.waitForIdle()
    }

    private fun submitAndAssertSwiftConsignee(
        viewModel: DropAlertViewModel,
        repository: RecordingDropAlertRepository,
    ) {
        compose.onNodeWithTag("drop-alert-submit-button", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.submission != null && viewModel.state.value.dialog?.title == "Submitted"
        }

        val submission = repository.submission
        assertNotNull(submission)
        assertEquals("Kerry Smith", submission!!.consignee)
        assertEquals("Amazon", submission.shipper)
        assertEquals("", viewModel.state.value.consignee)
        compose.onNodeWithText("Submitted").assertIsDisplayed()
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
            "screenshots/drop_alert",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/drop_alert")
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

    private class RecordingDropAlertRepository(
        private val profileName: String?,
    ) : DropAlertRepository {
        @Volatile
        var submission: DropAlertSubmission? = null

        override suspend fun createDropAlert(submission: DropAlertSubmission): DropAlertResult {
            this.submission = submission
            return DropAlertResult(success = true, message = "Your drop alert was created.")
        }

        override suspend fun consigneeName(): String? = profileName
    }

    /** Drop focus and wait for the ime inset to reach zero before measuring. */
    private fun dismissKeyboardAndSettle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            runCatching {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .firstOrNull()
                activity?.window?.decorView?.windowInsetsController
                    ?.hide(android.view.WindowInsets.Type.ime())
            }
        }
        compose.waitForIdle()
        // The hide is animated; give the inset time to land before measuring.
        runCatching {
            compose.waitUntil(timeoutMillis = 3_000) {
                compose.onNodeWithTag("drop-alert-root", useUnmergedTree = true)
                    .getUnclippedBoundsInRoot().bottom.value >= 500f
            }
        }
        compose.waitForIdle()
    }

}
