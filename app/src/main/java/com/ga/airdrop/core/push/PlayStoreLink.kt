package com.ga.airdrop.core.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ga.airdrop.feature.shop.launchExternalUrl

/**
 * Opens this app's Play Store listing — the Android answer to the "App update
 * available" broadcast.
 *
 * Laravel sends it (`AppUpdateBroadcastCommand:118`,
 * `NotificationType::APP_UPDATE_AVAILABLE => 'AppUpdateView'`) and iOS acts on
 * it (`AppDelegate.swift:1736`, `UIApplication.shared.open(appStoreURL)`).
 * Android had no mapping at all, so the one notification whose entire purpose is
 * to get the customer onto a new build did nothing when tapped.
 *
 * ⚠️ USES THE PACKAGE NAME AT RUNTIME, NOT A HARDCODED ID. The staging flavor
 * carries `applicationIdSuffix = ".staging"`, so a literal
 * "com.ga.airdrop.app" would send staging testers to a listing that is not the
 * app they are running. `context.packageName` is right in both flavors by
 * construction.
 */
fun openPlayStoreListing(context: Context): Boolean {
    val pkg = context.packageName

    // Prefer the Play Store app itself: market:// opens the listing directly and
    // skips the browser hop. It is absent on emulators and some devices, hence
    // the web fallback below rather than a silent no-op.
    val marketOpened = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (marketOpened) return true

    return launchExternalUrl(context, "https://play.google.com/store/apps/details?id=$pkg")
}
