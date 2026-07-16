package com.ga.airdrop.feature.more

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.FakeAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.SessionStore
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentsSessionBindingTest {

    @After
    fun tearDown() {
        SessionStore.onAuthenticatedSessionChanged(null)
    }

    @Test
    fun lateAccountAIdentityAndListCannotOverwriteAccountBReload() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = SwitchingDocumentsRepository()
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        repository.accountAIdentity.awaitEntered()

        onMain { viewModel.load() }
        repository.accountAList.awaitEntered()

        boundary.replace("session-b", accountId = 202)
        waitUntil {
            repository.identityCalls.get() == 2 &&
                repository.listCalls.get() == 2 &&
                viewModel.state.value.legacyUserId == 202 &&
                viewModel.state.value.files["trn"]?.fileName == "account-b.pdf" &&
                !viewModel.state.value.loading
        }

        repository.accountAIdentity.complete(Result.success(101))
        repository.accountAList.complete(Result.success(documentsFor("account-a.pdf")))
        waitUntil { boundary.rejectedApplyAttempts.get() > 0 }

        assertEquals(202, viewModel.state.value.legacyUserId)
        assertEquals("account-b.pdf", viewModel.state.value.files["trn"]?.fileName)
        assertEquals("session-a", repository.identityOwners.first().sessionId)
        assertEquals("session-b", repository.identityOwners.last().sessionId)
        assertEquals("session-a", repository.listOwners.first().sessionId)
        assertEquals("session-b", repository.listOwners.last().sessionId)
        assertTrue(repository.identityOwners.last().revision > repository.identityOwners.first().revision)
    }

    @Test
    fun stagedAccountAUploadHasZeroDispatchBeforeCollectorObservesReplacement() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = RecordingDocumentsRepository()
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        waitUntil { viewModel.state.value.legacyUserId == 101 }

        val slot = DOCUMENT_SLOTS.first { it.docType == "trn" }
        val claim = onMain { requireNotNull(viewModel.claimUpload(slot)) }
        onMain {
            viewModel.stageUpload(claim, "account-a.jpg", "image/jpeg", byteArrayOf(1, 0, 1))
        }
        assertEquals("account-a.jpg", viewModel.state.value.pendingUploads["trn"]?.fileName)

        boundary.replaceCurrent("session-b", accountId = 101)
        onMain { viewModel.commitPendingUpload(slot) }
        assertEquals(0, repository.uploadAttempts.get())
        assertEquals("account-a.jpg", viewModel.state.value.pendingUploads["trn"]?.fileName)

        boundary.emitCurrent()
        waitUntil { viewModel.state.value.pendingUploads.isEmpty() }
    }

    @Test
    fun accountAPickerCallbackCannotStageBytesOrErrorUnderAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = RecordingDocumentsRepository()
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        waitUntil { viewModel.state.value.legacyUserId == 101 }
        val slot = DOCUMENT_SLOTS.first { it.docType == "trn" }
        val accountAClaim = onMain { requireNotNull(viewModel.claimUpload(slot)) }

        boundary.replace("session-b", accountId = 202)
        waitUntil { viewModel.state.value.legacyUserId == 202 }
        onMain {
            viewModel.stageUpload(
                accountAClaim,
                "late-account-a.jpg",
                "image/jpeg",
                byteArrayOf(1, 0, 1),
            )
            viewModel.showUploadFailure(accountAClaim, "Account A picker failed")
        }

        assertTrue(viewModel.state.value.pendingUploads.isEmpty())
        assertEquals(null, viewModel.state.value.alert)
        assertEquals(0, repository.uploadAttempts.get())
    }

    @Test
    fun accountADeleteConfirmationCannotResolveOrDispatchUnderAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = RecordingDocumentsRepository()
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        onMain { viewModel.load() }
        waitUntil { viewModel.state.value.files["trn"] != null }
        val slot = DOCUMENT_SLOTS.first { it.docType == "trn" }
        val accountAClaim = onMain { requireNotNull(viewModel.claimDelete(slot)) }

        boundary.replace("session-b", accountId = 202)
        waitUntil { viewModel.state.value.legacyUserId == 202 }
        onMain { viewModel.delete(accountAClaim) }

        assertEquals(0, repository.deleteAttempts.get())
        assertEquals(null, viewModel.state.value.alert)
    }

    @Test
    fun documentOpenUsesCurrentLegacyIdentityAndRejectsPreCollectorReplacementGap() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = LegacyOnlyDocumentsRepository()
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        waitUntil { viewModel.state.value.legacyUserId == 101 }
        val slot = DOCUMENT_SLOTS.first { it.docType == "airdrop_contract" }
        val openedUrl = AtomicReference<String?>()

        onMain {
            viewModel.openDocument(slot, "https://legacy.example") { url, _ -> openedUrl.set(url) }
        }
        assertEquals(
            "https://legacy.example/api_download-contract-form.php?user_documenttype=101",
            openedUrl.get(),
        )

        openedUrl.set(null)
        boundary.replaceCurrent("session-b", accountId = 202)
        onMain {
            viewModel.openDocument(slot, "https://legacy.example") { url, _ -> openedUrl.set(url) }
        }
        assertEquals(null, openedUrl.get())

        boundary.emitCurrent()
        waitUntil {
            viewModel.state.value.ownerSessionId == "session-b" &&
                viewModel.state.value.legacyUserId == 202
        }
        onMain {
            viewModel.openDocument(slot, "https://legacy.example") { url, _ -> openedUrl.set(url) }
        }
        assertEquals(
            "https://legacy.example/api_download-contract-form.php?user_documenttype=202",
            openedUrl.get(),
        )
    }

    @Test
    fun delayedAccountADeleteHasZeroServerSideEffectsUnderAccountB() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = DelayedDeleteDocumentsRepository(boundary)
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        onMain { viewModel.load() }
        waitUntil { viewModel.state.value.files["trn"] != null }

        val slot = DOCUMENT_SLOTS.first { it.docType == "trn" }
        val claim = onMain { requireNotNull(viewModel.claimDelete(slot)) }
        onMain { viewModel.delete(claim) }
        repository.deleteClaim.awaitEntered()
        val accountARequest = requireNotNull(repository.deleteOwner.get())

        boundary.replaceCurrent("session-b", accountId = 202)
        assertTrue(accountARequest != boundary.currentRequestOwner()?.provenance)
        repository.releaseDelete()
        waitUntil { boundary.rejectedApplyAttempts.get() > 0 }

        assertEquals(1, repository.deleteAttempts.get())
        assertEquals(0, repository.serverDeleteCalls.get())
        boundary.emitCurrent()
        waitUntil { viewModel.state.value.legacyUserId == 202 }
    }

    @Test
    fun logoutResetsRowsPendingMutationsFlagsErrorsAndLegacyIdentity() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = ResetDocumentsRepository()
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        onMain { viewModel.load() }
        waitUntil {
            viewModel.state.value.legacyUserId == 101 &&
                viewModel.state.value.files["trn"] != null &&
                !viewModel.state.value.loading
        }

        val slot = DOCUMENT_SLOTS.first { it.docType == "trn" }
        onMain {
            val claim = requireNotNull(viewModel.claimUpload(slot))
            viewModel.stageUpload(claim, "account-a.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
            viewModel.commitPendingUpload(slot)
        }
        repository.uploadResult.awaitEntered()
        onMain { viewModel.refresh() }
        repository.refreshResult.awaitEntered()
        onMain { viewModel.showAlert("Old error", "Account A only") }
        assertEquals("trn", viewModel.state.value.uploadingType)
        assertTrue(viewModel.state.value.refreshing)
        assertTrue(viewModel.state.value.pendingUploads.isNotEmpty())

        boundary.replace(null, accountId = null)
        waitUntil { viewModel.state.value == DocumentsUiState() }

        repository.uploadResult.complete(Result.success(Unit))
        repository.refreshResult.complete(Result.success(documentsFor("late-account-a.pdf")))
        waitUntil { boundary.rejectedApplyAttempts.get() >= 2 }
        assertEquals(DocumentsUiState(), viewModel.state.value)
    }

    @Test
    fun logoutDuringInitialLoadClearsLoadingBeforeLateResponseReturns() {
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        val repository = InitialLoadDocumentsRepository()
        val viewModel = onMain { DocumentsViewModel(repository, boundary) }
        onMain { viewModel.load() }
        repository.loadResult.awaitEntered()
        assertTrue(viewModel.state.value.loading)

        boundary.replace(null, accountId = null)
        waitUntil { viewModel.state.value == DocumentsUiState() }

        repository.loadResult.complete(Result.success(documentsFor("late-account-a.pdf")))
        waitUntil { boundary.rejectedApplyAttempts.get() > 0 }
        assertEquals(DocumentsUiState(), viewModel.state.value)
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate() && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(20)
        }
        assertTrue("Timed out waiting for asynchronous state", predicate())
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value.set(block()) }
        return value.get()
    }
}

private class SwitchingDocumentsRepository : DocumentsRepository {
    val accountAIdentity = LateResult<Int?>()
    val accountAList = LateResult<Map<String, MoreDocumentFile>>()
    val identityCalls = AtomicInteger()
    val listCalls = AtomicInteger()
    val identityOwners = Collections.synchronizedList(mutableListOf<AuthTokenStore.RequestProvenance>())
    val listOwners = Collections.synchronizedList(mutableListOf<AuthTokenStore.RequestProvenance>())

    override suspend fun currentUserId(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Int?> {
        identityOwners += expectedSession
        return if (identityCalls.incrementAndGet() == 1) {
            accountAIdentity.awaitResult()
        } else {
            Result.success(202)
        }
    }

    override suspend fun userDocuments(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Map<String, MoreDocumentFile>> {
        listOwners += expectedSession
        return if (listCalls.incrementAndGet() == 1) {
            accountAList.awaitResult()
        } else {
            Result.success(documentsFor("account-b.pdf"))
        }
    }

    override suspend fun uploadUserDocument(
        docType: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteUserDocument(
        identifier: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = Result.success(Unit)
}

private open class RecordingDocumentsRepository : DocumentsRepository {
    val uploadAttempts = AtomicInteger()
    val deleteAttempts = AtomicInteger()

    override suspend fun currentUserId(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Int?> = Result.success(expectedSession.accountId)

    override suspend fun userDocuments(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Map<String, MoreDocumentFile>> = Result.success(documentsFor("current.pdf"))

    override suspend fun uploadUserDocument(
        docType: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> {
        uploadAttempts.incrementAndGet()
        return Result.success(Unit)
    }

    override suspend fun deleteUserDocument(
        identifier: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> {
        deleteAttempts.incrementAndGet()
        return Result.success(Unit)
    }
}

private class DelayedDeleteDocumentsRepository(
    private val boundary: FakeAuthenticatedSessionBoundary,
) : RecordingDocumentsRepository() {
    val serverDeleteCalls = AtomicInteger()
    val deleteOwner = AtomicReference<AuthTokenStore.RequestProvenance>()
    val deleteClaim = LateResult<Unit>()

    override suspend fun currentUserId(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Int?> = Result.success(boundary.capture()?.accountId)

    override suspend fun deleteUserDocument(
        identifier: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> {
        deleteAttempts.incrementAndGet()
        deleteOwner.set(expectedSession)
        return deleteClaim.awaitResult()
    }

    fun releaseDelete() {
        val stillCurrent = boundary.currentRequestOwner()?.provenance == deleteOwner.get()
        if (stillCurrent) serverDeleteCalls.incrementAndGet()
        deleteClaim.complete(
            if (stillCurrent) Result.success(Unit)
            else Result.failure(IllegalStateException("Stale authenticated session")),
        )
    }
}

private class ResetDocumentsRepository : RecordingDocumentsRepository() {
    val uploadResult = LateResult<Unit>()
    val refreshResult = LateResult<Map<String, MoreDocumentFile>>()
    private val listCalls = AtomicInteger()

    override suspend fun userDocuments(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Map<String, MoreDocumentFile>> =
        if (listCalls.incrementAndGet() == 1) {
            Result.success(documentsFor("account-a.pdf"))
        } else {
            refreshResult.awaitResult()
        }

    override suspend fun uploadUserDocument(
        docType: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = uploadResult.awaitResult()
}

private class InitialLoadDocumentsRepository : RecordingDocumentsRepository() {
    val loadResult = LateResult<Map<String, MoreDocumentFile>>()

    override suspend fun userDocuments(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Map<String, MoreDocumentFile>> = loadResult.awaitResult()
}

private class LegacyOnlyDocumentsRepository : RecordingDocumentsRepository() {
    override suspend fun userDocuments(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Map<String, MoreDocumentFile>> = Result.success(emptyMap())
}

private class LateResult<T> {
    private val entered = CountDownLatch(1)
    private val continuation = AtomicReference<Continuation<Result<T>>>()

    suspend fun awaitResult(): Result<T> = suspendCoroutine { next ->
        check(continuation.compareAndSet(null, next)) { "Late result already has a waiter" }
        entered.countDown()
    }

    fun awaitEntered() {
        assertTrue("Timed out waiting for request claim", entered.await(5, TimeUnit.SECONDS))
    }

    fun complete(result: Result<T>) {
        requireNotNull(continuation.getAndSet(null)).resume(result)
    }
}

private fun documentsFor(fileName: String): Map<String, MoreDocumentFile> = mapOf(
    "trn" to MoreDocumentFile(
        id = 1,
        fileName = fileName,
        fileUrl = "https://example.com/$fileName",
        docType = "trn",
        uploadStatus = true,
    ),
)
