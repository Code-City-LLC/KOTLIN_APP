package com.ga.airdrop.feature.homedetails

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

        compose.onNodeWithText("You’re all set!").assertIsDisplayed()
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

        compose.onNodeWithText("You’re all set!").assertIsDisplayed()
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
        compose.onNodeWithText("You’re all set!").assertIsDisplayed()

        NotificationAccountPreferences.commit(101, NotificationPreferenceMatrix(master = false))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()

        compose.onNodeWithText("You’re all caught up.").assertIsDisplayed()
    }

    @Test
    fun eligibleUpdateRendersUpdateActionAndUsesExistingRowTap() {
        val taps = AtomicInteger()
        val update = com.ga.airdrop.data.model.AirdropNotification(
            id = "update-row",
            title = "A new version is available",
            body = "Update Airdrop to get the latest improvements.",
            type = "app_update",
            payload = mapOf("platform" to "android", "latest_version" to "99.0"),
        )
        compose.setContent {
            AirdropTheme {
                NotificationsScreenContent(
                    state = NotificationsUiState(
                        items = listOf(update),
                        loadedOnce = true,
                        endReached = true,
                    ),
                    onBack = {},
                    onOpenSettings = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onNotificationTap = { taps.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("Update App").assertIsDisplayed()
        compose.onNodeWithTag(NotificationsTags.row("update-row")).performClick()
        assertEquals(1, taps.get())
    }

    @Test
    fun filteredFullPageRequestsTheNextPageInsteadOfShowingFalseEmptyState() {
        val loads = AtomicInteger()
        compose.setContent {
            AirdropTheme {
                NotificationsScreenContent(
                    state = NotificationsUiState(loadedOnce = true, endReached = false),
                    onBack = {},
                    onOpenSettings = {},
                    onRefresh = {},
                    onLoadMore = { loads.incrementAndGet() },
                    onNotificationTap = {},
                )
            }
        }
        compose.waitForIdle()
        assertEquals(1, loads.get())
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun handle(event: Lifecycle.Event) {
        registry.handleLifecycleEvent(event)
    }
}
