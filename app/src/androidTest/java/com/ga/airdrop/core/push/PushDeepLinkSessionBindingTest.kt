package com.ga.airdrop.core.push

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.navigation.consumePendingPushIfUnlocked
import com.ga.airdrop.core.network.TokenRefresher
import com.ga.airdrop.core.session.clearLocalUserSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushDeepLinkSessionBindingTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        AuthTokenStore.init(context)
        PushDeepLink.init(context)
        PushDeepLink.clear()
        AuthTokenStore.clear()
    }

    @After
    fun tearDown() {
        PushDeepLink.clear()
        AuthTokenStore.clear()
    }

    @Test
    fun authenticatedRouteReplaysOnceForSameSession() {
        AuthTokenStore.save("account-a-token")
        capture("PackagesView")

        assertEquals(Routes.PACKAGES, PushDeepLink.consume(AuthTokenStore.snapshot()))
        assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun accountSwitchRejectsAndClearsAccountBoundRoute() {
        AuthTokenStore.save("account-a-token")
        capture("PackagesView")

        AuthTokenStore.save("account-b-token")

        assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))
        assertNull(PushDeepLink.pending.value)
    }

    @Test
    fun processRestoreUsesStableSessionIdWithoutPersistingRawToken() {
        AuthTokenStore.save("account-a-secret-token")
        capture("PackagesView")
        val store = context.getSharedPreferences("push_deeplink", Context.MODE_PRIVATE)

        PushDeepLink.init(context)

        assertEquals(Routes.PACKAGES, PushDeepLink.consume(AuthTokenStore.snapshot()))
        assertTrue(store.all.values.none { it == "account-a-secret-token" })
    }

    @Test
    fun processRestoreUnderDifferentAccountCannotReplay() {
        AuthTokenStore.save("account-a-token")
        capture("PackagesView")
        AuthTokenStore.save("account-b-token")

        PushDeepLink.init(context)

        assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))
        assertNull(PushDeepLink.pending.value)
    }

    @Test
    fun preLoginRouteWaitsForFirstAuthenticatedUnlockedSession() {
        capture("PackagesView")
        assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))

        AuthTokenStore.save("first-login-token")
        assertNull(consumePendingPushIfUnlocked(false, AuthTokenStore.snapshot()))
        assertEquals(
            Routes.PACKAGES,
            consumePendingPushIfUnlocked(true, AuthTokenStore.snapshot()),
        )
        assertNull(consumePendingPushIfUnlocked(true, AuthTokenStore.snapshot()))
    }

    @Test
    fun foregroundRotationBeforeBiometricUnlockPreservesAndConsumesRouteOnce() {
        AuthTokenStore.save("account-a-token")
        val capturedSession = AuthTokenStore.snapshot()
        capture("PackagesView")

        TokenRefresher.applyForegroundRefresh(
            capturedSession,
            httpCode = null,
            newToken = "account-a-rotated-token",
        )
        AuthTokenStore.init(context)
        PushDeepLink.init(context)
        val rotatedSession = AuthTokenStore.snapshot()

        assertEquals(capturedSession.sessionId, rotatedSession.sessionId)
        assertNull(consumePendingPushIfUnlocked(false, rotatedSession))
        assertEquals(Routes.PACKAGES, consumePendingPushIfUnlocked(true, rotatedSession))
        assertNull(consumePendingPushIfUnlocked(true, rotatedSession))
    }

    @Test
    fun logoutTeardownClearsAccountBoundRoute() {
        AuthTokenStore.save("account-a-token")
        capture("PackagesView")

        clearLocalUserSession(context)
        AuthTokenStore.init(context)

        assertNull(PushDeepLink.pending.value)
        assertNull(AuthTokenStore.snapshot().sessionId)
    }

    @Test
    fun expiredRouteIsRejected() {
        AuthTokenStore.save("account-a-token")
        capture("PackagesView")
        context.getSharedPreferences("push_deeplink", Context.MODE_PRIVATE).edit()
            .putLong("pendingAt", System.currentTimeMillis() - 31L * 60 * 1000)
            .commit()

        PushDeepLink.init(context)

        assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun paymentReturnKeepsSessionBindingAndSessionId() {
        AuthTokenStore.save("account-a-token")
        PushDeepLink.captureUri(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                "airdrop://payment-success?session_id=cs_test_90",
            )),
        )

        assertNull(consumePendingPushIfUnlocked(false, AuthTokenStore.snapshot()))
        assertEquals(
            Routes.paymentReturn("cs_test_90"),
            consumePendingPushIfUnlocked(true, AuthTokenStore.snapshot()),
        )
    }

    private fun capture(route: String) {
        PushDeepLink.capture(
            Intent().putExtra(AirdropMessagingService.EXTRA_ROUTE, route),
        )
    }
}
