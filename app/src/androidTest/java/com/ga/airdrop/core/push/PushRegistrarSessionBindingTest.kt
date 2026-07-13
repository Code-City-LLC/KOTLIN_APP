package com.ga.airdrop.core.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.prefs.NotificationAccountPreferences
import com.ga.airdrop.core.prefs.NotificationPreferenceMatrix
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushRegistrarSessionBindingTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.clear()
        PushRegistrar.initForTest(context) { true }
        context.getSharedPreferences(NotificationAccountPreferences.PREFS, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotificationAccountPreferences.init(context)
        awaitLogout()
    }

    @After
    fun tearDown() {
        awaitLogout()
        AuthTokenStore.clear()
    }

    @Test
    fun delayedAccountATokenAfterAccountBReplacementSendsZeroRegistration() {
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        val tokenRequested = CountDownLatch(1)
        val token = CompletableDeferred<String?>()
        val registrations = AtomicInteger()
        val completed = CountDownLatch(1)

        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { tokenRequested.countDown(); token.await() },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { completed.countDown() },
        )
        assertTrue(tokenRequested.await(5, TimeUnit.SECONDS))
        AuthTokenStore.save("account-b-token", authenticatedAccountId = 202)
        token.complete("late-account-a-fcm-token")
        assertTrue(completed.await(5, TimeUnit.SECONDS))

        assertEquals(0, registrations.get())
    }

    @Test
    fun currentSessionRegistersWithExactEventProvenance() {
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        val received = AtomicReference<AuthTokenStore.RequestProvenance>()
        val registered = CountDownLatch(1)

        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { "account-a-fcm-token" },
            registerRequest = { _, provenance ->
                received.set(provenance)
                registered.countDown()
                true
            },
        )

        assertTrue("Timed out waiting for registration", registered.await(5, TimeUnit.SECONDS))
        assertEquals(expected, received.get())
    }

    @Test
    fun identicalFcmTokenDoesNotDuplicateForSameAccountRelogin() {
        AuthTokenStore.save("same-bearer", authenticatedAccountId = 101)
        val accountA = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        val first = CountDownLatch(1)
        val registrations = AtomicInteger()
        val request: suspend (String, AuthTokenStore.RequestProvenance) -> Boolean = { _, _ ->
            registrations.incrementAndGet()
            first.countDown()
            true
        }
        PushRegistrar.registerForSessionWith(
            expected = accountA,
            force = false,
            tokenRequester = { "same-fcm-token" },
            registerRequest = request,
        )
        assertTrue(first.await(5, TimeUnit.SECONDS))

        AuthTokenStore.save("same-bearer", authenticatedAccountId = 101)
        val accountB = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        val completed = CountDownLatch(1)
        PushRegistrar.registerForSessionWith(
            expected = accountB,
            force = false,
            tokenRequester = { "same-fcm-token" },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { completed.countDown() },
        )

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(1, registrations.get())
    }

    @Test
    fun missingAccountIdentityFailsClosedBeforeTokenRequest() {
        AuthTokenStore.save("debug-seed-without-account")
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        val tokenRequests = AtomicInteger()
        val registrations = AtomicInteger()
        val completed = CountDownLatch(1)

        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { tokenRequests.incrementAndGet(); "token" },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { completed.countDown() },
        )

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(0, tokenRequests.get())
        assertEquals(0, registrations.get())
    }

    @Test
    fun masterOffDeletionCompletesBeforeRapidMasterOnRequestsToken() {
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        assertTrue(NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = false)))
        val deleteStarted = CountDownLatch(1)
        val releaseDelete = CompletableDeferred<Unit>()
        val enabled = CountDownLatch(1)
        val events = mutableListOf<String>()

        PushRegistrar.setDevicePushEnabledWith(
            expected = expected,
            enabled = false,
            deleteToken = {
                synchronized(events) { events += "delete-start" }
                deleteStarted.countDown()
                releaseDelete.await()
                synchronized(events) { events += "delete-finish" }
                Result.success(Unit)
            },
            tokenRequester = { error("OFF must not request a token") },
            registerRequest = { _, _ -> error("OFF must not register") },
        )
        assertTrue(NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = true)))
        PushRegistrar.setDevicePushEnabledWith(
            expected = expected,
            enabled = true,
            deleteToken = { error("ON must not delete") },
            tokenRequester = {
                synchronized(events) { events += "token-request" }
                "new-fcm-token"
            },
            registerRequest = { _, _ ->
                synchronized(events) { events += "register" }
                true
            },
            onComplete = { enabled.countDown() },
        )

        assertTrue(deleteStarted.await(5, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertFalse(synchronized(events) { events.contains("token-request") })
        releaseDelete.complete(Unit)
        assertTrue(enabled.await(5, TimeUnit.SECONDS))
        assertEquals(
            listOf("delete-start", "delete-finish", "token-request", "register"),
            synchronized(events) { events.toList() },
        )
    }

    @Test
    fun tokenReceivedWhileLoggedOutDoesNotSeedTheNextAccount() {
        AuthTokenStore.clear()
        PushRegistrar.onNewToken("logged-out-fcm-token")

        AuthTokenStore.save("account-b-token", authenticatedAccountId = 202)
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        val tokenRequests = AtomicInteger()
        val completed = CountDownLatch(1)
        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { tokenRequests.incrementAndGet(); "account-b-fcm-token" },
            registerRequest = { _, _ -> true },
            onComplete = { completed.countDown() },
        )

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(1, tokenRequests.get())
    }

    @Test
    fun persistedMasterFalseBlocksStartupAndNewTokenWithoutMirrorKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        assertTrue(NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = false)))
        val tokenRequests = AtomicInteger()
        val registrations = AtomicInteger()
        val startupComplete = CountDownLatch(1)

        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { tokenRequests.incrementAndGet(); "startup-token" },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { startupComplete.countDown() },
        )
        assertTrue(startupComplete.await(5, TimeUnit.SECONDS))

        PushRegistrar.onNewToken("rotated-while-off")
        val barrier = CountDownLatch(1)
        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { tokenRequests.incrementAndGet(); "barrier-token" },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { barrier.countDown() },
        )
        assertTrue(barrier.await(5, TimeUnit.SECONDS))

        assertEquals(0, tokenRequests.get())
        assertEquals(0, registrations.get())
        val mirrorKeys = context.getSharedPreferences("push_registrar", android.content.Context.MODE_PRIVATE)
            .all.keys.filter { it.startsWith("devicePushEnabled.") }
        assertTrue("PushRegistrar must not persist a second master setting", mirrorKeys.isEmpty())
    }

    @Test
    fun deniedAuthorizationBlocksStartupAndNewTokenUntilGrantedRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PushRegistrar.initForTest(context) { false }
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        assertTrue(NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = true)))
        val tokenRequests = AtomicInteger()
        val registrations = AtomicInteger()
        val deniedComplete = CountDownLatch(1)
        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { tokenRequests.incrementAndGet(); "denied-startup-token" },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { deniedComplete.countDown() },
        )
        assertTrue(deniedComplete.await(5, TimeUnit.SECONDS))
        PushRegistrar.onNewToken("denied-new-token")

        PushRegistrar.initForTest(context) { true }
        val grantedComplete = CountDownLatch(1)
        PushRegistrar.registerForSessionWith(
            expected = expected,
            force = true,
            tokenRequester = { tokenRequests.incrementAndGet(); "granted-retry-token" },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { grantedComplete.countDown() },
        )
        assertTrue(grantedComplete.await(5, TimeUnit.SECONDS))

        assertEquals(1, tokenRequests.get())
        assertEquals(1, registrations.get())
    }

    @Test
    fun tokenReceivedDuringQueuedOffIsNeitherCachedNorRegistered() {
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        val expected = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        assertTrue(NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = false)))
        val deleteStarted = CountDownLatch(1)
        val releaseDelete = CompletableDeferred<Unit>()
        val offComplete = CountDownLatch(1)
        PushRegistrar.setDevicePushEnabledWith(
            expected = expected,
            enabled = false,
            deleteToken = {
                deleteStarted.countDown()
                releaseDelete.await()
                Result.success(Unit)
            },
            tokenRequester = { error("OFF must not request a token") },
            registerRequest = { _, _ -> error("OFF must not register") },
            onComplete = { offComplete.countDown() },
        )
        assertTrue(deleteStarted.await(5, TimeUnit.SECONDS))

        PushRegistrar.onNewToken("late-during-off-token")
        releaseDelete.complete(Unit)
        assertTrue(offComplete.await(5, TimeUnit.SECONDS))

        assertTrue(NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = true)))
        val tokenRequests = AtomicInteger()
        val registrations = AtomicInteger()
        val onComplete = CountDownLatch(1)
        PushRegistrar.setDevicePushEnabledWith(
            expected = expected,
            enabled = true,
            deleteToken = { error("ON must not delete") },
            tokenRequester = { tokenRequests.incrementAndGet(); "fresh-after-off-token" },
            registerRequest = { _, _ -> registrations.incrementAndGet(); true },
            onComplete = { onComplete.countDown() },
        )

        assertTrue(onComplete.await(5, TimeUnit.SECONDS))
        assertEquals(1, tokenRequests.get())
        assertEquals(1, registrations.get())
    }

    private fun awaitLogout() {
        val complete = CountDownLatch(1)
        PushRegistrar.onLogoutWith(
            deleteToken = { Result.success(Unit) },
            onComplete = { complete.countDown() },
        )
        assertTrue(complete.await(5, TimeUnit.SECONDS))
    }
}
