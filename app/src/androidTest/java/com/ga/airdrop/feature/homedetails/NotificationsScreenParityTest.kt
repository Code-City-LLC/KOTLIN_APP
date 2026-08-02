package com.ga.airdrop.feature.homedetails

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.prefs.NotificationAccountPreferences
import com.ga.airdrop.core.prefs.NotificationPreferenceMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ⚠️ THESE TESTS ONCE MADE REAL NETWORK CALLS. THAT WAS THE BUG.
 *
 * `AuthTokenStore.save()` calls `NotificationBadgeSync
 * .onAuthenticatedSessionChanged(...)` (AuthTokenStore.kt:126-129), so saving
 * a token ISSUES A LIVE AUTHENTICATED REQUEST. The fake token below therefore
 * fired a real `GET /user/notifications`, which 401'd; the interceptor
 * refreshed; the refresh 401'd; and the session was torn down mid-test —
 * correctly, since that bearer really is invalid.
 *
 * The damage then landed somewhere that looks unrelated:
 * `currentMasterEnabled()` reads inside `runWhileCurrentSession(...)`, which
 * now failed and returned null, and `?.master == true` turns null into FALSE.
 * The screen rendered "notifications are off" and these tests failed with the
 * wrong copy — with no mention of the network anywhere in them. CI-only,
 * because the async 401 lands inside the test window on 3 cores and after it
 * locally, and a DIFFERENT test each run.
 *
 * The fix is the injected `NotificationBadgeGateway`, bound in @Before. Two
 * earlier explanations of mine — viewport/fold, then generic state resolution —
 * were WRONG and are recorded here only so nobody re-derives them. The
 * `performScrollTo()` calls stay because they turned a misleading "not
 * displayed" into an honest "does not exist", which is what exposed the real
 * cause; they are not the fix.
 *
 * Two things this does NOT fix, both flagged upstream rather than papered over:
 * a token store performing network I/O as a side effect of a state mutation,
 * and `?.master == true` rendering an UNREADABLE preference as a disabled one.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsScreenParityTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * `commit()` returns false when the store is uninitialised or the account
     * id is not positive, and every test here ignored that return. A silently
     * failed commit makes the whole test meaningless — it would then assert a
     * screen state against a preference that was never written, and "fail" for
     * a reason that has nothing to do with the code under test.
     * BrightHarbor #180 hold 3A.
     */
    private fun assertPreferenceCommitted(
        accountId: Int,
        matrix: NotificationPreferenceMatrix,
    ) {
        assertTrue(
            "the preference commit for account $accountId must succeed, " +
                "otherwise this test proves nothing",
            NotificationAccountPreferences.commit(accountId, matrix),
        )
    }

    private val realGateway = com.ga.airdrop.core.push.NotificationBadgeSync.gateway
    private val probeCalls = java.util.concurrent.atomic.AtomicInteger(0)

    @Before
    fun isolateTheBadgeNetworkSideEffect() {
        // ⚠️ WITHOUT THIS, THESE TESTS HIT THE NETWORK.
        //
        // AuthTokenStore.save() calls NotificationBadgeSync
        // .onAuthenticatedSessionChanged(...), so saving the fake token below
        // fires a REAL GET /user/notifications with a bogus bearer. It 401s,
        // the interceptor refreshes, the refresh 401s, and the session is torn
        // down mid-test — after which the preference read's session guard fails
        // and returns null, which renders as "notifications off".
        //
        // That is why a test driving a pure composable, with no network of its
        // own, failed intermittently on CI with the wrong copy on screen.
        // BrightHarbor's ruling: inject the gateway rather than no-op the sync
        // or dodge the auth transition — the transition is what is under test.
        probeCalls.set(0)
        com.ga.airdrop.core.push.NotificationBadgeSync.gateway =
            com.ga.airdrop.core.push.NotificationBadgeGateway {
                probeCalls.incrementAndGet()
                null
            }
    }

    /**
     * Proves the seam is REAL rather than assumed: the fake must be the thing
     * that actually runs, and the session must survive it untouched.
     *
     * Without this, a substitution that silently failed would leave these tests
     * hitting the network exactly as before — and passing, because the flake is
     * intermittent. BrightHarbor #180 hold 2/3.
     */
    @Test
    fun theInjectedProbeRunsAndTheSessionSurvivesTheBadgeFetch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.clear()
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        val sessionAfterSave = AuthTokenStore.currentSessionId()

        // save() fires onAuthenticatedSessionChanged, so the probe must have
        // been asked — through the fake, never the network.
        compose.waitUntil(timeoutMillis = 20_000) { probeCalls.get() > 0 }
        assertTrue("the injected probe must be the one invoked", probeCalls.get() > 0)

        // No real 401, therefore no teardown: auth, session and account intact.
        assertEquals("the token must survive the badge probe", "account-a-token", AuthTokenStore.token)
        assertEquals(
            "the session generation must not be torn down and rebuilt",
            sessionAfterSave,
            AuthTokenStore.currentSessionId(),
        )
        assertEquals("the account provenance must be unchanged", 101, AuthTokenStore.snapshot().accountId)
    }

    @After
    fun tearDown() {
        com.ga.airdrop.core.push.NotificationBadgeSync.gateway = realGateway
        AuthTokenStore.clear()
    }

    @Test
    fun emptyStateReadsCurrentAccountsScopedMasterInsteadOfRemovedLegacyKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.clear()
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotificationAccountPreferences.init(context)
        assertPreferenceCommitted(101, NotificationPreferenceMatrix(master = true))

        // The account this test actually composes under — 202 in the
        // cross-account case, which is the whole point of that test.
        val expectedAccountId = 101
        // Pin the PREFERENCE before the render. If this fails, the fault is in
        // account/session resolution, not in the composable — and CI will say
        // so instead of only reporting that the wrong copy appeared.
        assertTrue(
            "the committed master preference must be readable for the current account " +
                "before the screen is composed — if THIS fails the fault is in account/session " +
                "resolution, not in the composable",
            NotificationAccountPreferences.currentMasterEnabled(),
        )
        assertEquals(
            "the preference must be scoped to the account under test, not another one",
            expectedAccountId,
            AuthTokenStore.snapshot().accountId,
        )
        compose.setContent {
            AirdropTheme {
                NotificationsScreenContent(
                    state = NotificationsUiState(loadedOnce = true),
                    onBack = {},
                    onOpenSettings = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onNotificationTap = {},
                )
            }
        }

        compose.onNodeWithText("You’re all set!").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("You’re all caught up.").assertDoesNotExist()
    }

    @Test
    fun differentAccountUsesDefaultMasterInsteadOfFirstAccountsOffSetting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.clear()
        context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotificationAccountPreferences.init(context)
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        assertPreferenceCommitted(101, NotificationPreferenceMatrix(master = false))
        AuthTokenStore.save("account-b-token", authenticatedAccountId = 202)

        // The account this test actually composes under — 202 in the
        // cross-account case, which is the whole point of that test.
        val expectedAccountId = 202
        // Pin the PREFERENCE before the render. If this fails, the fault is in
        // account/session resolution, not in the composable — and CI will say
        // so instead of only reporting that the wrong copy appeared.
        assertTrue(
            "the committed master preference must be readable for the current account " +
                "before the screen is composed — if THIS fails the fault is in account/session " +
                "resolution, not in the composable",
            NotificationAccountPreferences.currentMasterEnabled(),
        )
        assertEquals(
            "the preference must be scoped to the account under test, not another one",
            expectedAccountId,
            AuthTokenStore.snapshot().accountId,
        )
        compose.setContent {
            AirdropTheme {
                NotificationsScreenContent(
                    state = NotificationsUiState(loadedOnce = true),
                    onBack = {},
                    onOpenSettings = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onNotificationTap = {},
                )
            }
        }

        compose.onNodeWithText("You’re all set!").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("You’re all caught up.").assertDoesNotExist()
        assertEquals(false, NotificationAccountPreferences.load(101)?.master)
    }

    @Test
    fun emptyStateRefreshesScopedMasterWhenScreenResumes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.clear()
        AuthTokenStore.save("account-a-token", authenticatedAccountId = 101)
        context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotificationAccountPreferences.init(context)
        assertPreferenceCommitted(101, NotificationPreferenceMatrix(master = true))
        val lifecycleOwner = TestLifecycleOwner()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handle(Lifecycle.Event.ON_START)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }

        // The account this test actually composes under — 202 in the
        // cross-account case, which is the whole point of that test.
        val expectedAccountId = 101
        // Pin the PREFERENCE before the render. If this fails, the fault is in
        // account/session resolution, not in the composable — and CI will say
        // so instead of only reporting that the wrong copy appeared.
        assertTrue(
            "the committed master preference must be readable for the current account " +
                "before the screen is composed — if THIS fails the fault is in account/session " +
                "resolution, not in the composable",
            NotificationAccountPreferences.currentMasterEnabled(),
        )
        assertEquals(
            "the preference must be scoped to the account under test, not another one",
            expectedAccountId,
            AuthTokenStore.snapshot().accountId,
        )
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AirdropTheme {
                    NotificationsScreenContent(
                        state = NotificationsUiState(loadedOnce = true),
                        onBack = {},
                        onOpenSettings = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onNotificationTap = {},
                    )
                }
            }
        }
        compose.onNodeWithText("You’re all set!").performScrollTo().assertIsDisplayed()

        assertPreferenceCommitted(101, NotificationPreferenceMatrix(master = false))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()

        compose.onNodeWithText("You’re all caught up.").performScrollTo().assertIsDisplayed()
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun handle(event: Lifecycle.Event) {
        registry.handleLifecycleEvent(event)
    }
}
