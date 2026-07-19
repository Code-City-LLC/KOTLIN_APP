package com.ga.airdrop.feature.shop

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ga.airdrop.R
import com.ga.airdrop.core.push.AirdropMessagingService

/**
 * Fires the "back in stock" local notification on the shared
 * `airdrop_alerts` channel. Tagged `local = notifyWhenInStock` semantics so
 * it aligns with the quiet-hours exemption AirdropMessagingService already
 * recognizes for this local producer.
 */
fun notifyBackInStock(context: Context, productId: Int, title: String = "") {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return // no runtime permission — the watch entry was already cleared
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    AirdropMessagingService.createChannels(manager)
    val name = title.trim().ifEmpty { "An item on your watch list" }
    val notification = androidx.core.app.NotificationCompat.Builder(
        context,
        AirdropMessagingService.CHANNEL_ID,
    )
        .setSmallIcon(R.drawable.ic_nav_home_filled)
        .setContentTitle("Back in stock")
        .setContentText("$name is back in stock — tap to shop before it sells out.")
        .setAutoCancel(true)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .build()
    NotificationManagerCompat.from(context).notify(2_000_000 + productId, notification)
}
