package com.ga.airdrop.feature.more

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * An uploaded document's url is a Spaces PRE-SIGNED link good for 60 minutes
 * (UserDocumentResource, `url_expires_at` beside it). Opening one the list
 * already knows is dead must fetch a fresh list and open the NEW link.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsExpiredLinkTest {

    private val dispatcher = StandardTestDispatcher()
    private val contract = DOCUMENT_SLOTS.first { it.docType == "airdrop_contract" }
    private val apiBase = "https://pre-staging.airdropja.com/api/v1"
    private val oldUrl = "https://spaces.test/contract.pdf?X-Amz-Signature=old"
    private val newUrl = "https://spaces.test/contract.pdf?X-Amz-Signature=new"
    private val expired = OffsetDateTime.now().minusMinutes(5).toString()
    private val fresh = OffsetDateTime.now().plusMinutes(55).toString()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an expired link refreshes the list and opens the new link`() = runTest(dispatcher) {
        val repo = SequencedRepo(listOf(documents(oldUrl, expired), documents(newUrl, fresh)))
        val vm = DocumentsViewModel(repo, TestSessionBoundary())
        vm.load()
        advanceUntilIdle()

        val opened = mutableListOf<String>()
        vm.openDocument(contract, apiBase) { url, _ -> opened += url }
        advanceUntilIdle()

        assertEquals(listOf(newUrl), opened)
        assertEquals(2, repo.calls)
        assertEquals(newUrl, vm.state.value.files["airdrop_contract"]?.fileUrl)
    }

    @Test
    fun `a link with time left, or with no expiry, opens at once`() = runTest(dispatcher) {
        for (expiresAt in listOf(fresh, null)) {
            val repo = SequencedRepo(listOf(documents(oldUrl, expiresAt)))
            val vm = DocumentsViewModel(repo, TestSessionBoundary())
            vm.load()
            advanceUntilIdle()

            var opened: String? = null
            vm.openDocument(contract, apiBase) { url, _ -> opened = url }

            assertEquals(oldUrl, opened)
            assertEquals(1, repo.calls)
        }
    }

    @Test
    fun `a failed refresh still opens the old link`() = runTest(dispatcher) {
        val repo = SequencedRepo(listOf(documents(oldUrl, expired), Result.failure(RuntimeException("offline"))))
        val vm = DocumentsViewModel(repo, TestSessionBoundary())
        vm.load()
        advanceUntilIdle()

        val opened = mutableListOf<String>()
        vm.openDocument(contract, apiBase) { url, _ -> opened += url }
        advanceUntilIdle()

        assertEquals(listOf(oldUrl), opened)
        assertEquals(2, repo.calls)
    }

    @Test
    fun `a link within a minute of its expiry counts as expired`() {
        val now = OffsetDateTime.parse("2026-09-24T14:00:00-05:00")
        fun file(expiresAt: String?) = MoreDocumentFile(1, "c.pdf", oldUrl, "airdrop_contract", true, expiresAt)
        val nowMs = now.toInstant().toEpochMilli()

        assertTrue(file(now.plusSeconds(30).toString()).isUrlExpired(nowMs))
        assertTrue(file(now.minusMinutes(1).toString()).isUrlExpired(nowMs))
        assertFalse(file(now.plusMinutes(2).toString()).isUrlExpired(nowMs))
        assertFalse(file(null).isUrlExpired(nowMs))
        assertFalse(file("not a date").isUrlExpired(nowMs))
    }

    @Test
    fun `the document list keeps each link's expiry`() {
        val body = """{"success":true,"data":{"airdrop_contract":{"id":7,"file_name":"c.pdf",""" +
            """"file_url":"$oldUrl","doc_type":"airdrop_contract","url_expires_at":"2026-09-24T15:00:00-05:00"}}}"""
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        val files = runBlocking {
            MoreRepository(client = client, base = "https://example.com")
                .userDocuments(AuthTokenStore.RequestProvenance(revision = 1L, sessionId = "session-a"))
        }.getOrThrow()

        assertEquals("2026-09-24T15:00:00-05:00", files["airdrop_contract"]?.urlExpiresAt)
    }

    private fun documents(url: String, expiresAt: String?) = Result.success(
        mapOf("airdrop_contract" to MoreDocumentFile(7, "contract.pdf", url, "airdrop_contract", true, expiresAt)),
    )

    private class SequencedRepo(
        private val results: List<Result<Map<String, MoreDocumentFile>>>,
    ) : DocumentsRepository {
        var calls = 0

        override suspend fun currentUserId(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success<Int?>(1)

        override suspend fun userDocuments(expectedSession: AuthTokenStore.RequestProvenance) =
            results[minOf(calls++, results.lastIndex)]

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
