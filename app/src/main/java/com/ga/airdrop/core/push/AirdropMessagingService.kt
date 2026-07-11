package com.ga.airdrop.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ga.airdrop.MainActivity
import com.ga.airdrop.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point. Mirrors the Swift AppDelegate push handling: register the
 * device token with /device-tokens/register (via [PushRegistrar]), surface the
 * notification, and carry `route` + `referenceID` data through to the route
 * resolver. Live: the google-services plugin is enabled and
 * app/google-services.json is committed.
 */
class AirdropMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Always cache — a fresh install issues the token BEFORE login, and the
        // old early-return dropped it forever (no push ever reached the device).
        // PushRegistrar registers on an application-scoped coroutine, so the
        // POST also survives this short-lived service being destroyed.
        PushRegistrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Swift AirdropPushNotificationRouter parses deep fallback chains for
        // every field — a payload keyed "message"/"screen"/"package_id" must
        // not lose its title/body/route/reference on Android (round-3 sweep).
        fun data(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> message.data[key]?.takeIf { it.isNotBlank() } }
        val title = message.notification?.title
            ?: data("title", "notification_title", "message_title")
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: data("body", "message", "description", "message_description")
            ?: return
        val route = data("route", "screen", "navigate_to", "deep_link")
        val referenceId = data(
            "referenceID", "reference_id", "referenceId",
            "package_id", "packageId", "packageID",
            "tracking_code", "courier_number",
        )
        // Type-based backend pushes (payment_reminder, package_status_update…)
        // carry no route; forward the type so the tray tap can deep-link via
        // the same type→route map the in-app inbox uses (Audit#7 C3).
        val notificationType = data("type", "notification_type")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            route?.let { putExtra(EXTRA_ROUTE, it) }
            referenceId?.let { putExtra(EXTRA_REFERENCE_ID, it) }
            notificationType?.let { putExtra(EXTRA_NOTIFICATION_TYPE, it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            System.identityHashCode(message),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Quiet Hours (Swift AppDelegate willPresent parity): inside the window
        // the push is delivered SILENTLY — no heads-up/sound, but it still lands
        // in the shade (never dropped). Locally-scheduled back-in-stock alerts
        // the user opted into are exempt (no Kotlin producer yet — future-proof
        // guard). FCM is inert today, so this path is dormant until Firebase.
        val exemptLocal = message.data["local"] == LOCAL_NOTIFY_WHEN_IN_STOCK
        val silent = QuietHoursStore.isInQuietWindow(applicationContext) && !exemptLocal

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels(manager)
        val notification = NotificationCompat.Builder(
            this,
            if (silent) QUIET_CHANNEL_ID else CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_nav_home_filled)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH,
            )
            .apply { if (silent) setSilent(true) }
            .build()
        // Monotonic id — identityHashCode could collide and overwrite a
        // still-visible earlier notification.
        manager.notify(nextNotificationId.incrementAndGet(), notification)
    }

    // No local coroutine scope: token registration moved to PushRegistrar's
    // application scope precisely because this service's onDestroy used to
    // cancel the in-flight POST (rotated token silently never reached the
    // backend → stale token, dropped pushes).

    companion object {
        // "airdrop_alerts" replaces the legacy "airdrop_default" channel: a
        // channel's importance is frozen at creation, and DEFAULT importance
        // meant no heads-up banner ever (RN uses AndroidImportance.HIGH; iOS
        // shows [.banner, .sound]). The legacy channel is deleted on the next
        // channel creation so existing installs migrate.
        const val CHANNEL_ID = "airdrop_alerts"
        const val LEGACY_CHANNEL_ID = "airdrop_default"
        const val QUIET_CHANNEL_ID = "airdrop_quiet"
        const val LOCAL_NOTIFY_WHEN_IN_STOCK = "notifyWhenInStock"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_REFERENCE_ID = "referenceID"
        const val EXTRA_NOTIFICATION_TYPE = "notificationType"

        private val nextNotificationId = java.util.concurrent.atomic.AtomicInteger(
            (System.currentTimeMillis() % 100_000).toInt(),
        )

        /**
         * Also called from MainActivity at startup so the channels exist BEFORE
         * the first background (system-posted) push arrives — the manifest
         * meta-data points background notification-block messages at
         * [CHANNEL_ID] instead of Firebase's unnamed "Miscellaneous" fallback.
         */
        fun createChannels(manager: NotificationManager) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Airdrop", NotificationManager.IMPORTANCE_HIGH),
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    QUIET_CHANNEL_ID,
                    "Airdrop (quiet hours)",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
    }
}
