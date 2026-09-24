package com.ga.airdrop.feature.more

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Laravel StoreUserDocumentsRequest: the contract, the 1583 and the customs
 * authorization are `mimes:pdf` (the content is checked, not the name); the ID
 * card and TRN also take images. Every slot is `max:10240` KB. A file the
 * server is certain to refuse must never be uploaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentUploadRulesTest {

    private val dispatcher = StandardTestDispatcher()
    private val pdfOnlyTypes = listOf("airdrop_contract", "file_1583", "authorization_form")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a photo is never uploaded to a PDF-only slot`() = runTest(dispatcher) {
        val repo = RecordingRepo()
        val vm = DocumentsViewModel(repo, TestSessionBoundary())
        advanceUntilIdle()

        for (docType in pdfOnlyTypes) {
            stageAndCommit(vm, docType, "photo-1.jpg", "image/jpeg")
            val alert = vm.state.value.alert
            assertEquals("Upload failed", alert?.first)
            assertTrue("$docType: ${alert?.second}", alert?.second.orEmpty().contains("must be a PDF"))
            vm.dismissAlert()
        }

        assertEquals(emptyList<Pair<String, String>>(), repo.uploads)
    }

    @Test
    fun `PDFs still upload to PDF-only slots and images to the ID card and TRN`() = runTest(dispatcher) {
        val repo = RecordingRepo()
        val vm = DocumentsViewModel(repo, TestSessionBoundary())
        advanceUntilIdle()

        stageAndCommit(vm, "airdrop_contract", "contract.pdf", "application/pdf")
        stageAndCommit(vm, "id_card_form", "photo-2.jpg", "image/jpeg")
        stageAndCommit(vm, "trn", "trn.png", "image/png")

        assertEquals(
            listOf(
                "airdrop_contract" to "application/pdf",
                "id_card_form" to "image/jpeg",
                "trn" to "image/png",
            ),
            repo.uploads,
        )
    }

    @Test
    fun `the upload sheet hands back photos as PDFs exactly for the PDF-only slots`() {
        assertEquals(
            pdfOnlyTypes,
            DOCUMENT_SLOTS.filter { documentUploadConfig(it).imagesAsPdf }.map(DocumentSlot::docType),
        )
    }

    private fun TestScope.stageAndCommit(
        vm: DocumentsViewModel,
        docType: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ) {
        val slot = DOCUMENT_SLOTS.first { it.docType == docType }
        vm.stageUpload(requireNotNull(vm.claimUpload(slot)), fileName, mimeType, bytes)
        vm.commitPendingUpload(slot)
        advanceUntilIdle()
    }

    private class RecordingRepo : DocumentsRepository {
        val uploads = mutableListOf<Pair<String, String>>()

        override suspend fun currentUserId(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success<Int?>(1)

        override suspend fun userDocuments(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(emptyMap<String, MoreDocumentFile>())

        override suspend fun uploadUserDocument(
            docType: String,
            fileName: String,
            mimeType: String,
            bytes: ByteArray,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<Unit> {
            uploads += docType to mimeType
            return Result.success(Unit)
        }

        override suspend fun deleteUserDocument(
            identifier: String,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.success(Unit)
    }

    private class TestSessionBoundary : AuthenticatedSessionBoundary {
        private val owner = AuthenticatedSessionOwner(sessionId = "session-a", accountId = 1)
        private val ownerFlow = MutableStateFlow<AuthenticatedSessionOwner?>(owner)
        override val changes: Flow<AuthenticatedSessionOwner?> = ownerFlow
        override fun capture(): AuthenticatedSessionOwner? = owner
        override fun isCurrent(owner: AuthenticatedSessionOwner) = owner.sessionId == this.owner.sessionId
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            action(); return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) = action()
        override fun requestOwner(owner: AuthenticatedSessionOwner) =
            AuthenticatedRequestOwner(
                session = owner,
                provenance = AuthTokenStore.RequestProvenance(revision = 1L, sessionId = owner.sessionId),
            )
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = true
    }
}
