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
        fun raw(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k -> intent.getStringExtra(k)?.takeIf { it.isNotBlank() } }

        // Two delivery paths land here with DIFFERENT extras:
        //  - foreground/data-only pushes: our service re-keys them to
        //    EXTRA_ROUTE/EXTRA_NOTIFICATION_TYPE/EXTRA_REFERENCE_ID;
        //  - background/killed-state notification-block pushes: ANDROID posts
        //    the tray entry itself (onMessageReceived never runs) and delivers
        //    the RAW backend data keys (screen/navigate_to/deep_link/
        //    notification_type/type/package_id/…) as launcher-intent extras.
        // The old capture only read the re-keyed extras, so every background
        // tap dead-ended on Home (Swift didReceive + RN NotificationRouter both
        // parse the raw keys).

        // deep_link is the FIRST priority in both references' resolution order.
        raw("deep_link")?.let { link ->
            runCatching { Uri.parse(link) }.getOrNull()?.let { uri ->
                resolveDeepLink(uri)?.let { setPending(it); return }
            }
        }

        val routeName = raw(AirdropMessagingService.EXTRA_ROUTE, "screen", "navigate_to")
        val notificationType = raw(
            AirdropMessagingService.EXTRA_NOTIFICATION_TYPE, "notification_type", "type",
        )
        // Route-less type-based pushes resolve through the same type→route map
        // the in-app inbox uses (Audit#7 C3).
        val route = routeName
            ?: notificationType?.let {
                com.ga.airdrop.feature.homedetails.routeNameForNotificationType(it)
            }
            ?: return
        val referenceId = raw(
            AirdropMessagingService.EXTRA_REFERENCE_ID,
            "reference_id", "referenceId",
            "package_id", "packageId",
            "tracking_code", "courier_number",
        )
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
            // Swift SceneDelegate:533 also accepts the *_by_user variants the
            // backend emits for user-initiated aborts (Audit #95 P42-C2).
            "payment-cancelled", "payment_cancelled", "payment-cancel", "payment_cancel",
            "payment-cancelled-by-user", "payment_cancelled_by_user" ->
                Routes.paymentCancelled()
            else -> null
        }
    }

    /**
     * Full airdrop:// host map for push `deep_link` payloads — Swift
     * AppDelegate.parseDeepLink (:1141-1184). Hosts map to the SAME screen
     * names the notification resolver already understands, so both entry
     * points share one routing table. Payment-return hosts keep their
     * dedicated pipeline via [resolveUri].
     */
    internal fun resolveDeepLink(uri: Uri): String? {
        resolveUri(uri)?.let { return it }
        if (uri.scheme?.lowercase() !in setOf("airdrop", "airdropexpress")) return null
        val detail = uri.path?.split('/')?.firstOrNull { it.isNotEmpty() }
        val screen = when (uri.host?.lowercase() ?: "") {
            "package" -> "PackageDetailsView"
            "upload-invoice" -> "InvoiceViewerScreen"
            "update-address" -> "ProfileView"
            "packages" -> "PackagesView"
            "payments" -> "PaymentsView"
            "promotions" -> "PromotionsView"
            "refer", "referral" -> "ReferView"
            "support", "contact", "contacts" -> "ContactsView"
            else -> uri.host ?: return null
        }
        return com.ga.airdrop.feature.homedetails.resolveNotificationRoute(screen, detail)
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
