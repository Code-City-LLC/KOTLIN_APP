package com.ga.airdrop.feature.shipments

import androidx.activity.ComponentActivity
import android.content.Intent
import android.content.ContentUris
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceViewerParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
    fun cachedPdfRendersInsideViewerAndEnablesActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "invoice-preview-screen.pdf").also(::writeSamplePdf)
        val file = copyInvoiceStreamToCache(context, source.name, source.inputStream())

        setInvoiceViewerContent(
            mode = ThemeController.Mode.LIGHT,
            url = file.toUri().toString(),
        )
        compose.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                compose.onNodeWithTag("invoice-save-button").assertIsEnabled()
                compose.onNodeWithTag("invoice-share-button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        saveRootScreenshot("invoice_viewer_local_pdf_light.png")
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun saveButtonPersistsTwoCompleteCopiesWithoutReplacingTheFirst() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = "airdrop-export-ui-${System.nanoTime()}"
        val source = File(context.cacheDir, "$marker.pdf").also(::writeSamplePdf)
        val resolver = context.contentResolver
        fun exports(): List<android.net.Uri> = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("$marker%"),
            null,
        )!!.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)))
                }
            }
        }
        assertTrue(exports().isEmpty())
        try {
            setInvoiceViewerContent(ThemeController.Mode.LIGHT, source.toUri().toString())
            repeat(2) { index ->
                compose.waitUntil(timeoutMillis = 20_000) {
                    runCatching { compose.onNodeWithTag("invoice-save-button").assertIsEnabled(); true }.getOrDefault(false)
                }
                compose.onNodeWithTag("invoice-save-button").performClick()
                var observedCount = -1
                var observedEnabled = false
                try {
                    compose.waitUntil(timeoutMillis = 20_000) {
                        observedCount = exports().size
                        observedEnabled = runCatching {
                            compose.onNodeWithTag("invoice-save-button").assertIsEnabled(); true
                        }.getOrDefault(false)
                        observedCount == index + 1 && observedEnabled
                    }
                } catch (failure: Throwable) {
                    saveRootScreenshot("invoice_viewer_save_failure.png")
                    throw AssertionError(
                        "Save attempt ${index + 1}: exports=$observedCount, enabled=$observedEnabled, " +
                            "button=${compose.onNodeWithTag("invoice-save-button").getUnclippedBoundsInRoot()}",
                        failure,
                    )
                }
            }
            assertEquals(2, exports().size)
            exports().forEach { uri ->
                org.junit.Assert.assertArrayEquals(source.readBytes(), resolver.openInputStream(uri)!!.use { it.readBytes() })
            }
            saveRootScreenshot("invoice_viewer_saved_downloads.png")
        } finally {
            // Only rows bearing this attempt's unique synthetic marker.
            exports().forEach { resolver.delete(it, null, null) }
            source.delete()
        }
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

    @Test
    fun pdfPreviewRendersFromLocalFileLikeSwiftQuickLook() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "invoice-preview-test.pdf").also(::writeSamplePdf)

        val bitmap = renderPdfFirstPage(file, maxWidth = 600)

        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
        bitmap.recycle()
    }

    @Test
    fun airdropAuthHeadersOnlyAttachToAirdropInvoiceHosts() {
        assertTrue(shouldAttachAirdropAuth("${BuildConfig.API_BASE_URL}/packages/1/invoices/2"))
        assertTrue(shouldAttachAirdropAuth("${BuildConfig.WEB_BASE_URL}/storage/invoices/invoice.pdf"))
        assertTrue(!shouldAttachAirdropAuth("https://example.test/invoice.pdf"))
    }

    @Test
    fun failedInvoiceCopyLeavesNoFinalOrPartialCustomerFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = "invoice-copy-failure-${System.nanoTime()}.pdf"
        val invoiceDir = File(context.cacheDir, "invoices")
        val existingEntries = invoiceDir.listFiles().orEmpty().map { it.name }.toSet()
        val failingStream = object : InputStream() {
            private var firstRead = true

            override fun read(): Int = error("bulk read expected")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (firstRead) {
                    firstRead = false
                    buffer[offset] = 0x25
                    return 1
                }
                throw IOException("simulated body failure")
            }
        }

        var failure: Throwable? = null
        try {
            copyInvoiceStreamToCache(context, marker, failingStream)
        } catch (caught: Throwable) {
            failure = caught
        }

        assertTrue("the simulated body failure must propagate", failure is IOException)
        assertTrue(
            "no final or .part customer file may survive a failed copy",
            invoiceDir.walkTopDown()
                .none { it.name.contains(marker) },
        )
        assertEquals(existingEntries, invoiceDir.listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test
    fun overlappingSameNameDownloadsKeepTheirOwnBytesAndShareHandles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val name = "invoice-overlap-${System.nanoTime()}.pdf"
        var older: File? = null
        var newer: File? = null
        val slow = object : ByteArrayInputStream("older document".toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "download was not released" }
                return super.read(buffer, offset, length)
            }
        }
        val pending = executor.submit<File> { copyInvoiceStreamToCache(context, name, slow) }
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
            val current = copyInvoiceStreamToCache(context, name, "newer document".byteInputStream())
            newer = current
            val intent = invoiceShareIntent(context, current, "Invoice")
            val shared = requireNotNull(intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
            release.countDown()
            val previous = pending.get(10, TimeUnit.SECONDS)
            older = previous

            assertNotEquals(previous.canonicalPath, current.canonicalPath)
            assertEquals(name, previous.name)
            assertEquals(name, current.name)
            assertEquals("older document", previous.readText())
            assertEquals("newer document", current.readText())
            assertTrue(previous.delete()) // mirrors stale-owner cleanup
            assertTrue(current.exists())
            assertEquals("newer document", context.contentResolver.openInputStream(shared)!!.bufferedReader().use { it.readText() })
        } finally {
            release.countDown()
            executor.shutdown()
            executor.awaitTermination(15, TimeUnit.SECONDS)
            if (older == null && pending.isDone) older = runCatching { pending.get() }.getOrNull()
            for (file in listOfNotNull(older, newer)) {
                file.delete()
                file.parentFile?.delete()
            }
        }
    }

    @Test
    fun cancelledCopyClosesStreamAndLeavesOtherDownloadUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "invoice-cancel-${System.nanoTime()}.pdf"
        val current = copyInvoiceStreamToCache(context, name, "keep me".byteInputStream())
        val root = File(context.cacheDir, "invoices")
        val before = root.listFiles().orEmpty().map { it.name }.toSet()
        var closed = false
        val cancelled = object : InputStream() {
            override fun read(): Int = throw CancellationException("synthetic cancelled download")
            override fun close() { closed = true }
        }
        try {
            val failure = runCatching { copyInvoiceStreamToCache(context, name, cancelled) }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertTrue(closed)
            assertEquals(before, root.listFiles().orEmpty().map { it.name }.toSet())
            assertEquals("keep me", current.readText())
        } finally {
            current.delete()
            current.parentFile?.delete()
        }
    }

    @Test
    fun closeFailureRemovesOnlyItsOwnUnpublishedDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "invoices")
        val before = root.listFiles().orEmpty().map { it.name }.toSet()
        val stream = object : ByteArrayInputStream("complete body".toByteArray()) {
            override fun close() { throw IOException("synthetic close failure") }
        }
        val failure = runCatching { copyInvoiceStreamToCache(context, "Invoice.pdf", stream) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(before, root.listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test
    fun dotFilenamesCannotEscapeOwnedDownloadDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in listOf(".", "..")) {
            val file = copyInvoiceStreamToCache(context, name, "document".byteInputStream())
            try {
                assertEquals("Invoice.pdf", file.name)
                assertEquals(File(context.cacheDir, "invoices").canonicalFile, file.parentFile!!.parentFile!!.canonicalFile)
                assertEquals("document", file.readText())
            } finally {
                file.delete()
                file.parentFile?.delete()
            }
        }
    }

    private fun setInvoiceViewerContent(
        mode: ThemeController.Mode,
        url: String = "",
        title: String = "Invoice",
    ) {
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
                        url = url,
                        title = title,
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

    private fun writeSamplePdf(file: File) {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(240, 320, 1).create())
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }
        page.canvas.drawText("Invoice", 32f, 64f, paint)
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }
}
