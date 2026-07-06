package com.ga.airdrop.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun hasPostNotificationsPermission(context: Context): Boolean =
    canPostNotifications(
        sdkInt = Build.VERSION.SDK_INT,
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
    )

internal fun shouldRequestPostNotificationsPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasPostNotificationsPermission(context)

internal fun canPostNotifications(sdkInt: Int, permissionGranted: Boolean): Boolean =
    sdkInt < Build.VERSION_CODES.TIRAMISU || permissionGranted

internal fun defaultAirdropNotificationChannel(): NotificationChannel =
    NotificationChannel(
        AirdropMessagingService.CHANNEL_ID,
        "Airdrop",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        setShowBadge(true)
    }
