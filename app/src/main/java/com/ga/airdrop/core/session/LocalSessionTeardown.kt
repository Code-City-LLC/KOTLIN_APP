package com.ga.airdrop.core.session

import android.content.Context
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.prefs.DeliveryDefaultsStore
import com.ga.airdrop.core.prefs.ExchangeRateStore
import com.ga.airdrop.core.push.PushRegistrar
import com.ga.airdrop.core.push.QuietHoursStore
import com.ga.airdrop.core.security.BiometricGate
import com.ga.airdrop.feature.calculator.CalculatorHistory
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.cart.SavedForLaterStore
import com.ga.airdrop.feature.dropalert.DropAlertPreset
import com.ga.airdrop.feature.shipments.clearShipmentsSessionCaches
import com.ga.airdrop.feature.shop.ShopRecentSearches
import com.ga.airdrop.feature.shop.clearShopSessionCaches

/** Canonical local end-of-session sweep shared by every auth boundary. */
fun clearLocalUserSession(context: Context) {
    val appContext = context.applicationContext
    AuthTokenStore.clear()
    SessionStore.clear()
    SessionIdentity.clear()
    // #90: account-bound (and unresolved) pending push routes must not
    // survive an auth boundary — account A's route must never replay
    // under account B.
    com.ga.airdrop.core.push.PushDeepLink.clearAll()
    CartStore.init(appContext)
    CartStore.clear()
    SavedForLaterStore.init(appContext)
    SavedForLaterStore.clearAll()
    DeliveryDefaultsStore.clearAll()
    QuietHoursStore.clear(appContext)
    BiometricGate.reset()
    clearShipmentsSessionCaches()
    clearShopSessionCaches()
    ExchangeRateStore.clear()
    CalculatorHistory.clear()
    DropAlertPreset.clear()
    ShopRecentSearches.clear()
    clearLegacySessionCachePrefs(appContext)

    runCatching {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
    }
    PushRegistrar.onLogout()
}

fun clearLegacySessionCachePrefs(context: Context) {
    context.applicationContext.getSharedPreferences(SESSION_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .apply { SESSION_CACHE_KEYS.forEach(::remove) }
        .apply()
}

const val SESSION_CACHE_PREFS = "airdrop_cache"
val SESSION_CACHE_KEYS = listOf(
    "PACKAGE", "CART_PACKAGES", "PACKAGE_SHORTLIST",
    "figma.cart.items", "figma.packages.cache", "figma.packages.shortlist",
)
