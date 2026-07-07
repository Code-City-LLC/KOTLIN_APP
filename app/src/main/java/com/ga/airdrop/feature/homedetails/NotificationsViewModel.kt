package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.data.repo.MiscRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val items: List<AirdropNotification> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    /** True once the first page has answered (success or failure). */
    val loadedOnce: Boolean = false,
)

/**
 * Live notifications inbox — matches Swift origin/main's populated-list path:
 * GET /user/notifications paginated, optimistic POST /user/notifications/mark-read
 * on tap, deep-link route resolution. Swift staging currently carries an older
 * static empty-state variant, so behavior changes here need a product decision.
 */
class NotificationsViewModel(
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state

    private var page = 1
    private val perPage = 20

    init {
        refresh()
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(loading = true, error = null, endReached = false) }
        viewModelScope.launch {
            miscRepository.notifications(page, perPage)
                .onSuccess { batch ->
                    _state.update {
                        it.copy(
                            items = batch,
                            loading = false,
                            loadedOnce = true,
                            endReached = batch.size < perPage,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadedOnce = true,
                            error = err.message ?: "Unable to load notifications",
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            miscRepository.notifications(page + 1, perPage)
                .onSuccess { batch ->
                    page += 1
                    _state.update {
                        it.copy(
                            items = it.items + batch,
                            loadingMore = false,
                            endReached = batch.size < perPage,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    /**
     * Optimistically flips the row to read and fires the mark-read mutation
     * (Swift comment: failure keeps the local flip until the next refresh).
     * Returns the resolved in-app route to navigate to, or null.
     */
    fun onNotificationTapped(notification: AirdropNotification): String? {
        if (!notification.isRead) {
            _state.update { state ->
                state.copy(
                    items = state.items.map {
                        if (it.id == notification.id) it.copy(isRead = true) else it
                    }
                )
            }
            viewModelScope.launch {
                miscRepository.markNotificationRead(notification.id)
            }
        }
        return resolveNotificationRoute(notification.route, notification.referenceId)
    }
}

/**
 * RN/Swift route-name → Android Routes resolution — mirrors
 * FigmaRouteResolver.destination(for:detail:) so push payloads
 * (`route` + `referenceID`) deep-link identically.
 */
fun resolveNotificationRoute(route: String?, referenceId: String?): String? {
    val name = route?.trim().orEmpty()
    if (name.isEmpty()) return null
    val ref = referenceId?.trim().orEmpty()
    return when (name) {
        "TabNavigator", "HomeView" -> Routes.HOME
        "ShipmentsView" -> Routes.SHIPMENTS
        "ShopView" -> Routes.SHOP
        "ContactsView", "HelpView" -> Routes.CONTACTS
        "MoreView" -> Routes.MORE
        "PackagesView" -> Routes.PACKAGES
        "PackageDetailsView" ->
            if (ref.isNotEmpty()) Routes.packageDetails(ref) else Routes.PACKAGES
        "PaymentsView" -> Routes.PAYMENTS
        "OrdersView" -> Routes.ORDERS
        "OrderDetailsView" ->
            if (ref.isNotEmpty()) Routes.orderDetails(ref) else Routes.ORDERS
        "AuctionView" -> Routes.AUCTION
        "AuctionProductView", "AuctionProductDetailsView" ->
            if (ref.isNotEmpty()) Routes.auctionProductDetails(ref) else Routes.AUCTION
        "FeatureProductsView" -> Routes.FEATURED_PRODUCTS
        "AuctionProductCheckoutView" -> {
            // Swift FigmaRouteViewController:727 — the checkout VC receives
            // the product reference and resolves it itself; the Kotlin route
            // carries no args, so the ref rides the checkout store (B4).
            com.ga.airdrop.feature.shop.ShopCheckoutStore.pendingRef =
                ref.takeIf { it.isNotEmpty() }
            Routes.AUCTION_CHECKOUT
        }
        "MyCartView", "CartView", "CheckoutView", "addToCart" -> Routes.CART
        "NotificationsView" -> Routes.NOTIFICATIONS
        "NotificationSettings", "NotificationSettingsView" -> Routes.NOTIFICATION_SETTINGS
        "MembershipTierView", "GoldPriorityView" -> Routes.GOLD_PRIORITY
        "AirCoinView", "AirCoinsView" -> Routes.AIRCOIN_HISTORY
        "AirCoinHistoryView", "AirCoinHistoryDetailView" -> AIRCOIN_HISTORY_DETAIL_ROUTE
        "WarehouseView" -> Routes.WAREHOUSES
        "ServicesScreen", "ServicesView" -> Routes.SERVICES
        "SalesTaxesView" -> Routes.SALES_TAXES
        "CalculatorView" -> Routes.CALCULATOR
        "DropAlertView" -> Routes.DROP_ALERT
        "ReferView" -> Routes.REFER_A_FRIEND
        "ReferredFriendsView" -> Routes.REFERRED_FRIENDS
        "InviteFriendView" -> Routes.INVITE_FRIEND
        "PreferencesView" -> Routes.PREFERENCES
        "PromotionsView" -> Routes.PROMOTIONS
        "DocumentsView", "DocumentDownloadingView" -> Routes.DOCUMENTS
        "AuthorizedUsersView" -> Routes.AUTHORIZED_USERS
        "SettingsView" -> Routes.SETTINGS
        "ShippingRatesView" -> Routes.SHIPPING_RATES
        "RestrictedItemsView", "RestrictedItemsScreenView", "RestrictedItemsInfoView" ->
            Routes.RESTRICTED_ITEMS
        "FAQView" -> Routes.FAQ
        "TermsConditionsView" -> Routes.TERMS
        "PrivacyPolicyView" -> Routes.PRIVACY
        "ProfileView", "EditProfileView" -> Routes.PROFILE
        else -> null
    }
}
