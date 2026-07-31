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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The three server-generated forms (Contract, 1583, Authorization) build their
 * download URL from the session user id. If `currentUserId` failed, the id
 * stayed null and every one of them reported:
 *
 *     "No download link is available for AirDrop Contract yet."
 *
 * Two separate defects behind one message:
 *
 *  1. `loadLegacyUserId` ran ONLY from init and had no onFailure. `refresh()`
 *     called `fetchDocuments` alone, so pull-to-refresh — the exact gesture a
 *     customer makes when a download says it is unavailable — never retried it.
 *     One flaky call at screen open killed all three downloads until the
 *     customer left the screen and came back.
 *
 *  2. The message is a claim about the DOCUMENT, and it was false. The form
 *     exists and the customer is entitled to it; we merely failed to fetch the
 *     id needed to address it. ID Card and TRN genuinely have no legacy form,
 *     so both cases have to stay tellable apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsLegacyDownloadRecoveryTest {

    private val dispatcher = StandardTestDispatcher()
    private val contractSlot = DOCUMENT_SLOTS.first { it.docType == "airdrop_contract" }
    private val legacyBase = "https://pre-staging.airdropja.com/airdrop/inc"

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `pull-to-refresh retries a failed user-id fetch and the download then works`() =
        runTest(dispatcher) {
            val repo = FakeRepo(userIdResults = listOf(Result.failure(RuntimeException("boom"))))
            val vm = DocumentsViewModel(repo, TestSessionBoundary())
            advanceUntilIdle()

            // First attempt failed: no id, so no URL can be built.
            assertNull(vm.state.value.legacyUserId)

            var opened: String? = null
            vm.openDocument(contractSlot, legacyBase) { url, _ -> opened = url }
            assertNull("nothing can open without an id", opened)

            // The gesture a customer actually makes.
            repo.userIdResults = listOf(Result.success(4242))
            vm.refresh()
            advanceUntilIdle()

            assertEquals(4242, vm.state.value.legacyUserId)
            vm.openDocument(contractSlot, legacyBase) { url, _ -> opened = url }
            assertEquals(
                "$legacyBase/api_download-contract-form.php?user_documenttype=4242",
                opened,
            )
        }

    @Test
    fun `a failed id fetch says it could not prepare the download, not that it does not exist`() =
        runTest(dispatcher) {
            val repo = FakeRepo(userIdResults = listOf(Result.failure(RuntimeException("boom"))))
            val vm = DocumentsViewModel(repo, TestSessionBoundary())
            advanceUntilIdle()

            vm.openDocument(contractSlot, legacyBase) { _, _ -> }

            val alert = vm.state.value.alert
            assertNotNull(alert)
            assertEquals("Couldn't prepare download", alert!!.first)
            assertTrue(
                "must point at the recovery that now exists: ${alert.second}",
                alert.second.contains("refresh"),
            )
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
        vm.openDocument(idCard, legacyBase) { _, _ -> }

        assertEquals("Not available", vm.state.value.alert?.first)
    }

    private class FakeRepo(
        var userIdResults: List<Result<Int?>>,
    ) : DocumentsRepository {
        val userIdCalls = AtomicInteger()

        override suspend fun currentUserId(expectedSession: AuthTokenStore.RequestProvenance): Result<Int?> {
            val index = userIdCalls.getAndIncrement()
            return userIdResults.getOrElse(index) { userIdResults.last() }
        }

        override suspend fun userDocuments(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(emptyMap<String, MoreDocumentFile>())

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
