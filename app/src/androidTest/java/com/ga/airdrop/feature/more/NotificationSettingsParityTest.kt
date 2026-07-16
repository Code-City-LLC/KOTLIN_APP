package com.ga.airdrop.feature.more

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.session.FakeAuthenticatedSessionBoundary
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.prefs.NotificationAccountPreferences
import com.ga.airdrop.core.push.PushRegistrar
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSettingsParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rowsUseSwiftGeometryAndIconColorsLight() {
        setNotificationSettings(mode = ThemeController.Mode.LIGHT)

        assertSwiftRowGeometry()
        assertIconContainsColor("notification-master-icon", DARK_ICON, "master bell is iconSelected in light mode")
        assertIconContainsColor("notification-package-email-icon", DARK_ICON, "email flap is iconSelected in light mode")
        assertIconContainsColor("notification-package-email-icon", ORANGE, "email body is orange in light mode")
        assertIconContainsColor("notification-package-sms-icon", DARK_ICON, "SMS bubble is iconSelected in light mode")
        assertIconContainsColor("notification-package-sms-icon", ORANGE, "SMS dots are orange in light mode")
        assertIconContainsColor("notification-package-push-icon", ORANGE, "Push bell is orange in light mode")
        compose.onNodeWithTag("quiet-hours-enable-row").assertDoesNotExist()
        compose.onNodeWithTag("quiet-hours-from-row").assertDoesNotExist()
        compose.onNodeWithTag("quiet-hours-until-row").assertDoesNotExist()
        compose.onNodeWithText("Enable quiet hours").assertDoesNotExist()
        saveRootScreenshot("notification_settings_swift_light.png")
    }

    @Test
    fun rowsUseSwiftGeometryAndIconColorsDark() {
        setNotificationSettings(mode = ThemeController.Mode.DARK)

        assertSwiftRowGeometry()
        assertIconContainsColor("notification-master-icon", WHITE_ICON, "master bell is iconSelected in dark mode")
        assertIconContainsColor("notification-package-email-icon", WHITE_ICON, "email flap is iconSelected in dark mode")
        assertIconContainsColor("notification-package-email-icon", ORANGE, "email body is orange in dark mode")
        assertIconContainsColor("notification-package-sms-icon", WHITE_ICON, "SMS bubble is iconSelected in dark mode")
        assertIconContainsColor("notification-package-sms-icon", ORANGE, "SMS dots are orange in dark mode")
        assertIconContainsColor("notification-package-push-icon", ORANGE, "Push bell is orange in dark mode")
        saveRootScreenshot("notification_settings_swift_dark.png")
    }

    @Test
    fun enablingPushReregistersFcmTokenLikeSwift() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val registration = AtomicReference<Pair<AuthTokenStore.RequestProvenance, Boolean>>()
        val registerLatch = CountDownLatch(1)
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)

        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { expected, enabled, onComplete ->
                registration.set(expected to enabled)
                onComplete(
                    Result.success(
                        if (enabled) PushRegistrar.DevicePushOutcome.RegistrationRequested
                        else PushRegistrar.DevicePushOutcome.Disabled,
                    ),
                )
                registerLatch.countDown()
            },
            hasNotificationPermission = { true },
            sessionBoundary = boundary,
        )

        viewModel.start(context)
        viewModel.setMaster(context, false)
        viewModel.setMaster(context, true)

        assertTrue("Push enable should register the current FCM token", registerLatch.await(5, TimeUnit.SECONDS))
        val registered = registration.get()
        assertNotNull(registered)
        assertEquals("session-a", registered.first.sessionId)
        assertEquals(101, registered.first.accountId)
        assertEquals(true, registered.second)
    }

    @Test
    fun accountAEventCannotMutateOrRegisterUnderAccountBAfterReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val boundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101)
        val deviceCommands = AtomicInteger()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> deviceCommands.incrementAndGet() },
            hasNotificationPermission = { true },
            sessionBoundary = boundary,
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.start(context)
            viewModel.setPackageMaster(context, true)
            viewModel.setChannel(context, { state, on -> state.copy(packagePush = on) }, true)
        }
        val accountACommands = deviceCommands.get()

        boundary.replaceCurrent("account-b", accountId = 202)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.setChannel(context, { state, on -> state.copy(promosPush = on) }, true)
        }

        assertEquals(accountACommands, deviceCommands.get())
        val prefs = context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
        assertEquals(true, prefs.getBoolean(accountKey(101, "packagePush"), false))
        boundary.emitCurrent()
        waitUntil {
            prefs.contains(accountKey(202, "packagePush")) &&
                !viewModel.state.value.packagePush
        }
        assertEquals(true, prefs.getBoolean(accountKey(101, "packagePush"), false))
        assertEquals(false, prefs.getBoolean(accountKey(202, "packagePush"), true))
    }

    @Test
    fun replacementDuringCandidateComputationPersistsNoStaleTrueForEitherAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val boundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101)
        val commands = AtomicInteger()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> commands.incrementAndGet() },
            hasNotificationPermission = { true },
            sessionBoundary = boundary,
        )
        viewModel.start(context)

        viewModel.setChannel(
            context = context,
            transform = { state, on ->
                boundary.replaceCurrent("account-b", accountId = 202)
                state.copy(packagePush = on)
            },
            on = true,
        )

        val prefs = context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
        assertEquals(false, prefs.getBoolean(accountKey(101, "packagePush"), true))
        assertEquals(0, commands.get())

        boundary.emitCurrent()
        waitUntil {
            prefs.contains(accountKey(202, "packagePush")) &&
                !viewModel.state.value.packagePush
        }
        assertEquals(false, prefs.getBoolean(accountKey(202, "packagePush"), true))
    }

    @Test
    fun replacementInsideSuccessfulCommitCallbackAppliesNoStaleUiOrDeviceCommand() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val boundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101)
        val commands = AtomicInteger()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> commands.incrementAndGet() },
            hasNotificationPermission = { true },
            commitPreferences = { _, _ ->
                boundary.replaceCurrent("account-b", accountId = 202)
                true
            },
            sessionBoundary = boundary,
        )
        viewModel.start(context)

        viewModel.setMaster(context, false)

        assertEquals(true, viewModel.state.value.master)
        assertEquals(0, commands.get())
        assertTrue(boundary.rejectedApplyAttempts.get() > 0)
        val prefs = context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
        boundary.emitCurrent()
        waitUntil {
            prefs.contains(accountKey(202, "isNotifications")) &&
                viewModel.state.value.master
        }
    }

    @Test
    fun replacementSessionPreservesAccountASettingsAndLoadsAccountBDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val boundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101)
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> },
            hasNotificationPermission = { true },
            sessionBoundary = boundary,
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.start(context)
            viewModel.setPackageMaster(context, true)
            viewModel.setChannel(context, { state, on -> state.copy(packageEmail = on) }, true)
        }
        waitUntil { viewModel.state.value.packageEmail }
        val prefs = context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
        assertEquals(true, prefs.getBoolean(accountKey(101, "packageMaster"), false))
        assertEquals(true, prefs.getBoolean(accountKey(101, "packageEmail"), false))

        boundary.replace("account-b", accountId = 202)
        waitUntil {
            prefs.contains(accountKey(202, "packageMaster")) &&
                prefs.contains(accountKey(202, "packageEmail")) &&
                !viewModel.state.value.packageMaster &&
                !viewModel.state.value.packageEmail
        }

        assertEquals(true, prefs.getBoolean(accountKey(101, "packageMaster"), false))
        assertEquals(true, prefs.getBoolean(accountKey(101, "packageEmail"), false))
        assertEquals(false, prefs.getBoolean(accountKey(202, "packageMaster"), true))
        assertEquals(false, prefs.getBoolean(accountKey(202, "packageEmail"), true))
    }

    @Test
    fun categoryPushCannotEnableDevicePushWhileMasterIsOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val boundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101)
        val commands = mutableListOf<Boolean>()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, enabled, onComplete ->
                commands.add(enabled)
                onComplete(Result.success(PushRegistrar.DevicePushOutcome.Disabled))
            },
            hasNotificationPermission = { true },
            sessionBoundary = boundary,
        )
        viewModel.start(context)

        viewModel.setMaster(context, false)
        viewModel.setPackageMaster(context, true)
        viewModel.setChannel(context, { state, on -> state.copy(packagePush = on) }, true)

        assertEquals(listOf(false), commands)
        assertEquals(false, viewModel.state.value.pushWanted())
    }

    @Test
    fun legacyValuesAreClaimedOnceAndSameAccountReloginRestoresThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .putBoolean("isNotifications", false)
            .putBoolean("packageMaster", true)
            .putBoolean("packageEmail", true)
            .commit()
        val boundary = FakeAuthenticatedSessionBoundary("account-a-1", initialAccountId = 101)
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> },
            hasNotificationPermission = { true },
            sessionBoundary = boundary,
        )
        viewModel.start(context)
        assertEquals(false, viewModel.state.value.master)
        assertEquals(true, viewModel.state.value.packageEmail)
        assertEquals(false, prefs.contains("packageEmail"))

        boundary.replace("account-a-2", accountId = 101)
        waitUntil { !viewModel.state.value.master && viewModel.state.value.packageEmail }

        boundary.replace("account-b", accountId = 202)
        waitUntil {
            prefs.contains(accountKey(202, "packageEmail")) &&
                viewModel.state.value.master &&
                !viewModel.state.value.packageEmail
        }
        assertEquals(true, prefs.getBoolean(accountKey(101, "packageEmail"), false))
        assertEquals(false, prefs.getBoolean(accountKey(202, "packageEmail"), true))
    }

    @Test
    fun categoryEditsWhileMasterUnchangedSendZeroDeviceCommands() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val commands = AtomicInteger()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> commands.incrementAndGet() },
            hasNotificationPermission = { true },
            sessionBoundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101),
        )

        viewModel.start(context)
        viewModel.setPackageMaster(context, true)
        viewModel.setChannel(context, { state, on -> state.copy(packageEmail = on) }, true)
        viewModel.setChannel(context, { state, on -> state.copy(packagePush = on) }, true)
        viewModel.setPromosMaster(context, true)
        viewModel.setChannel(context, { state, on -> state.copy(promosSms = on) }, true)

        assertEquals(0, commands.get())
        assertEquals(NotificationDeviceStatus.On, viewModel.state.value.deviceStatus)
    }

    @Test
    fun failedApplyPreservesRequestedIntentAndRetryCanRecover() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val attempts = AtomicInteger()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, enabled, onComplete ->
                if (attempts.incrementAndGet() == 1) {
                    onComplete(Result.failure(IllegalStateException("simulated failure")))
                } else {
                    onComplete(
                        Result.success(
                            if (enabled) PushRegistrar.DevicePushOutcome.RegistrationRequested
                            else PushRegistrar.DevicePushOutcome.Disabled,
                        ),
                    )
                }
            },
            hasNotificationPermission = { true },
            sessionBoundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101),
        )

        viewModel.start(context)
        viewModel.setMaster(context, false)
        assertEquals(false, viewModel.state.value.master)
        assertTrue(viewModel.state.value.deviceStatus is NotificationDeviceStatus.Failed)

        viewModel.retry(context)
        assertEquals(NotificationDeviceStatus.Off, viewModel.state.value.deviceStatus)
        assertEquals(false, viewModel.state.value.master)
        assertEquals(2, attempts.get())
    }

    @Test
    fun commitFailurePreservesPriorMatrixAndSendsZeroDeviceCommands() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val commands = AtomicInteger()
        val commitAttempts = AtomicInteger()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, enabled, onComplete ->
                commands.incrementAndGet()
                onComplete(
                    Result.success(
                        if (enabled) PushRegistrar.DevicePushOutcome.RegistrationRequested
                        else PushRegistrar.DevicePushOutcome.Disabled,
                    ),
                )
            },
            hasNotificationPermission = { true },
            commitPreferences = { accountId, matrix ->
                if (commitAttempts.incrementAndGet() == 1) false
                else NotificationAccountPreferences.commit(accountId, matrix)
            },
            sessionBoundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101),
        )
        viewModel.start(context)
        compose.setContent {
            AirdropTheme { NotificationSettingsScreen(onBack = {}, viewModel = viewModel) }
        }
        compose.waitForIdle()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.setMaster(context, false)
        }
        compose.waitForIdle()

        assertEquals(true, viewModel.state.value.master)
        assertEquals(NotificationDeviceStatus.PreferenceSaveFailed, viewModel.state.value.deviceStatus)
        assertEquals(0, commands.get())
        compose.onNodeWithText("Your notification preference was not saved. Change the setting again to retry.")
            .assertExists()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        val stored = NotificationAccountPreferences.load(101)
        assertEquals(true, stored?.master)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.setMaster(context, false)
        }
        compose.waitForIdle()

        assertEquals(false, viewModel.state.value.master)
        assertEquals(NotificationDeviceStatus.Off, viewModel.state.value.deviceStatus)
        assertEquals(1, commands.get())
        assertEquals(false, NotificationAccountPreferences.load(101)?.master)
    }

    @Test
    fun committedMatrixSurvivesStoreReinitializationAndSameAccountRelogin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val first = NotificationSettingsViewModel(
            setDevicePush = { _, _, onComplete ->
                onComplete(Result.success(PushRegistrar.DevicePushOutcome.Disabled))
            },
            hasNotificationPermission = { true },
            sessionBoundary = FakeAuthenticatedSessionBoundary("account-a-1", initialAccountId = 101),
        )
        first.start(context)
        first.setMaster(context, false)
        assertEquals(false, first.state.value.master)

        NotificationAccountPreferences.init(context)
        val restored = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> },
            hasNotificationPermission = { true },
            sessionBoundary = FakeAuthenticatedSessionBoundary("account-a-2", initialAccountId = 101),
        )
        restored.start(context)

        assertEquals(false, restored.state.value.master)
        assertEquals(NotificationDeviceStatus.Off, restored.state.value.deviceStatus)
    }

    @Test
    fun deniedPermissionShowsExistingAirdropDialogAndKeepsMasterIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearNotificationPrefs(context)
        val commands = AtomicInteger()
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> commands.incrementAndGet() },
            hasNotificationPermission = { false },
            sessionBoundary = FakeAuthenticatedSessionBoundary("account-a", initialAccountId = 101),
        )

        compose.setContent {
            AirdropTheme {
                NotificationSettingsScreen(onBack = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.setMaster(context, false)
            viewModel.setMaster(context, true)
        }
        compose.waitForIdle()

        assertEquals(true, viewModel.state.value.master)
        assertEquals(NotificationDeviceStatus.PermissionDenied, viewModel.state.value.deviceStatus)
        assertEquals(1, commands.get())
        compose.onNodeWithText("Notifications Disabled").assertExists()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
    }

    private fun setNotificationSettings(mode: ThemeController.Mode) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val boundary = FakeAuthenticatedSessionBoundary("session-a", initialAccountId = 101)
        seedEnabledNotificationPrefs(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        val viewModel = NotificationSettingsViewModel(
            setDevicePush = { _, _, _ -> },
            hasNotificationPermission = { true },
            sessionBoundary = boundary,
        )
        compose.setContent {
            AirdropTheme {
                NotificationSettingsScreen(
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun seedEnabledNotificationPrefs(context: Context) {
        context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("isNotifications", true)
            .putBoolean("packageMaster", true)
            .putBoolean("promosMaster", true)
            .commit()
    }

    private fun clearNotificationPrefs(context: Context) {
        context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun accountKey(accountId: Int, key: String): String =
        NotificationAccountPreferences.accountKey(accountId, key)

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate() && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for asynchronous state", predicate())
    }

    private fun assertSwiftRowGeometry() {
        val master = bounds("notification-master-row")
        val status = bounds("notification-sync-status")
        val packageSection = bounds("notification-package-section-row")
        val packageEmail = bounds("notification-package-email-row")
        val packageSms = bounds("notification-package-sms-row")
        val packagePush = bounds("notification-package-push-row")
        val promosSection = bounds("notification-promos-section-row")

        assertClose(60f, boundsHeight(master), "Master row height")
        assertClose(60f, boundsHeight(packageSection), "Package section row height")
        assertClose(56f, boundsHeight(packageEmail), "Package Email row height")
        assertClose(56f, boundsHeight(packageSms), "Package SMS row height")
        assertClose(56f, boundsHeight(packagePush), "Package Push row height")
        assertClose(60f, boundsHeight(promosSection), "Promotions section row height")

        assertClose(12f, verticalGap(master, status), "Master-to-status gap")
        assertClose(20f, verticalGap(status, packageSection), "Status-to-package gap")
        assertClose(12f, verticalGap(packageSection, packageEmail), "Package section-to-email gap")
        assertClose(12f, verticalGap(packageEmail, packageSms), "Package email-to-sms gap")
        assertClose(12f, verticalGap(packageSms, packagePush), "Package sms-to-push gap")
        assertClose(20f, verticalGap(packagePush, promosSection), "Package-to-promotions gap")

        assertClose(20f, iconStart("notification-master"), "Master icon leading inset")
        assertClose(28f, iconStart("notification-package-email"), "Sub-row icon leading inset")
        assertClose(24f, boundsWidth(bounds("notification-package-email-icon")), "Email icon width")
        assertClose(24f, boundsHeight(bounds("notification-package-email-icon")), "Email icon height")
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(
        tag,
        useUnmergedTree = true,
    ).getUnclippedBoundsInRoot()

    private fun iconStart(prefix: String): Float {
        val row = bounds("$prefix-row")
        val icon = bounds("$prefix-icon")
        return (icon.left - row.left).value
    }

    private fun assertIconContainsColor(tag: String, target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(
            tag,
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()

        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(green - targetGreen) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(blue - targetBlue) <= COLOR_TOLERANCE
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private fun verticalGap(first: androidx.compose.ui.unit.DpRect, second: androidx.compose.ui.unit.DpRect): Float =
        (second.top - first.bottom).value

    private companion object {
        const val ORANGE = 0xFFF15114.toInt()
        const val DARK_ICON = 0xFF292929.toInt()
        const val WHITE_ICON = 0xFFFFFFFF.toInt()
        const val COLOR_TOLERANCE = 22
    }
}
