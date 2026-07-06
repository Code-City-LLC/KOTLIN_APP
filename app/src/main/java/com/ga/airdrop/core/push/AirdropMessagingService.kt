package com.ga.airdrop.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ga.airdrop.MainActivity
import com.ga.airdrop.R
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.repo.MiscRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * FCM entry point. Mirrors the Swift AppDelegate push handling: register the
 * device token with /device-tokens/register, surface the notification, and
 * carry `route` + `referenceID` data through to the route resolver.
 *
 * Inactive until google-services.json is provided (the google-services plugin
 * is commented out in app/build.gradle.kts) — without Firebase init this
 * service is never instantiated.
 */
class AirdropMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (AuthTokenStore.token == null) return
        scope.launch {
            MiscRepository(ApiClient.service).registerFcmToken(
                deviceToken = token,
                deviceType = "android",
            )
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        val route = message.data["route"]
        val referenceId = message.data["referenceID"] ?: message.data["reference_id"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            route?.let { putExtra(EXTRA_ROUTE, it) }
            referenceId?.let { putExtra(EXTRA_REFERENCE_ID, it) }
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
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Airdrop", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                QUIET_CHANNEL_ID,
                "Airdrop (quiet hours)",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
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
                if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT,
            )
            .apply { if (silent) setSilent(true) }
            .build()
        manager.notify(System.identityHashCode(message), notification)
    }

    override fun onDestroy() {
        // Cancel the IO scope so in-flight token-registration coroutines don't
        // outlive the service — the scope used to leak (BUG_AUDIT C3).
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "airdrop_default"
        const val QUIET_CHANNEL_ID = "airdrop_quiet"
        const val LOCAL_NOTIFY_WHEN_IN_STOCK = "notifyWhenInStock"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_REFERENCE_ID = "referenceID"
    }
}
