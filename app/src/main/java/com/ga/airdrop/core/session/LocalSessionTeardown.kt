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
import com.ga.airdrop.feature.cart.CheckoutFlowStore
import com.ga.airdrop.feature.cart.SavedForLaterStore
import com.ga.airdrop.feature.dropalert.DropAlertPreset
import com.ga.airdrop.feature.auth.OnboardingStore
import com.ga.airdrop.feature.shipments.clearShipmentsSessionCaches
import com.ga.airdrop.feature.shop.ShopRecentSearches
import com.ga.airdrop.feature.shop.clearShopSessionCaches

/** Account-scoped disclosure acceptance; it must never survive a user boundary. */
internal object LiveAgentChatConsentStore {
    private const val PREFS = "airdrop_live_chat"
    private const val AI_CONSENT_KEY = "AirdropAutoPilotAIConsent.v1"

    fun isAccepted(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(AI_CONSENT_KEY, false)

    fun accept(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AI_CONSENT_KEY, true)
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(AI_CONSENT_KEY)
            .apply()
    }
}

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
    CheckoutFlowStore.init(appContext)
    CheckoutFlowStore.clear()
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
    LiveAgentChatConsentStore.clear(appContext)

    PushRegistrar.onLogout()
}

/**
 * Explicit customer logout has one extra product rule: the next successful
 * login must enter the existing onboarding sequence before Home. The other
 * session boundaries — registration completion and account deletion — keep
 * their current routing and therefore continue to call [clearLocalUserSession].
 *
 * ⚠️ "rejected bearer" used to be listed here as a third boundary. It is not
 * one any more, and there is no such callsite in source: the app does not log
 * the customer out on a 401, a failed refresh, or a dropped connection
 * (Kemar 2026-07-26 — "if the customer doesn't log out, it never logs out").
 * Corrected after BrightHarbor #80131; a doc comment naming a boundary that
 * does not exist reads as an instruction to add one back.
 */
fun clearLocalUserSessionAfterCustomerLogout(context: Context) {
    OnboardingStore.requireAfterNextLogin(context)
    clearLocalUserSession(context)
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
