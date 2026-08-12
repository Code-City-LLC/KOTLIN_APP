package com.ga.airdrop.feature.more

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Blank-form routing is independent of the profile-id lookup. Laravel derives
 * the customer from the bearer, so a transient /user/profile failure must not
 * disable Contract, 1583 or Authorization downloads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsMobileFormDownloadRecoveryTest {

    private val dispatcher = StandardTestDispatcher()
    private val contractSlot = DOCUMENT_SLOTS.first { it.docType == "airdrop_contract" }
    private val apiBase = "https://pre-staging.airdropja.com/api/v1"

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failed user-id fetch cannot disable the authenticated form route`() =
        runTest(dispatcher) {
            val repo = FakeRepo(userIdResults = listOf(Result.failure(RuntimeException("boom"))))
            val vm = DocumentsViewModel(repo, TestSessionBoundary())
            advanceUntilIdle()

            assertNull(vm.state.value.accountUserId)

            var opened: String? = null
            vm.openDocument(contractSlot, apiBase) { url, _ -> opened = url }
            assertEquals(
                "$apiBase/user/forms/contract/download",
                opened,
            )
            assertNull(vm.state.value.alert)
        }

    @Test
    fun `pull-to-refresh may bind account identity without changing the form URL`() =
        runTest(dispatcher) {
            val repo = FakeRepo(userIdResults = listOf(Result.failure(RuntimeException("boom"))))
            val vm = DocumentsViewModel(repo, TestSessionBoundary())
            advanceUntilIdle()

            var opened: String? = null
            vm.openDocument(contractSlot, apiBase) { url, _ -> opened = url }
            assertEquals("$apiBase/user/forms/contract/download", opened)

            repo.userIdResults = listOf(Result.success(4242))
            vm.refresh()
            advanceUntilIdle()
            assertEquals(4242, vm.state.value.accountUserId)

            opened = null
            vm.openDocument(contractSlot, apiBase) { url, _ -> opened = url }
            assertEquals("$apiBase/user/forms/contract/download", opened)
        }

    @Test
    fun `a blank uploaded-file URL falls back to the generated form`() = runTest(dispatcher) {
        val blankUploadedFile = MoreDocumentFile(
            id = 7,
            fileName = "contract.pdf",
            fileUrl = "   ",
            docType = contractSlot.docType,
            uploadStatus = true,
        )
        val repo = FakeRepo(
            userIdResults = listOf(Result.success(4242)),
            documents = mapOf(contractSlot.docType to blankUploadedFile),
        )
        val vm = DocumentsViewModel(repo, TestSessionBoundary())
        advanceUntilIdle()

        var opened: String? = null
        vm.openDocument(contractSlot, apiBase) { url, _ -> opened = url }

        assertEquals("$apiBase/user/forms/contract/download", opened)
        assertNull(vm.state.value.alert)
    }

    /**
     * The other half: ID Card really has no server-generated form, so it must
     * keep saying so rather than inheriting the new "try refreshing" message.
     */
    @Test
    fun `a slot with no legacy form still reports genuinely unavailable`() = runTest(dispatcher) {
        val repo = FakeRepo(userIdResults = listOf(Result.success(4242)))
        val vm = DocumentsViewModel(repo, TestSessionBoundary())
        advanceUntilIdle()

        val idCard = DOCUMENT_SLOTS.first { !it.docType.let { t ->
            t == "airdrop_contract" || t == "file_1583" || t == "authorization_form"
        } }
        vm.openDocument(idCard, apiBase) { _, _ -> }

        assertEquals("Not available", vm.state.value.alert?.first)
    }

    private class FakeRepo(
        var userIdResults: List<Result<Int?>>,
        private val documents: Map<String, MoreDocumentFile> = emptyMap(),
    ) : DocumentsRepository {
        val userIdCalls = AtomicInteger()

        override suspend fun currentUserId(expectedSession: AuthTokenStore.RequestProvenance): Result<Int?> {
            val index = userIdCalls.getAndIncrement()
            return userIdResults.getOrElse(index) { userIdResults.last() }
        }

        override suspend fun userDocuments(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(documents)

        override suspend fun uploadUserDocument(
            docType: String,
            fileName: String,
            mimeType: String,
            bytes: ByteArray,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.success(Unit)

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
