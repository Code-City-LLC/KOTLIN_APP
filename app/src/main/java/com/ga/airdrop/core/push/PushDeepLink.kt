package com.ga.airdrop.core.push

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.SessionIdentity
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
    private const val KEY_OWNER = "pendingOwner"

    /**
     * Session provenance of the pending route (issue #90). A route is either
     * captured logged-out (binds once to the first authenticated account),
     * captured under a known account id, or captured under a bearer whose
     * account id is not yet known (consumable only until the next auth
     * boundary — teardown clears it). Never a token or token fingerprint.
     */
    internal const val OWNER_PRE_LOGIN = "pre_login"
    internal const val OWNER_UNKNOWN = "unknown_session"

    /** Swift AppDelegate staleness window — 30 minutes. */
    private const val STALE_MS = 30L * 60 * 1000

    private var prefs: SharedPreferences? = null

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    private var pendingOwner: String? = null

    /** Restore a persisted (non-stale) pending route on cold start. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val store = prefs ?: return
        val route = store.getString(KEY_ROUTE, null) ?: return
        val at = store.getLong(KEY_AT, 0L)
        val owner = store.getString(KEY_OWNER, null)
        // Fail closed on routes persisted without provenance (pre-#90 rows):
        // an ownerless authenticated capture could otherwise replay under a
        // different account after re-login.
        if (owner != null && System.currentTimeMillis() - at <= STALE_MS) {
            pendingOwner = owner
            _pending.value = route
        } else {
            store.edit().remove(KEY_ROUTE).remove(KEY_AT).remove(KEY_OWNER).apply()
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

    /**
     * One-shot consumption, owner-checked (issue #90): the pending route is
     * released only to the session it belongs to. A mismatch — including a
     * route owned by account A while account B (or no identity) is active —
     * purges the route instead of navigating. PRE_LOGIN capture binds once
     * to the first authenticated session that consumes it.
     */
    fun consume(): String? {
        val route = _pending.value ?: return null
        val hasBearer = !AuthTokenStore.token.isNullOrBlank()
        return when (val owner = pendingOwner) {
            // Provenance lost — fail closed.
            null -> { clearAll(); null }
            // Logged-out capture: binds once to the FIRST authenticated
            // session. While no bearer exists it stays pending (Swift's
            // 30-minute replay window), it is never released logged-out.
            OWNER_PRE_LOGIN, OWNER_UNKNOWN ->
                if (hasBearer) { clearAll(); route } else null
            // Account-bound: released only to its own session.
            SessionIdentity.current -> { clearAll(); route }
            // Cross-account (or identity unknown) — purge, never navigate.
            else -> { clearAll(); null }
        }
    }

    /** Canonical wipe — every auth boundary (logout, implicit teardown). */
    fun clearAll() {
        pendingOwner = null
        _pending.value = null
        prefs?.edit()?.remove(KEY_ROUTE)?.remove(KEY_AT)?.remove(KEY_OWNER)?.apply()
    }

    // internal: the #90 owner matrix is pinned by PushSessionBindingTest.
    internal fun setPending(route: String) {
        val owner = when {
            AuthTokenStore.token.isNullOrBlank() -> OWNER_PRE_LOGIN
            else -> SessionIdentity.current ?: OWNER_UNKNOWN
        }
        pendingOwner = owner
        _pending.value = route
        prefs?.edit()
            ?.putString(KEY_ROUTE, route)
            ?.putLong(KEY_AT, System.currentTimeMillis())
            ?.putString(KEY_OWNER, owner)
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
