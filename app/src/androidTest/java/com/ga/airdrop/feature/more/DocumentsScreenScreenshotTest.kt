package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentsScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun documentCardUsesSwiftActionsGeometry() {
        setDocumentCard(mode = ThemeController.Mode.LIGHT, file = null)

        val cardBounds = compose.onNodeWithTag("documents-card-airdrop_contract")
            .getUnclippedBoundsInRoot()
        val actionsBounds = compose.onNodeWithTag("documents-actions-airdrop_contract")
            .getUnclippedBoundsInRoot()

        assertClose(335f, boundsWidth(cardBounds), "Documents card width")
        assertClose(303f, boundsWidth(actionsBounds), "Inset actions row width")
        assertClose(48f, boundsHeight(actionsBounds), "Inset actions row height")
    }

    @Test
    fun documentCardWithFileUsesSwiftFileRowGeometryLight() {
        setDocumentCard(mode = ThemeController.Mode.LIGHT, file = sampleFile)

        assertUploadedFileAndActionsGeometry()
        saveRootScreenshot("documents_card_swift_geometry_light.png")
    }

    @Test
    fun documentCardWithFileUsesSwiftFileRowGeometryDark() {
        setDocumentCard(mode = ThemeController.Mode.DARK, file = sampleFile)

        assertUploadedFileAndActionsGeometry()
        saveRootScreenshot("documents_card_swift_geometry_dark.png")
    }

    @Test
    fun documentCardShowsSwiftPendingUploadBeforeCommit() {
        setDocumentCard(
            mode = ThemeController.Mode.LIGHT,
            file = sampleFile,
            pendingUpload = PendingDocumentUpload(
                fileName = "new-trn.jpg",
                mimeType = "image/jpeg",
                bytes = ByteArray(2048),
            ),
        )

        compose.onNodeWithTag("documents-pending-upload-airdrop_contract").assertIsDisplayed()
        compose.onNodeWithTag("documents-commit-upload-airdrop_contract").assertIsDisplayed()
        compose.onNodeWithText("Upload Document").assertIsDisplayed()
        compose.onNodeWithText("JPG files, 2KB").assertIsDisplayed()
        saveRootScreenshot("documents_pending_upload_swift_stage.png")
    }

    @Test
    fun documentsInfoDialogUsesSwiftConfirmLabel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                DocumentsScreen(onBack = {}, onNavigate = {})
            }
        }
        compose.onNodeWithContentDescription("AirDrop Contract info").performClick()

        compose.onNodeWithText("Got it").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("OK").fetchSemanticsNodes().size)
    }

    @Test
    fun documentsScreenReloadsOnResumeLikeSwiftViewDidAppear() {
        val repository = CountingDocumentsRepository()
        lateinit var viewModel: DocumentsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = DocumentsViewModel(repository)
        }

        compose.setContent {
            AirdropTheme {
                DocumentsScreen(onBack = {}, onNavigate = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithTag("documents-pull-refresh").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.loadCount.get() >= 1 && !viewModel.state.value.loading
        }
    }

    @Test
    fun documentsViewModelRefreshUsesSameRepositoryPath() {
        val repository = CountingDocumentsRepository()
        lateinit var viewModel: DocumentsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = DocumentsViewModel(repository)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.refresh()
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.loadCount.get() >= 1 && !viewModel.state.value.refreshing
        }
    }

    @Test
    fun uploadUsesSwiftMultipartShapeShowsAlertAndReloads() {
        val repository = CountingDocumentsRepository()
        lateinit var viewModel: DocumentsViewModel
        val slot = DOCUMENT_SLOTS.first { it.docType == "file_1583" }
        val bytes = byteArrayOf(1, 5, 8, 3)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = DocumentsViewModel(repository)
            viewModel.stageUpload(slot, "form-1583.pdf", "application/pdf", bytes)
            viewModel.commitPendingUpload(slot)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.uploadCount.get() == 1 &&
                repository.loadCount.get() == 1 &&
                viewModel.state.value.pendingUploads["file_1583"] == null &&
                viewModel.state.value.uploadingType == null &&
                !viewModel.state.value.loading
        }

        val upload = repository.lastUpload.get()
        assertEquals("file_1583", upload?.docType)
        assertEquals("form-1583.pdf", upload?.fileName)
        assertEquals("application/pdf", upload?.mimeType)
        assertArrayEquals(bytes, upload?.bytes)
        assertEquals("Uploaded" to "1583 Form was uploaded.", viewModel.state.value.alert)
    }

    @Test
    fun stagedUploadWaitsForExplicitCommitLikeSwift() {
        val repository = CountingDocumentsRepository()
        lateinit var viewModel: DocumentsViewModel
        val slot = DOCUMENT_SLOTS.first { it.docType == "trn" }
        val bytes = byteArrayOf(7, 7, 7)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = DocumentsViewModel(repository)
            viewModel.stageUpload(slot, "trn-photo.jpg", "image/jpeg", bytes)
        }

        assertEquals(0, repository.uploadCount.get())
        assertEquals("trn-photo.jpg", viewModel.state.value.pendingUploads["trn"]?.fileName)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.commitPendingUpload(slot)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.uploadCount.get() == 1 &&
                repository.loadCount.get() == 1 &&
                viewModel.state.value.pendingUploads["trn"] == null &&
                viewModel.state.value.uploadingType == null
        }

        val upload = repository.lastUpload.get()
        assertEquals("trn", upload?.docType)
        assertEquals("trn-photo.jpg", upload?.fileName)
        assertEquals("image/jpeg", upload?.mimeType)
        assertArrayEquals(bytes, upload?.bytes)
    }

    @Test
    fun deletePrefersRemoteDocTypeLikeSwiftAndReloads() {
        val repository = CountingDocumentsRepository(
            documents = mapOf(
                "airdrop_contract" to sampleFile.copy(docType = "server_contract_type"),
            ),
        )
        lateinit var viewModel: DocumentsViewModel
        val slot = DOCUMENT_SLOTS.first { it.docType == "airdrop_contract" }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = DocumentsViewModel(repository)
            viewModel.load()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.loadCount.get() == 1 && !viewModel.state.value.loading
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.delete(slot)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.deleteCount.get() == 1 &&
                repository.loadCount.get() == 2 &&
                !viewModel.state.value.loading
        }

        assertEquals("server_contract_type", repository.lastDelete.get())
        assertEquals("Deleted" to "AirDrop Contract was removed.", viewModel.state.value.alert)
    }

    @Test
    fun deleteFallsBackToSlotTypeWhenRemoteDocTypeIsMissing() {
        val repository = CountingDocumentsRepository(
            documents = mapOf(
                "trn" to sampleFile.copy(docType = ""),
            ),
        )
        lateinit var viewModel: DocumentsViewModel
        val slot = DOCUMENT_SLOTS.first { it.docType == "trn" }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = DocumentsViewModel(repository)
            viewModel.load()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.loadCount.get() == 1 && !viewModel.state.value.loading
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.delete(slot)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repository.deleteCount.get() == 1 &&
                repository.loadCount.get() == 2 &&
                !viewModel.state.value.loading
        }

        assertEquals("trn", repository.lastDelete.get())
        assertEquals("Deleted" to "TRN was removed.", viewModel.state.value.alert)
    }

    private fun setDocumentCard(
        mode: ThemeController.Mode,
        file: MoreDocumentFile?,
        pendingUpload: PendingDocumentUpload? = null,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Box(Modifier.width(335.dp)) {
                    DocumentCard(
                        slot = DOCUMENT_SLOTS.first(),
                        file = file,
                        pendingUpload = pendingUpload,
                        uploading = false,
                        onInfo = {},
                        onDownload = {},
                        onView = {},
                        onDelete = {},
                        onUpload = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertUploadedFileAndActionsGeometry() {
        val fileBounds = compose.onNodeWithTag("documents-uploaded-file-row")
            .getUnclippedBoundsInRoot()
        val actionsBounds = compose.onNodeWithTag("documents-actions-airdrop_contract")
            .getUnclippedBoundsInRoot()

        assertClose(303f, boundsWidth(fileBounds), "Uploaded file row width")
        assertClose(56f, boundsHeight(fileBounds), "Uploaded file row height")
        assertClose(303f, boundsWidth(actionsBounds), "Inset actions row width")
        assertClose(48f, boundsHeight(actionsBounds), "Inset actions row height")
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private val sampleFile = MoreDocumentFile(
        id = 1,
        fileName = "Invoice.pdf",
        fileUrl = "https://example.com/invoice.pdf",
        docType = "airdrop_contract",
        uploadStatus = true,
    )

    private data class UploadRecord(
        val docType: String,
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray,
    )

    private class CountingDocumentsRepository(
        private val documents: Map<String, MoreDocumentFile> = emptyMap(),
    ) : DocumentsRepository {
        val loadCount = AtomicInteger()
        val uploadCount = AtomicInteger()
        val deleteCount = AtomicInteger()
        val lastUpload = AtomicReference<UploadRecord?>()
        val lastDelete = AtomicReference<String?>()

        override suspend fun userDocuments(): Result<Map<String, MoreDocumentFile>> {
            loadCount.incrementAndGet()
            return Result.success(documents)
        }

        override suspend fun uploadUserDocument(
            docType: String,
            fileName: String,
            mimeType: String,
            bytes: ByteArray,
        ): Result<Unit> {
            uploadCount.incrementAndGet()
            lastUpload.set(UploadRecord(docType, fileName, mimeType, bytes))
            return Result.success(Unit)
        }

        override suspend fun deleteUserDocument(identifier: String): Result<Unit> {
            deleteCount.incrementAndGet()
            lastDelete.set(identifier)
            return Result.success(Unit)
        }
    }
}
