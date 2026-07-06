package com.ga.airdrop.core.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.MainActivity
import com.ga.airdrop.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermissionParityTest {

    @Test
    fun postNotificationGateMatchesAndroidFreshInstallPermissionRules() {
        assertTrue(
            "Pre-Android 13 devices do not require the runtime notification permission",
            canPostNotifications(Build.VERSION_CODES.S_V2, permissionGranted = false),
        )
        assertFalse(
            "Android 13+ fresh installs cannot post notifications or launcher badges until granted",
            canPostNotifications(Build.VERSION_CODES.TIRAMISU, permissionGranted = false),
        )
        assertTrue(
            "Android 13+ can post notifications and launcher badges after permission is granted",
            canPostNotifications(Build.VERSION_CODES.TIRAMISU, permissionGranted = true),
        )
    }

    @Test
    fun defaultNotificationChannelKeepsLauncherBadgeEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = defaultAirdropNotificationChannel()

        manager.createNotificationChannel(channel)
        val stored = manager.getNotificationChannel(AirdropMessagingService.CHANNEL_ID)

        assertEquals(AirdropMessagingService.CHANNEL_ID, channel.id)
        assertEquals("Airdrop", channel.name)
        assertNotNull(stored)
        assertTrue("Launcher badges must stay enabled for the default FCM channel", stored.canShowBadge())
    }

    @Test
    fun grantedPermissionPathPostsBadgeEligibleNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantPostNotificationsPermissionIfNeeded(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(defaultAirdropNotificationChannel())

        val notificationId = 7318
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(
            context,
            AirdropMessagingService.CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_nav_home_filled)
            .setContentTitle("Badge proof")
            .setContentText("Permission granted")
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(1)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            manager.notify(notificationId, notification)
            val posted = waitForActiveNotification(manager, notificationId)

            assertNotNull("Notification should post once Android notification permission is granted", posted)
            assertEquals(1, posted!!.notification.number)
        } finally {
            manager.cancel(notificationId)
        }
    }

    private fun grantPostNotificationsPermissionIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    private fun waitForActiveNotification(manager: NotificationManager, id: Int) =
        (0 until 20).firstNotNullOfOrNull {
            manager.activeNotifications.firstOrNull { it.id == id }
                ?: run {
                    Thread.sleep(100)
                    null
                }
        }
}
