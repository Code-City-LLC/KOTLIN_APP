package com.ga.airdrop.feature.shipments

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceExportPreservationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun sameNameConcurrentExportsPreserveEachOtherInRealDownloads() = runBlocking {
        val name = "airdrop-export-proof-${UUID.randomUUID()}.pdf"
        val sources = listOf("first document", "second document").map { text ->
            File.createTempFile("export-source-", ".pdf", context.cacheDir).apply { writeText(text) }
        }
        val owned = mutableListOf<Uri>()
        try {
            val destinations = sources.map { file ->
                async { saveInvoiceToDownloads(context.contentResolver, file, name).also { owned.add(it) } }
            }.awaitAll()
            assertNotEquals(destinations[0], destinations[1])
            destinations.zip(sources).forEach { (uri, source) ->
                assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.IS_PENDING), null, null, null)!!.use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            }
        } finally {
            // These are exclusively the new synthetic rows returned above.
            owned.forEach { context.contentResolver.delete(it, null, null) }
            sources.forEach(File::delete)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun aFailedWriteRemovesOnlyItsNewPendingRowAndPreservesPriorExport() = runBlocking {
        val provider = ExportProvider(failOpen = true)
        val resolver = resolver(provider)
        val source = source()
        try {
            val failure = runCatching { withContext(Dispatchers.Main) { saveInvoiceToDownloads(resolver, source, "same.pdf") } }
            assertTrue(failure.isFailure)
            assertEquals(listOf(provider.owned), provider.deleted)
            assertArrayEquals("previous export".toByteArray(), provider.previous.readBytes())
            assertEquals(1, provider.insertValues?.getAsInteger(MediaStore.Downloads.IS_PENDING))
            assertFalse(provider.published)
            assertTrue(provider.ioWasOffMain)
        } finally { source.delete(); provider.close() }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun publicationFailureCannotReportSuccessOrLeaveItsPartialRow() = runBlocking {
        val provider = ExportProvider(failPublish = true)
        val source = source()
        try {
            assertTrue(runCatching { saveInvoiceToDownloads(resolver(provider), source, "same.pdf") }.isFailure)
            assertEquals(listOf(provider.owned), provider.deleted)
            assertArrayEquals("previous export".toByteArray(), provider.previous.readBytes())
        } finally { source.delete(); provider.close() }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun successPublishesCompleteBytesAfterCopyOffMainThread() = runBlocking {
        val provider = ExportProvider()
        val source = source()
        try {
            val uri = withContext(Dispatchers.Main) { saveInvoiceToDownloads(resolver(provider), source, "same.pdf") }
            assertEquals(provider.owned, uri)
            assertArrayEquals(source.readBytes(), provider.output.readBytes())
            assertTrue(provider.published)
            assertTrue(provider.ioWasOffMain)
            assertTrue(provider.deleted.isEmpty())
        } finally { source.delete(); provider.close() }
    }

    @Test
    fun legacyPickerCreatesOneDocumentWithoutBroadStoragePermission() {
        val intent = ActivityResultContracts.CreateDocument("application/pdf").createIntent(context, "same.pdf")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals("same.pdf", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
    }

    private fun source() = File.createTempFile("export-test-", ".pdf", context.cacheDir).apply { writeText("new export") }

    private fun resolver(provider: ExportProvider): ContentResolver {
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        return ContentResolver.wrap(provider)
    }

    private inner class ExportProvider(
        private val failOpen: Boolean = false,
        private val failPublish: Boolean = false,
    ) : ContentProvider() {
        val owned: Uri = Uri.parse("content://media/external/downloads/741")
        val previous = source().apply { writeText("previous export") }
        val output = File.createTempFile("pending-export-", ".pdf", this@InvoiceExportPreservationTest.context.cacheDir)
        val deleted = mutableListOf<Uri>()
        var insertValues: ContentValues? = null
        var published = false
        var ioWasOffMain = true
        private fun recordThread() { ioWasOffMain = ioWasOffMain && Looper.myLooper() != Looper.getMainLooper() }
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri { recordThread(); insertValues = values; return owned }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            recordThread()
            if (failOpen) throw FileNotFoundException("synthetic write failure")
            return ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            recordThread()
            assertEquals(owned, uri)
            assertEquals("new export", output.readText())
            if (failPublish) return 0
            assertEquals(0, values?.getAsInteger(MediaStore.Downloads.IS_PENDING))
            published = true
            return 1
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int { recordThread(); deleted.add(uri); return 1 }
        override fun getType(uri: Uri) = "application/pdf"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
        fun close() { previous.delete(); output.delete() }
    }
}
