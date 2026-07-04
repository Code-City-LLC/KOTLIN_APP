package com.ga.airdrop.core.push

import android.content.Intent
import com.ga.airdrop.core.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pending push navigation, consumed by AppRoot once the nav graph is up.
 * Android counterpart of FigmaRouteResolver.push(route:referenceID:).
 */
object PushDeepLink {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun capture(intent: Intent?) {
        val route = intent?.getStringExtra(AirdropMessagingService.EXTRA_ROUTE) ?: return
        val referenceId = intent.getStringExtra(AirdropMessagingService.EXTRA_REFERENCE_ID)
        _pending.value = resolve(route, referenceId)
    }

    fun consume(): String? = _pending.value.also { _pending.value = null }

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
        "PromotionsView", "promotions" -> Routes.PROMOTIONS
        else -> Routes.NOTIFICATIONS
    }
}
