package com.ga.airdrop.feature.shipments

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceViewerParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun invoiceViewerUsesSwiftSurfaceGeometryLight() {
        setInvoiceViewerContent(ThemeController.Mode.LIGHT)

        assertInvoiceViewerGeometry()
        saveRootScreenshot("invoice_viewer_swift_light.png")
    }

    @Test
    fun invoiceViewerUsesSwiftSurfaceGeometryDark() {
        setInvoiceViewerContent(ThemeController.Mode.DARK)

        assertInvoiceViewerGeometry()
        saveRootScreenshot("invoice_viewer_swift_dark.png")
    }

    @Test
    fun shareIntentUsesFileStreamNotRawUrl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "invoice-share-test.pdf").also {
            it.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))
        }

        val intent = invoiceShareIntent(context, file, "Invoice #100")
        val stream = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals("Invoice #100", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertNull(intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("content", stream?.scheme)
        assertEquals("${context.packageName}.fileprovider", stream?.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(stream, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun localInvoiceFileIsUsedDirectlyForActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "invoice-local-test.pdf").also {
            it.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))
        }

        val prepared = kotlinx.coroutines.runBlocking {
            prepareInvoiceActionFile(context, file.toUri().toString(), file.name)
        }

        assertEquals(file.absolutePath, prepared.absolutePath)
    }

    @Test
    fun invoiceMimeTypeMatchesSwiftShareFileTypes() {
        assertEquals("application/pdf", invoiceMimeType("invoice.pdf"))
        assertEquals("image/jpeg", invoiceMimeType("invoice.jpeg"))
        assertEquals("image/png", invoiceMimeType("invoice.png"))
        assertEquals("application/octet-stream", invoiceMimeType("invoice.bin"))
    }

    private fun setInvoiceViewerContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    InvoiceViewerScreen(
                        url = "",
                        title = "Invoice",
                        onBack = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInvoiceViewerGeometry() {
        val preview = compose.onNodeWithTag("invoice-viewer-preview").getUnclippedBoundsInRoot()
        val save = compose.onNodeWithTag("invoice-save-button").getUnclippedBoundsInRoot()
        val share = compose.onNodeWithTag("invoice-share-button").getUnclippedBoundsInRoot()

        assertClose(335f, boundsWidth(preview), "Swift preview horizontal inset")
        assertClose(52f, boundsHeight(save), "Save button height")
        assertClose(52f, boundsHeight(share), "Share button height")
        assertClose(12f, boundsLeft(share) - boundsRight(save), "Swift actions gap")
        assertClose(boundsWidth(save), boundsWidth(share), "Equal action widths")
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/invoice_viewer",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun boundsLeft(bounds: DpRect): Float = bounds.left.value

    private fun boundsRight(bounds: DpRect): Float = bounds.right.value

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
