package com.ga.airdrop.feature.common

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.feature.more.DOCUMENT_SLOTS
import com.ga.airdrop.feature.more.documentUploadConfig
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A photo or camera shot for a slot Laravel accepts only as PDF (`mimes:pdf`
 * reads the content) must reach it as a real one-page PDF inside the 10 MB
 * limit. Photos for the ID card and TRN stay JPEGs.
 */
@RunWith(AndroidJUnit4::class)
class ImagePdfUploadTest {

    private val contract = documentUploadConfig(DOCUMENT_SLOTS.first { it.docType == "airdrop_contract" })
    private val idCard = documentUploadConfig(DOCUMENT_SLOTS.first { it.docType == "id_card_form" })
    private val limit = 10 * 1024 * 1024

    @Test
    fun aPhotoForAPdfOnlySlotBecomesAOnePagePdf() {
        val file = imageUploadFile(noise(1200, 1600), contract)

        assertEquals("application/pdf", file.mimeType)
        assertTrue(file.fileName, file.fileName.endsWith(".pdf"))
        assertEquals("%PDF-", String(file.bytes.copyOfRange(0, 5), Charsets.US_ASCII))
        assertEquals(1, pageCount(file.bytes))
    }

    @Test
    fun anIncompressibleFullResolutionPhotoStaysInsideTheLimit() {
        // Random pixels are the worst case for PdfDocument's lossless images.
        val file = imageUploadFile(noise(3200, 3200), contract)

        assertEquals("application/pdf", file.mimeType)
        assertTrue("${file.bytes.size} bytes", file.bytes.size <= limit)
        assertEquals(1, pageCount(file.bytes))
    }

    @Test
    fun aPhotoForTheIdCardStaysAJpeg() {
        val file = imageUploadFile(noise(1200, 1600), idCard)

        assertEquals("image/jpeg", file.mimeType)
        assertTrue(file.fileName, file.fileName.endsWith(".jpg"))
    }

    @Test
    fun aLargeImageFileIsDecodedNearThePdfSizeNotAtFullResolution() {
        val source = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        val jpeg = ByteArrayOutputStream().use { out ->
            source.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }

        val decoded = requireNotNull(decodeImageForPdf(jpeg))

        assertEquals(2000, decoded.width)
        assertEquals(1500, decoded.height)
    }

    private fun noise(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        val random = Random(7)
        for (y in 0 until height) {
            for (x in 0 until width) row[x] = random.nextInt() or 0xFF000000.toInt()
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bitmap
    }

    private fun pageCount(pdf: ByteArray): Int {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("upload", ".pdf", context.cacheDir)
        try {
            file.writeBytes(pdf)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { return it.pageCount }
            }
        } finally {
            file.delete()
        }
    }
}
