package com.ga.airdrop.feature.homedetails

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun handle(event: Lifecycle.Event) {
        registry.handleLifecycleEvent(event)
    }
}
