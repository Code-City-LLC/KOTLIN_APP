package com.ga.airdrop.core.session

import android.content.Context
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.prefs.DeliveryDefaultsStore
import com.ga.airdrop.core.prefs.ExchangeRateStore
import com.ga.airdrop.core.push.PushRegistrar
import com.ga.airdrop.core.push.PushDeepLink
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
    // Clear while the old auth snapshot is still attributable. This prevents
    // account-bound push state from surviving into the next login.
    PushDeepLink.clear()
    // Also clears the fixed-key pickup/currency cache owned by this auth session.
    AuthTokenStore.clear()
    SessionStore.clear()
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
