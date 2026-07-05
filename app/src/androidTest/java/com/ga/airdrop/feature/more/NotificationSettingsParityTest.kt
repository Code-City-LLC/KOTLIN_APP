package com.ga.airdrop.feature.more

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
        val profileUpdates = mutableListOf<Map<String, String?>>()
        val registration = AtomicReference<Pair<String, String?>>()
        val registerLatch = CountDownLatch(1)

        val viewModel = NotificationSettingsViewModel(
            updateProfile = { fields ->
                synchronized(profileUpdates) {
                    profileUpdates.add(fields)
                }
            },
            requestFcmToken = { onToken -> onToken(" test-fcm-token ") },
            registerFcmToken = { token, deviceInfo ->
                registration.set(token to deviceInfo)
                registerLatch.countDown()
            },
            deviceInfoProvider = { "ID: android-test, OS: 35" },
        )

        viewModel.setPackageMaster(context, true)
        viewModel.setChannel(context, { state, on -> state.copy(packagePush = on) }, true)

        assertTrue("Push enable should register the current FCM token", registerLatch.await(5, TimeUnit.SECONDS))
        val registered = registration.get()
        assertNotNull(registered)
        assertEquals("test-fcm-token", registered.first)
        assertEquals("ID: android-test, OS: 35", registered.second)
        val finalProfileUpdate = synchronized(profileUpdates) { profileUpdates.last() }
        assertEquals("0", finalProfileUpdate["email_notification"])
        assertEquals("0", finalProfileUpdate["sms_notification"])
        assertEquals("0", finalProfileUpdate["offers_notification"])
    }

    private fun setNotificationSettings(mode: ThemeController.Mode) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedEnabledNotificationPrefs(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                NotificationSettingsScreen(
                    onBack = {},
                    viewModel = NotificationSettingsViewModel(
                        updateProfile = {},
                        requestFcmToken = {},
                        registerFcmToken = { _, _ -> },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun seedEnabledNotificationPrefs(context: Context) {
        context.getSharedPreferences(NotificationSettingsViewModel.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("isNotifications", true)
            .putBoolean("packageMaster", true)
            .putBoolean("promosMaster", true)
            .apply()
    }

    private fun clearNotificationPrefs(context: Context) {
        context.getSharedPreferences(NotificationSettingsViewModel.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun assertSwiftRowGeometry() {
        val master = bounds("notification-master-row")
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

        assertClose(20f, verticalGap(master, packageSection), "Master-to-package gap")
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
