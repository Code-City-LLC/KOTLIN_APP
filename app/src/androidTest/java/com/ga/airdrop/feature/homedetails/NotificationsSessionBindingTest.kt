package com.ga.airdrop.feature.homedetails

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.feature.shop.ShopCheckoutStore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsSessionBindingTest {

    @Test
    fun lateRefreshCannotReplaceStateAfterSessionChanges() {
        val session = AtomicReference(snapshot("account-a", 10, "session-a"))
        val response = CompletableDeferred<Result<List<AirdropNotification>>>()
        val source = FakeNotificationsDataSource(fetchNotifications = { _, _ -> response.await() })
        val viewModel = onMain { NotificationsViewModel(source, session::get) }
        session.set(snapshot("account-b", 11, "session-b"))
        response.complete(Result.success(listOf(notification("late-a"))))
        waitForIdle()

        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.loadingMore)
        assertEquals(emptyList<AirdropNotification>(), viewModel.state.value.items)
    }

    @Test
    fun lateLoadMoreCannotChangeRowsOrPaginationAfterSessionChanges() {
        val session = AtomicReference(snapshot("account-a", 20, "session-a"))
        val nextPage = CompletableDeferred<Result<List<AirdropNotification>>>()
        val calls = AtomicInteger()
        val firstPage = (1..20).map { notification("a-$it") }
        val source = FakeNotificationsDataSource(fetchNotifications = { _, _ ->
            if (calls.incrementAndGet() == 1) Result.success(firstPage) else nextPage.await()
        })
        val viewModel = onMain { NotificationsViewModel(source, session::get) }
        waitUntil { viewModel.state.value.loadedOnce }
        onMain { viewModel.loadMore() }
        waitUntil { viewModel.state.value.loadingMore }
        session.set(snapshot("account-b", 21, "session-b"))
        nextPage.complete(Result.success(listOf(notification("late-page"))))
        waitForIdle()

        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.loadingMore)
        assertEquals(emptyList<AirdropNotification>(), viewModel.state.value.items)
        assertFalse(viewModel.state.value.items.any { it.id == "late-page" })
    }

    @Test
    fun staleAccountCompletionCannotClearNewerAccountRefresh() {
        val session = AtomicReference(snapshot("account-a", 25, "session-a"))
        val accountA = CompletableDeferred<Result<List<AirdropNotification>>>()
        val accountB = CompletableDeferred<Result<List<AirdropNotification>>>()
        val calls = AtomicInteger()
        val source = FakeNotificationsDataSource(fetchNotifications = { _, _ ->
            if (calls.incrementAndGet() == 1) accountA.await() else accountB.await()
        })
        val viewModel = onMain { NotificationsViewModel(source, session::get) }

        session.set(snapshot("account-b", 26, "session-b"))
        onMain { viewModel.refresh() }
        accountA.complete(Result.success(listOf(notification("late-a"))))
        waitForIdle()

        assertTrue(viewModel.state.value.loading)
        accountB.complete(Result.success(listOf(notification("current-b"))))
        waitUntil { viewModel.state.value.loadedOnce }
        assertEquals(listOf("current-b"), viewModel.state.value.items.map { it.id })
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun staleCompletionBeforeSessionEmissionCannotSuppressReplacementRefresh() {
        val accountA = snapshot("account-a", 27, "session-a")
        val accountB = snapshot("account-b", 28, "session-b")
        val session = AtomicReference(accountA)
        val sessionFlow = MutableStateFlow(accountA)
        val firstResponse = CompletableDeferred<Result<List<AirdropNotification>>>()
        val calls = AtomicInteger()
        val source = FakeNotificationsDataSource(fetchNotifications = { _, _ ->
            if (calls.incrementAndGet() == 1) firstResponse.await()
            else Result.success(listOf(notification("current-b")))
        })
        val viewModel = onMain { NotificationsViewModel(source, session::get, sessionFlow) }

        session.set(accountB)
        firstResponse.complete(Result.success(listOf(notification("late-a"))))
        waitUntil { !viewModel.state.value.loading && viewModel.state.value.items.isEmpty() }

        sessionFlow.value = accountB
        waitUntil {
            calls.get() == 2 &&
                viewModel.state.value.loadedOnce &&
                viewModel.state.value.items.singleOrNull()?.id == "current-b"
        }

        assertEquals(listOf("current-b"), viewModel.state.value.items.map { it.id })
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun staleNotificationTapCannotMutateUiGlobalRouteOrServer() {
        val session = AtomicReference(snapshot("account-a", 30, "session-a"))
        val sessionFlow = MutableStateFlow(session.get())
        val markCalls = AtomicInteger()
        val fetchCalls = AtomicInteger()
        val item = notification(
            "account-a-row",
            route = "AuctionProductCheckoutView",
            referenceId = "account-a-product",
        )
        val source = FakeNotificationsDataSource(
            fetchNotifications = { _, _ ->
                if (fetchCalls.incrementAndGet() == 1) Result.success(listOf(item))
                else Result.success(emptyList())
            },
            markReadRequest = { _, _ -> markCalls.incrementAndGet(); Result.success(Unit) },
        )
        val viewModel = onMain { NotificationsViewModel(source, session::get, sessionFlow) }
        waitUntil { viewModel.state.value.loadedOnce }
        ShopCheckoutStore.pendingRef = "account-b-existing-ref"
        val replacement = snapshot("account-b", 31, "session-b")
        session.set(replacement)
        sessionFlow.value = replacement
        waitUntil {
            fetchCalls.get() == 2 &&
                viewModel.state.value.loadedOnce &&
                viewModel.state.value.items.isEmpty()
        }

        val route = onMain { viewModel.onNotificationTapped(item) }
        waitForIdle()

        assertNull(route)
        assertEquals(emptyList<AirdropNotification>(), viewModel.state.value.items)
        assertEquals(0, markCalls.get())
        assertEquals("account-b-existing-ref", ShopCheckoutStore.pendingRef)
        ShopCheckoutStore.pendingRef = null
    }

    @Test
    fun lateMarkReadCompletionCannotMutateReplacementSessionState() {
        val session = AtomicReference(snapshot("account-a", 35, "session-a"))
        val markResponse = CompletableDeferred<Result<Unit>>()
        val item = notification("account-a-row", route = "PackagesView")
        val source = FakeNotificationsDataSource(
            fetchNotifications = { _, _ -> Result.success(listOf(item)) },
            markReadRequest = { _, _ -> markResponse.await() },
        )
        val viewModel = onMain { NotificationsViewModel(source, session::get) }
        waitUntil { viewModel.state.value.loadedOnce }
        onMain { viewModel.onNotificationTapped(item) }
        val beforeCompletion = viewModel.state.value

        session.set(snapshot("account-b", 36, "session-b"))
        markResponse.complete(Result.success(Unit))
        waitForIdle()

        assertEquals(beforeCompletion, viewModel.state.value)
    }

    @Test
    fun currentSessionTapStillMarksReadAndResolvesCanonicalRoute() {
        val session = AtomicReference(snapshot("account-a", 40, "session-a"))
        val markCalls = AtomicInteger()
        val item = notification("current-row", route = "PackagesView")
        val source = FakeNotificationsDataSource(
            fetchNotifications = { _, _ -> Result.success(listOf(item)) },
            markReadRequest = { _, _ -> markCalls.incrementAndGet(); Result.success(Unit) },
        )
        val viewModel = onMain { NotificationsViewModel(source, session::get) }
        waitUntil { viewModel.state.value.loadedOnce }

        val route = onMain { viewModel.onNotificationTapped(item) }
        waitUntil { markCalls.get() == 1 }

        assertEquals(Routes.PACKAGES, route)
        assertEquals(true, viewModel.state.value.items.single().isRead)
    }

    @Test
    fun sameSessionRotationAcceptsLateRefreshWithoutStrandingLoading() {
        val session = AtomicReference(snapshot("token-a", 50, "session-a"))
        val response = CompletableDeferred<Result<List<AirdropNotification>>>()
        val source = FakeNotificationsDataSource(fetchNotifications = { _, _ -> response.await() })
        val viewModel = onMain { NotificationsViewModel(source, session::get) }

        session.set(snapshot("token-a-rotated", 51, "session-a"))
        response.complete(Result.success(listOf(notification("same-session-refresh"))))
        waitUntil { viewModel.state.value.loadedOnce }

        assertFalse(viewModel.state.value.loading)
        assertEquals("same-session-refresh", viewModel.state.value.items.single().id)
    }

    @Test
    fun sameSessionRotationAcceptsLatePageWithoutStrandingPagination() {
        val session = AtomicReference(snapshot("token-a", 60, "session-a"))
        val nextPage = CompletableDeferred<Result<List<AirdropNotification>>>()
        val calls = AtomicInteger()
        val firstPage = (1..20).map { notification("a-$it") }
        val source = FakeNotificationsDataSource(fetchNotifications = { _, _ ->
            if (calls.incrementAndGet() == 1) Result.success(firstPage) else nextPage.await()
        })
        val viewModel = onMain { NotificationsViewModel(source, session::get) }
        waitUntil { viewModel.state.value.loadedOnce }
        onMain { viewModel.loadMore() }
        waitUntil { viewModel.state.value.loadingMore }

        session.set(snapshot("token-a-rotated", 61, "session-a"))
        nextPage.complete(Result.success(listOf(notification("same-session-page"))))
        waitUntil { !viewModel.state.value.loadingMore }

        assertEquals(21, viewModel.state.value.items.size)
        assertEquals("same-session-page", viewModel.state.value.items.last().id)
    }

    private fun snapshot(token: String, revision: Long, sessionId: String) =
        AuthTokenStore.Snapshot(token, revision, sessionId)

    private fun notification(
        id: String,
        route: String? = null,
        referenceId: String? = null,
    ) = AirdropNotification(
        id = id,
        title = id,
        route = route,
        referenceId = referenceId,
    )

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(50)
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate() && System.currentTimeMillis() < deadline) {
            waitForIdle()
        }
        assertEquals("Timed out waiting for asynchronous state", true, predicate())
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value.set(block()) }
        return value.get()
    }
}

private class FakeNotificationsDataSource(
    private val fetchNotifications: suspend (Int, Int) -> Result<List<AirdropNotification>>,
    private val markReadRequest: suspend (String, AuthTokenStore.Snapshot) -> Result<Unit> =
        { _, _ -> Result.success(Unit) },
) : NotificationsDataSource {
    override suspend fun notifications(
        page: Int,
        limit: Int,
    ): Result<List<AirdropNotification>> = fetchNotifications(page, limit)

    override suspend fun markNotificationRead(
        id: String,
        expectedSession: AuthTokenStore.Snapshot,
    ): Result<Unit> = markReadRequest(id, expectedSession)
}
