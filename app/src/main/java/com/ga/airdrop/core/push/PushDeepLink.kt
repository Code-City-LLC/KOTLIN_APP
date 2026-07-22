package com.ga.airdrop.core.push

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.ga.airdrop.core.auth.AuthTokenStore
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
    private const val KEY_PROVENANCE = "pendingProvenance"
    private const val KEY_SESSION_ID = "pendingSessionId"

    private const val PROVENANCE_PRE_LOGIN = "preLogin"
    private const val PROVENANCE_AUTHENTICATED = "authenticated"

    /** Swift AppDelegate staleness window — 30 minutes. */
    private const val STALE_MS = 30L * 60 * 1000

    private var prefs: SharedPreferences? = null

    internal sealed interface Provenance {
        data object PreLogin : Provenance
        data class Authenticated(val sessionId: String) : Provenance
    }

    internal data class PendingRoute(
        val route: String,
        val capturedAt: Long,
        val provenance: Provenance,
    )

    private val _pending = MutableStateFlow<PendingRoute?>(null)
    internal val pending: StateFlow<PendingRoute?> = _pending

    /** Restore a persisted (non-stale) pending route on cold start. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val store = prefs ?: return
        val restored = restore(store)
        if (restored == null) clear() else _pending.value = restored
    }

    fun capture(intent: Intent?) {
        if (intent == null) return
        fun raw(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k -> intent.getStringExtra(k)?.takeIf { it.isNotBlank() } }
        fun packageReference(): String? = PackageDeepLinkReference.select(
            packageIdCandidates = listOf(
                raw("package_id"),
                raw("packageId"),
                raw("packageID"),
            ),
            aliasCandidates = listOf(
                raw(AirdropMessagingService.EXTRA_REFERENCE_ID),
                raw("reference_id"),
                raw("referenceId"),
                raw("tracking_code"),
                raw("package_tracking_code"),
                raw("courier_number"),
                raw("package_courier_number"),
                raw("package_couirer_number"),
            ),
        )
        fun notificationOwnerId(): Int? = listOf(
            raw(AirdropMessagingService.EXTRA_NOTIFICATION_USER_ID),
            raw("user_id"),
            raw("userId"),
        ).firstNotNullOfOrNull(PackageDeepLinkReference::positiveId)
        fun allowsPackageResourceRoute(resolvedRoute: String): Boolean {
            if (!resolvedRoute.startsWith("packageDetails/")) return true
            val snapshot = AuthTokenStore.snapshot()
            val currentAccountId = snapshot.accountId ?: return true
            val payloadAccountId = notificationOwnerId() ?: return true
            return snapshot.token == null || currentAccountId == payloadAccountId
        }

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
        raw(AirdropMessagingService.EXTRA_DEEP_LINK)?.let { link ->
            runCatching { Uri.parse(link) }.getOrNull()?.let { uri ->
                if (AuthTokenStore.token == null && !isSafePreLoginDeepLink(uri)) return
                resolveDeepLink(
                    uri = uri,
                    siblingPackageReference = packageReference(),
                )?.let { resolvedRoute ->
                    if (!allowsPackageResourceRoute(resolvedRoute)) return
                    setPending(resolvedRoute)
                    return
                }
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
        val referenceId = if (
            com.ga.airdrop.feature.homedetails.notificationTargetsPackageDetails(
                routeName,
                notificationType,
            )
        ) {
            packageReference()
        } else {
            raw(
                AirdropMessagingService.EXTRA_REFERENCE_ID,
                "reference_id", "referenceId",
                "package_id", "packageId",
                "tracking_code", "courier_number",
            )
        }
        if (AuthTokenStore.token == null && isSensitivePreLoginRouteName(route)) return
        val resolvedRoute = resolve(route, referenceId)
        if (!allowsPackageResourceRoute(resolvedRoute)) return
        setPending(resolvedRoute)
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

    fun consume(snapshot: AuthTokenStore.Snapshot = AuthTokenStore.snapshot()): String? {
        val candidate = _pending.value ?: return null
        if (isExpired(candidate)) {
            clear()
            return null
        }
        return when (val provenance = candidate.provenance) {
            Provenance.PreLogin -> {
                if (snapshot.token == null) null else candidate.route.also { clear() }
            }
            is Provenance.Authenticated -> {
                if (snapshot.sessionId != provenance.sessionId) {
                    // A route bound to one authenticated account must never wait
                    // around to replay after a different account signs in.
                    if (snapshot.token != null) clear()
                    null
                } else {
                    candidate.route.also { clear() }
                }
            }
        }
    }

    fun clear() {
        _pending.value = null
        prefs?.edit()?.remove(KEY_ROUTE)?.remove(KEY_AT)
            ?.remove(KEY_PROVENANCE)?.remove(KEY_SESSION_ID)?.commit()
    }

    private fun setPending(route: String) {
        val snapshot = AuthTokenStore.snapshot()
        val requestProvenance = AuthTokenStore.requestProvenance(snapshot)
        val provenance = requestProvenance?.let {
            Provenance.Authenticated(it.sessionId)
        } ?: if (isSafePreLoginRoute(route)) Provenance.PreLogin else return
        val candidate = PendingRoute(route, System.currentTimeMillis(), provenance)
        _pending.value = candidate
        val editor = prefs?.edit()
            ?.putString(KEY_ROUTE, route)
            ?.putLong(KEY_AT, candidate.capturedAt)
        when (provenance) {
            Provenance.PreLogin -> editor
                ?.putString(KEY_PROVENANCE, PROVENANCE_PRE_LOGIN)
                ?.remove(KEY_SESSION_ID)
            is Provenance.Authenticated -> editor
                ?.putString(KEY_PROVENANCE, PROVENANCE_AUTHENTICATED)
                ?.putString(KEY_SESSION_ID, provenance.sessionId)
        }
        editor?.commit()
    }

    private fun restore(store: SharedPreferences): PendingRoute? {
        val route = store.getString(KEY_ROUTE, null) ?: return null
        val capturedAt = store.getLong(KEY_AT, 0L)
        if (isExpired(capturedAt)) return null
        val provenance = when (store.getString(KEY_PROVENANCE, null)) {
            PROVENANCE_PRE_LOGIN ->
                Provenance.PreLogin.takeIf { isSafePreLoginRoute(route) } ?: return null
            PROVENANCE_AUTHENTICATED -> {
                val persistedSessionId = store.getString(KEY_SESSION_ID, null) ?: return null
                val snapshot = AuthTokenStore.snapshot()
                // The encrypted auth store preserves this ID through process
                // restarts and token rotation, but fresh login replaces it.
                Provenance.Authenticated(persistedSessionId).takeIf {
                    snapshot.token == null || snapshot.sessionId == persistedSessionId
                } ?: return null
            }
            // Legacy route-only entries have no safe account provenance.
            else -> return null
        }
        return PendingRoute(route, capturedAt, provenance)
    }

    /**
     * Logged-out push payloads have no authoritative account owner. Only
     * static destinations may bind to the first later session; resource IDs,
     * carts/checkouts, and account-management surfaces fail closed.
     */
    internal fun isSafePreLoginRoute(route: String): Boolean = route in setOf(
        Routes.HOME,
        Routes.SHIPMENTS,
        Routes.SHOP,
        Routes.CONTACTS,
        Routes.MORE,
        Routes.PACKAGES,
        Routes.PAYMENTS,
        Routes.ORDERS,
        Routes.AUCTION,
        Routes.FEATURED_PRODUCTS,
        Routes.NOTIFICATIONS,
        Routes.NOTIFICATION_SETTINGS,
        Routes.GOLD_PRIORITY,
        Routes.AIRCOIN_HISTORY,
        Routes.WAREHOUSES,
        Routes.SERVICES,
        Routes.SALES_TAXES,
        Routes.CALCULATOR,
        Routes.DROP_ALERT,
        Routes.REFER_A_FRIEND,
        Routes.REFERRED_FRIENDS,
        Routes.INVITE_FRIEND,
        Routes.PROMOTIONS,
        Routes.DOCUMENTS,
        Routes.SHIPPING_RATES,
        Routes.RESTRICTED_ITEMS,
        Routes.FAQ,
        Routes.TERMS,
        Routes.PRIVACY,
    )

    private fun isSensitivePreLoginRouteName(route: String): Boolean =
        route.trim().lowercase() in setOf(
            "packagedetailsview",
            "paymentpackagedetailsview",
            "productpaymentdetailsview",
            "orderdetailsview",
            "authorizeduserdetailview",
            "auctionproductcheckoutview",
            "checkoutview",
            "addtocart",
        )

    private fun isSafePreLoginDeepLink(uri: Uri): Boolean =
        uri.scheme?.lowercase() in setOf("airdrop", "airdropexpress") &&
            uri.host?.lowercase() in setOf(
                "packages",
                "payments",
                "promotions",
                "refer",
                "referral",
                "support",
                "contact",
                "contacts",
            )

    private fun isExpired(candidate: PendingRoute): Boolean = isExpired(candidate.capturedAt)

    private fun isExpired(capturedAt: Long): Boolean {
        val age = System.currentTimeMillis() - capturedAt
        return capturedAt <= 0L || age < 0L || age > STALE_MS
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
     * Full custom-scheme host map plus the narrowly allowlisted AirDrop web
     * package rail. Both map to the same notification route resolver; external
     * HTTPS hosts and lookalikes never become in-app resource routes.
     */
    internal fun resolveDeepLink(
        uri: Uri,
        siblingPackageReference: String? = null,
    ): String? {
        resolveUri(uri)?.let { return it }
        val scheme = uri.scheme?.lowercase()
        val screenAndDetail = when {
            scheme in setOf("airdrop", "airdropexpress") -> {
                val detail = uri.pathSegments.firstOrNull { it.isNotEmpty() }
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
                screen to detail
            }
            scheme == "https" && isOwnedAirdropHost(uri.host) -> {
                val segments = uri.pathSegments.filter(String::isNotEmpty)
                val first = segments.firstOrNull()?.lowercase() ?: return null
                if (first !in setOf("package", "packages")) return null
                "PackageDetailsView" to segments.getOrNull(1)
            }
            else -> return null
        }
        val (screen, detail) = screenAndDetail
        val reference = if (screen == "PackageDetailsView") {
            PackageDeepLinkReference.routeReference(detail) ?: siblingPackageReference
        } else {
            detail
        }
        return com.ga.airdrop.feature.homedetails.resolveNotificationRoute(screen, reference)
    }

    private fun isOwnedAirdropHost(rawHost: String?): Boolean {
        val host = rawHost?.trim()?.lowercase() ?: return false
        return host == "airdropja.com" || host.endsWith(".airdropja.com")
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
