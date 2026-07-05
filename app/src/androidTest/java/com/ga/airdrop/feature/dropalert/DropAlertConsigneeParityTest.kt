package com.ga.airdrop.feature.dropalert

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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        saveRootScreenshot("drop_alert_consignee_manual_light.png")
        submitAndAssertSwiftConsignee(viewModel, repository)
    }

    @Test
    fun blankProfileAllowsManualConsigneeAndSubmitDark() {
        val repository = RecordingDropAlertRepository(profileName = null)
        val viewModel = setDropAlert(ThemeController.Mode.DARK, repository)

        fillRequiredFields()
        saveRootScreenshot("drop_alert_consignee_manual_dark.png")
        submitAndAssertSwiftConsignee(viewModel, repository)
    }

    private fun setDropAlert(
        mode: ThemeController.Mode,
        repository: RecordingDropAlertRepository,
    ): DropAlertViewModel {
        lateinit var viewModel: DropAlertViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
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

    private fun submitAndAssertSwiftConsignee(
        viewModel: DropAlertViewModel,
        repository: RecordingDropAlertRepository,
    ) {
        compose.onNodeWithTag("drop-alert-submit-button", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.submission != null && viewModel.state.value.dialog?.title == "Submitted"
        }

        val submission = repository.submission
        assertNotNull(submission)
        assertEquals("Kerry Smith", submission!!.consignee)
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
}
