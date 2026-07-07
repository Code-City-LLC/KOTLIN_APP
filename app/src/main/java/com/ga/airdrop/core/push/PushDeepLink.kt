package com.ga.airdrop.core.push

import android.content.Intent
import android.net.Uri
import com.ga.airdrop.core.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pending push navigation, consumed by AppRoot once the nav graph is up.
 * Android counterpart of FigmaRouteResolver.push(route:referenceID:) plus
 * SceneDelegate's payment-return URL handling (Stripe hosted-checkout
 * redirects back via `airdrop://payment-success?session_id=…`).
 */
object PushDeepLink {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun capture(intent: Intent?) {
        val route = intent?.getStringExtra(AirdropMessagingService.EXTRA_ROUTE) ?: return
        val referenceId = intent.getStringExtra(AirdropMessagingService.EXTRA_REFERENCE_ID)
        _pending.value = resolve(route, referenceId)
    }

    /**
     * Stripe payment-return deeplinks (VIEW intents carry a data Uri, never
     * the FCM extras, so this cannot clash with [capture]). Swift parity:
     * SceneDelegate.swift:432 handles the same airdrop:// return URLs.
     */
    fun captureUri(intent: Intent?) {
        val uri = intent?.data ?: return
        resolveUri(uri)?.let { _pending.value = it }
    }

    fun consume(): String? = _pending.value.also { _pending.value = null }

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

    /** Maps the RN/Swift route names carried by pushes to nav destinations. */
    private fun resolve(route: String, referenceId: String?): String = when (route) {
        "PackageDetailsView", "packageDetails" ->
            referenceId?.let { Routes.packageDetails(it) } ?: Routes.PACKAGES
        "PackagesView", "packages" -> Routes.PACKAGES
        "PaymentsView", "payments" -> Routes.PAYMENTS
        "OrdersView", "orders" -> Routes.ORDERS
        "NotificationsView", "notifications" -> Routes.NOTIFICATIONS
        "ShopView", "shop" -> Routes.SHOP
        "CartView", "cart" -> Routes.CART
        "AirCoinView", "airCoins" -> Routes.AIRCOIN_HISTORY
        "ReferView", "Refer", "refer", "referAFriend" ->
            Routes.REFER_A_FRIEND
        "ReferredFriendsView", "ReferredFriends", "referredFriends" -> Routes.REFERRED_FRIENDS
        "InviteFriendView", "InviteFriend", "inviteFriend" -> Routes.INVITE_FRIEND
        "PromotionsView", "promotions" -> Routes.PROMOTIONS
        else -> Routes.NOTIFICATIONS
    }
}
