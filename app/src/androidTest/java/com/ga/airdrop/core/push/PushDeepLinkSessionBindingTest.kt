package com.ga.airdrop.core.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.navigation.consumePendingPushIfUnlocked
import com.ga.airdrop.core.network.TokenRefresher
import com.ga.airdrop.core.session.clearLocalUserSession
import com.ga.airdrop.core.session.SessionRestoreProbeProvider
import com.ga.airdrop.feature.shop.ShopCheckoutStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
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
    fun processRestoreUsesFreshSecondaryProcessWithStableSessionId() {
        val killedProbePid = startAndKillProbeProcess()
        AuthTokenStore.save("account-a-secret-token")
        val capturedSessionId = AuthTokenStore.snapshot().sessionId
        capture("PackagesView")
        val store = context.getSharedPreferences("push_deeplink", Context.MODE_PRIVATE)

        val restored = restoreInFreshProcess()
        val restoredPid = restored.getInt(SessionRestoreProbeProvider.KEY_PROCESS_ID)

        assertNotEquals(Process.myPid(), restoredPid)
        assertNotEquals(killedProbePid, restoredPid)
        assertTrue(restored.getBoolean(SessionRestoreProbeProvider.KEY_TOKEN_PRESENT))
        assertEquals(capturedSessionId, restored.getString(SessionRestoreProbeProvider.KEY_SESSION_ID))
        assertEquals(Routes.PACKAGES, restored.getString(SessionRestoreProbeProvider.KEY_ROUTE))
        assertTrue(store.all.values.none { it == "account-a-secret-token" })
        awaitProcessExit(restoredPid)
    }

    @Test
    fun processRestoreUnderDifferentAccountCannotReplay() {
        val killedProbePid = startAndKillProbeProcess()
        AuthTokenStore.save("account-a-token")
        capture("PackagesView")
        AuthTokenStore.save("account-b-token")

        val restored = restoreInFreshProcess()
        val restoredPid = restored.getInt(SessionRestoreProbeProvider.KEY_PROCESS_ID)

        assertNotEquals(Process.myPid(), restoredPid)
        assertNotEquals(killedProbePid, restoredPid)
        assertNull(restored.getString(SessionRestoreProbeProvider.KEY_ROUTE))
        awaitProcessExit(restoredPid)
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
    fun preLoginSensitiveResourceRoutesFailClosed() {
        listOf(
            "PackageDetailsView" to "package-a",
            "PaymentPackageDetailsView" to "payment-a",
            "OrderDetailsView" to "order-a",
            "AuthorizedUserDetailView" to "user-a",
            "AuctionProductCheckoutView" to "product-a",
        ).forEach { (route, referenceId) ->
            PushDeepLink.capture(
                Intent()
                    .putExtra(AirdropMessagingService.EXTRA_ROUTE, route)
                    .putExtra(AirdropMessagingService.EXTRA_REFERENCE_ID, referenceId),
            )
            assertNull(PushDeepLink.pending.value)
            AuthTokenStore.save("first-login-$referenceId")
            assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))
            AuthTokenStore.clear()
        }
    }

    @Test
    fun preLoginCheckoutCannotMutateGlobalCheckoutHandoff() {
        ShopCheckoutStore.pendingRef = "existing-session-ref"

        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_ROUTE, "AuctionProductCheckoutView")
                .putExtra(AirdropMessagingService.EXTRA_REFERENCE_ID, "unowned-product"),
        )

        assertNull(PushDeepLink.pending.value)
        assertEquals("existing-session-ref", ShopCheckoutStore.pendingRef)
        ShopCheckoutStore.pendingRef = null
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

    @Test
    fun loggedOutPaymentReturnFailsClosedInsteadOfBindingToNextAccount() {
        PushDeepLink.captureUri(
            Intent(Intent.ACTION_VIEW, Uri.parse(
                "airdrop://payment-success?session_id=cs_unowned",
            )),
        )

        assertNull(PushDeepLink.pending.value)
        AuthTokenStore.save("unrelated-later-account")
        assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun authenticatedPaymentCancellationRejectsReplacementAccount() {
        AuthTokenStore.save("payment-owner-token")
        PushDeepLink.captureUri(
            Intent(Intent.ACTION_VIEW, Uri.parse("airdrop://payment-cancelled-by-user")),
        )

        AuthTokenStore.save("replacement-account-token")

        assertNull(PushDeepLink.consume(AuthTokenStore.snapshot()))
        assertNull(PushDeepLink.pending.value)
    }

    private fun restoreInFreshProcess() = requireNotNull(
        context.contentResolver.call(
            Uri.parse("content://${context.packageName}.sessionrestoreprobe"),
            SessionRestoreProbeProvider.METHOD_RESTORE,
            null,
            null,
        ),
    )

    private fun startAndKillProbeProcess(): Int {
        val result = requireNotNull(
            context.contentResolver.call(
                Uri.parse("content://${context.packageName}.sessionrestoreprobe"),
                SessionRestoreProbeProvider.METHOD_PROCESS_ID,
                null,
                null,
            ),
        )
        val pid = result.getInt(SessionRestoreProbeProvider.KEY_PROCESS_ID)
        assertNotEquals(Process.myPid(), pid)
        Process.killProcess(pid)
        awaitProcessExit(pid)
        return pid
    }

    private fun awaitProcessExit(pid: Int) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val deadline = System.currentTimeMillis() + 5_000
        while (
            activityManager.runningAppProcesses.orEmpty().any { it.pid == pid } &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(25)
        }
        assertTrue(activityManager.runningAppProcesses.orEmpty().none { it.pid == pid })
    }

    private fun capture(route: String) {
        PushDeepLink.capture(
            Intent().putExtra(AirdropMessagingService.EXTRA_ROUTE, route),
        )
    }
}
