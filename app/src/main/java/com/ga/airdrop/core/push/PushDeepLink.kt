package com.ga.airdrop.core.push

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.ga.airdrop.core.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pending push navigation, consumed by AppRoot once the nav graph is up (and
 * only once a bearer exists — pushes tapped while logged out replay after
 * login). Android counterpart of FigmaRouteResolver.push(route:referenceID:)
 * plus SceneDelegate's payment-return URL handling.
 *
 * The pending route is ALSO persisted (Swift AirdropPushNotificationRouter
 * parity): a push tapped on a logged-out device must survive the process
 * being killed during the login flow. Swift keeps such routes for 30 minutes;
 * the same staleness window applies here.
 */
object PushDeepLink {

    private const val PREFS = "push_deeplink"
    private const val KEY_ROUTE = "pendingRoute"
    private const val KEY_AT = "pendingAt"

    /** Swift AppDelegate staleness window — 30 minutes. */
    private const val STALE_MS = 30L * 60 * 1000

    private var prefs: SharedPreferences? = null

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    /** Restore a persisted (non-stale) pending route on cold start. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val store = prefs ?: return
        val route = store.getString(KEY_ROUTE, null) ?: return
        val at = store.getLong(KEY_AT, 0L)
        if (System.currentTimeMillis() - at <= STALE_MS) {
            _pending.value = route
        } else {
            store.edit().remove(KEY_ROUTE).remove(KEY_AT).apply()
        }
    }

    fun capture(intent: Intent?) {
        if (intent == null) return
        // Route-less type-based pushes resolve through the same type→route map
        // the in-app inbox uses (Audit#7 C3).
        val route = intent.getStringExtra(AirdropMessagingService.EXTRA_ROUTE)
            ?: intent.getStringExtra(AirdropMessagingService.EXTRA_NOTIFICATION_TYPE)?.let {
                com.ga.airdrop.feature.homedetails.routeNameForNotificationType(it)
            }
            ?: return
        val referenceId = intent.getStringExtra(AirdropMessagingService.EXTRA_REFERENCE_ID)
        setPending(resolve(route, referenceId))
    }

    /**
     * Stripe payment-return deeplinks (VIEW intents carry a data Uri, never
     * the FCM extras, so this cannot clash with [capture]). Swift parity:
     * SceneDelegate.swift:432 handles the same airdrop:// return URLs.
     */
    fun captureUri(intent: Intent?) {
        val uri = intent?.data ?: return
        resolveUri(uri)?.let(::setPending)
    }

    fun consume(): String? = _pending.value.also {
        _pending.value = null
        prefs?.edit()?.remove(KEY_ROUTE)?.remove(KEY_AT)?.apply()
    }

    private fun setPending(route: String) {
        _pending.value = route
        prefs?.edit()
            ?.putString(KEY_ROUTE, route)
            ?.putLong(KEY_AT, System.currentTimeMillis())
            ?.apply()
    }

    /** airdrop://payment-success?session_id=… → nav route, else null. */
    internal fun resolveUri(uri: Uri): String? {
        if (uri.scheme?.lowercase() !in setOf("airdrop", "airdropexpress")) return null
        return when (uri.host?.lowercase()) {
            "payment-success", "payment_success", "payment-complete", "payment_complete" ->
                Routes.paymentReturn(uri.getQueryParameter("session_id"))
            "payment-cancelled", "payment_cancelled", "payment-cancel", "payment_cancel" ->
                Routes.paymentCancelled()
            else -> null
        }
    }

    /**
     * Delegates to the app's single route resolver (the same map notification
     * taps use) — the old private 13-route copy silently dead-ended every
     * other Swift route on Notifications (round-3 sweep).
     */
    private fun resolve(route: String, referenceId: String?): String =
        com.ga.airdrop.feature.homedetails.resolveNotificationRoute(route, referenceId)
            ?: Routes.NOTIFICATIONS
}
