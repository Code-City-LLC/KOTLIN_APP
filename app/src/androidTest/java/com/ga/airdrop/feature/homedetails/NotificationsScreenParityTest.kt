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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ⚠️ MY VIEWPORT HYPOTHESIS WAS WRONG. READ THIS BEFORE THEORISING AGAIN.
 *
 * This class fails intermittently on CI and never locally. I believed the
 * cause was that it was the only parity suite asserting visibility without
 * `performScrollTo()` (4 bare assertions here, vs 25 and 12 in the sibling
 * suites), so the result depended on where the fold landed.
 *
 * **That is refuted.** With scrolling added, CI failed again — but with a
 * different error, and the difference is everything:
 *
 *     before:  "The component is not displayed!"
 *     after:   "could not find ANY node containing 'You’re all set!'"
 *
 * The node does not EXIST. It is not offscreen — the screen rendered the other
 * copy, "You’re all caught up.", which means `notificationsOn` read FALSE when
 * the test had just committed `master = true`.
 *
 * So this is a real state-resolution problem, not a layout one. The scrolling
 * stays because it turned a misleading error into an honest one, and that is
 * how the actual cause became visible — but it is NOT the fix.
 *
 * WHERE TO LOOK. `NotificationsScreenContent` reads
 * `NotificationAccountPreferences.currentMasterEnabled()`, which is
 * `currentMatrix()?.master == true`. `currentMatrix()` resolves the account
 * from `AuthTokenStore.requestProvenance(...)` and then reads inside
 * `AuthTokenStore.runWhileCurrentSession(expectedSessionId, expectedAccountId)`.
 * If that guard does not hold, it returns null — and `?.master == true`
 * converts null into **false**, which renders as "not enabled" rather than as
 * "could not read". An unreadable preference is indistinguishable from a
 * disabled one, which is the same conflation that has been fixed on two other
 * screens this week.
 *
 * The assertions below deliberately pin the PREFERENCE first and the rendered
 * copy second, so the next CI failure says which link broke instead of only
 * that the screen looked wrong.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsScreenParityTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
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
        NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = true))

        // Pin the PREFERENCE before the render. If this fails, the fault is in
        // account/session resolution, not in the composable — and CI will say
        // so instead of only reporting that the wrong copy appeared.
        assertTrue(
            "the committed master preference must be readable for the current account " +
                "before the screen is composed — if THIS fails the fault is in account/session " +
                "resolution, not in the composable",
            NotificationAccountPreferences.currentMasterEnabled(),
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
        NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = false))
        AuthTokenStore.save("account-b-token", authenticatedAccountId = 202)

        // Pin the PREFERENCE before the render. If this fails, the fault is in
        // account/session resolution, not in the composable — and CI will say
        // so instead of only reporting that the wrong copy appeared.
        assertTrue(
            "the committed master preference must be readable for the current account " +
                "before the screen is composed — if THIS fails the fault is in account/session " +
                "resolution, not in the composable",
            NotificationAccountPreferences.currentMasterEnabled(),
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
        NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = true))
        val lifecycleOwner = TestLifecycleOwner()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handle(Lifecycle.Event.ON_START)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }

        // Pin the PREFERENCE before the render. If this fails, the fault is in
        // account/session resolution, not in the composable — and CI will say
        // so instead of only reporting that the wrong copy appeared.
        assertTrue(
            "the committed master preference must be readable for the current account " +
                "before the screen is composed — if THIS fails the fault is in account/session " +
                "resolution, not in the composable",
            NotificationAccountPreferences.currentMasterEnabled(),
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

        NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = false))
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
