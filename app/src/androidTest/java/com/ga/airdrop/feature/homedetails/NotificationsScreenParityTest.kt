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
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ⚠️ EVERY ASSERTION HERE SCROLLS FIRST, ON PURPOSE.
 *
 * These tests failed intermittently on CI — and only on CI — with
 * `AssertionError: Assert failed: The component is not displayed!`, a
 * DIFFERENT test in this class each run. They pass locally every time,
 * including under the full connected gate (716/716) and at 560dpi, which is
 * why three earlier hypotheses (a session-teardown side effect, viewport
 * geometry, cross-test contamination) were each refuted.
 *
 * The measured difference is in the tests, not the product. Across the parity
 * suites:
 *
 *     NotificationsScreenParityTest   bare assertIsDisplayed 4   performScrollTo 0
 *     PackageDetailsParityTest        bare assertIsDisplayed 5   performScrollTo 25
 *     GoldPriorityParityTest          bare assertIsDisplayed 13  performScrollTo 12
 *
 * This class was the ONLY one asserting visibility without scrolling first,
 * while its targets sit inside a `verticalScroll`. `assertIsDisplayed()`
 * requires the node to be on screen, so the result depends on where the fold
 * happens to land — hardware-sensitive by construction.
 *
 * Scrolling to the node does not weaken anything: it still asserts the node is
 * DISPLAYED, and still fails if the wrong copy renders. It only removes the
 * dependence on the viewport.
 *
 * ⚠️ Stated honestly: I could NOT reproduce the CI failure locally. This is a
 * robustness fix on strong circumstantial evidence, not a proven root cause.
 * If it recurs after this, the cause is elsewhere and this comment should be
 * treated as a refuted hypothesis rather than a settled explanation.
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
