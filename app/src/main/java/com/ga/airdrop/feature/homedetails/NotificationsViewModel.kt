package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.data.repo.MiscRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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
class NotificationsViewModel internal constructor(
    private val dataSource: NotificationsDataSource = RepositoryNotificationsDataSource(
        MiscRepository(ApiClient.service),
    ),
    private val sessionSnapshot: () -> AuthTokenStore.Snapshot = AuthTokenStore::snapshot,
    private val sessionChanges: Flow<AuthTokenStore.Snapshot>? = null,
) : ViewModel() {

    constructor() : this(
        dataSource = RepositoryNotificationsDataSource(MiscRepository(ApiClient.service)),
        sessionSnapshot = AuthTokenStore::snapshot,
        sessionChanges = AuthTokenStore.snapshotFlow,
    )

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state

    private var page = 1
    private val perPage = 20
    private var contentSessionId = sessionSnapshot().sessionId
    private var observedSessionId = contentSessionId
    private var refreshGeneration = 0L
    private var loadMoreGeneration = 0L

    init {
        refresh()
        sessionChanges?.let { changes ->
            viewModelScope.launch {
                changes.collect { changed ->
                    if (changed.sessionId == observedSessionId) return@collect
                    observedSessionId = changed.sessionId
                    refreshGeneration += 1
                    loadMoreGeneration += 1
                    contentSessionId = changed.sessionId
                    page = 1
                    _state.value = NotificationsUiState()
                    if (changed.token != null) refresh()
                }
            }
        }
    }

    fun refresh() {
        val expectedSession = sessionSnapshot()
        val generation = ++refreshGeneration
        loadMoreGeneration += 1
        page = 1
        val sessionChanged = expectedSession.sessionId != contentSessionId
        contentSessionId = expectedSession.sessionId
        _state.update {
            if (sessionChanged) {
                NotificationsUiState(loading = true)
            } else {
                it.copy(loading = true, loadingMore = false, error = null, endReached = false)
            }
        }
        viewModelScope.launch {
            dataSource.notifications(page, perPage)
                .onSuccess { batch ->
                    if (generation != refreshGeneration) return@onSuccess
                    if (!AuthTokenStore.isSameSession(sessionSnapshot(), expectedSession)) {
                        retireStaleSession(generation)
                        return@onSuccess
                    }
                    contentSessionId = expectedSession.sessionId
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
                    if (generation != refreshGeneration) return@onFailure
                    if (!AuthTokenStore.isSameSession(sessionSnapshot(), expectedSession)) {
                        retireStaleSession(generation)
                        return@onFailure
                    }
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
        if (!current.loadedOnce || current.loading || current.loadingMore || current.endReached) return
        if (sessionSnapshot().sessionId != contentSessionId || contentSessionId == null) return
        _state.update { it.copy(loadingMore = true) }
        val expectedSession = sessionSnapshot()
        val generation = ++loadMoreGeneration
        val requestedPage = page + 1
        viewModelScope.launch {
            if (!AuthTokenStore.isSameSession(sessionSnapshot(), expectedSession)) {
                retireStalePage(generation)
                return@launch
            }
            dataSource.notifications(requestedPage, perPage)
                .onSuccess { batch ->
                    if (generation != loadMoreGeneration) return@onSuccess
                    if (!AuthTokenStore.isSameSession(sessionSnapshot(), expectedSession)) {
                        retireStalePage(generation)
                        return@onSuccess
                    }
                    page = requestedPage
                    _state.update {
                        it.copy(
                            items = it.items + batch,
                            loadingMore = false,
                            endReached = batch.size < perPage,
                        )
                    }
                }
                .onFailure {
                    if (generation != loadMoreGeneration) return@onFailure
                    if (!AuthTokenStore.isSameSession(sessionSnapshot(), expectedSession)) {
                        retireStalePage(generation)
                        return@onFailure
                    }
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    private fun retireStaleSession(generation: Long) {
        if (generation != refreshGeneration) return
        contentSessionId = null
        page = 1
        _state.value = NotificationsUiState()
    }

    private fun retireStalePage(generation: Long) {
        if (generation != loadMoreGeneration) return
        contentSessionId = null
        page = 1
        _state.value = NotificationsUiState()
    }

    /**
     * Optimistically flips the row to read and fires the mark-read mutation
     * (Swift comment: failure keeps the local flip until the next refresh).
     * Returns the resolved in-app route to navigate to, or null.
     */
    fun onNotificationTapped(notification: AirdropNotification): String? {
        val expectedSession = sessionSnapshot()
        if (expectedSession.token == null || expectedSession.sessionId != contentSessionId) return null
        val ownedNotification = _state.value.items.firstOrNull { it.id == notification.id }
            ?: return null
        if (!ownedNotification.isRead) {
            _state.update { state ->
                state.copy(
                    items = state.items.map {
                        if (it.id == ownedNotification.id) it.copy(isRead = true) else it
                    }
                )
            }
            viewModelScope.launch {
                if (sessionSnapshot() != expectedSession) return@launch
                dataSource.markNotificationRead(ownedNotification.id, expectedSession)
            }
        }
        return resolveNotificationRoute(ownedNotification)
    }
}

internal interface NotificationsDataSource {
    suspend fun notifications(page: Int, limit: Int): Result<List<AirdropNotification>>
    suspend fun markNotificationRead(
        id: String,
        expectedSession: AuthTokenStore.Snapshot,
    ): Result<Unit>
}

private class RepositoryNotificationsDataSource(
    private val repository: MiscRepository,
) : NotificationsDataSource {
    override suspend fun notifications(page: Int, limit: Int): Result<List<AirdropNotification>> =
        repository.notifications(page, limit)

    override suspend fun markNotificationRead(
        id: String,
        expectedSession: AuthTokenStore.Snapshot,
    ): Result<Unit> = repository.markNotificationRead(id, expectedSession).map { }
}

/**
 * RN/Swift route-name → Android Routes resolution — mirrors
 * FigmaRouteResolver.destination(for:detail:) so push payloads
 * (`route` + `referenceID`) deep-link identically.
 */
fun resolveNotificationRoute(notification: AirdropNotification): String? {
    val route = notification.route?.trim()?.takeIf { it.isNotEmpty() }
        ?: notificationTypeRouteName(notification)
    return resolveNotificationRoute(route, notification.referenceId, notification)
}

fun resolveNotificationRoute(route: String?, referenceId: String?): String? =
    resolveNotificationRoute(route, referenceId, notification = null)

private fun resolveNotificationRoute(
    route: String?,
    referenceId: String?,
    notification: AirdropNotification?,
): String? {
    val name = canonicalNotificationRoute(route?.trim().orEmpty())
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
        "PaymentPackageDetailsView" ->
            if (ref.isNotEmpty()) Routes.paymentPackageDetails(ref) else Routes.PAYMENTS
        "ProductPaymentDetailsView" ->
            if (ref.isNotEmpty()) Routes.productPaymentDetails(ref) else Routes.PAYMENTS
        "InvoiceViewerScreen" -> {
            val url = notification?.payload?.firstValue(
                "invoice_url",
                "invoiceUrl",
                "document_url",
                "documentUrl",
                "url",
            ).orEmpty()
            val title = notification?.title?.takeIf { it.isNotBlank() } ?: "Invoice"
            Routes.invoiceViewer(url, title)
        }
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
        // Swift FigmaRouteViewController routes previously missing here — a
        // push tap to any of these dead-ended on Android (round-3 sweep).
        "AddAuthorizedUserView" -> Routes.addAuthorizedUser()
        "AuthorizedUserDetailView" ->
            if (ref.isNotEmpty()) Routes.authorizedUserDetail(ref) else Routes.AUTHORIZED_USERS
        "BackgroundImagesView", "Backgrounds" -> Routes.BACKGROUNDS
        "DeliveryMethodView", "DeliveryMethod" -> Routes.DELIVERY_METHOD
        "LiveAgentChatView" -> Routes.LIVE_CHAT
        "PaymentMethodsView", "PaymentMethods" -> Routes.PAYMENT_METHODS
        // Swift's timeline screen lives inside the payment package details on
        // Android — nearest equivalent destination.
        "PaymentShipmentTimelineScreen", "PackageHistoryView" ->
            if (ref.isNotEmpty()) Routes.paymentPackageDetails(ref) else Routes.PAYMENTS
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

private fun notificationTypeRouteName(notification: AirdropNotification): String? {
    val candidates = listOf(
        notification.type,
        notification.payload["notification_type"],
        notification.payload["type"],
    )
    for (candidate in candidates) {
        routeNameForNotificationType(candidate)?.let { return it }
    }
    return null
}

internal fun routeNameForNotificationType(type: String?): String? {
    val raw = type?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = raw.lowercase().replace("-", "_")
    return NOTIFICATION_TYPE_ROUTES[raw] ?: NOTIFICATION_TYPE_ROUTES[normalized]
}

private fun canonicalNotificationRoute(route: String): String {
    if (route.isEmpty()) return ""
    return SCREEN_ROUTE_ALIASES[route] ?: SCREEN_ROUTE_ALIASES[route.replace("-", "_")] ?: route
}

private fun Map<String, String>.firstValue(vararg keys: String): String? {
    for (key in keys) {
        this[key]?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

private val SCREEN_ROUTE_ALIASES = mapOf(
    "PackageDetailScreen" to "PackageDetailsView",
    "packageDetails" to "PackageDetailsView",
    "PaymentPackageDetailsScreen" to "PaymentPackageDetailsView",
    "paymentPackageDetails" to "PaymentPackageDetailsView",
    "ProductPaymentDetailsScreen" to "ProductPaymentDetailsView",
    "productPaymentDetails" to "ProductPaymentDetailsView",
    "OrderDetailsScreen" to "OrderDetailsView",
    "orderDetails" to "OrderDetailsView",
    "PackagesScreen" to "PackagesView",
    "UploadInvoiceScreen" to "InvoiceViewerScreen",
    "UpdateAddressScreen" to "ProfileView",
    "DocumentsScreen" to "DocumentsView",
    "PaymentScreen" to "PaymentsView",
    "PromotionsScreen" to "PromotionsView",
    "HomeScreen" to "HomeView",
    "ProfileScreen" to "ProfileView",
    "AuctionScreen" to "AuctionView",
    "TransactionScreen" to "PaymentsView",
    "WalletScreen" to "PaymentsView",
    "MessagesScreen" to "NotificationsView",
    "AccountScreen" to "ProfileView",
    "AirdropScreen" to "HomeView",
    "ReferralScreen" to "ReferView",
    "KYCScreen" to "ProfileView",
    "SupportScreen" to "ContactsView",
    "ReferView" to "ReferView",
    "ContactsView" to "ContactsView",
    // Bare RN/push aliases the old PushDeepLink map resolved directly — kept so
    // the shared resolver is a superset, not a subset (round-4 regression).
    "Refer" to "ReferView",
    "refer" to "ReferView",
    "referAFriend" to "ReferView",
    "ReferredFriends" to "ReferredFriendsView",
    "referredFriends" to "ReferredFriendsView",
    "InviteFriend" to "InviteFriendView",
    "inviteFriend" to "InviteFriendView",
)

private val NOTIFICATION_TYPE_ROUTES = mapOf(
    "package_status_update" to "PackageDetailsView",
    "package_received" to "PackageDetailsView",
    "shipment_received" to "PackageDetailsView",
    "shipment_update" to "PackageDetailsView",
    "shipment_status" to "PackageDetailsView",
    "shipment_status_update" to "PackageDetailsView",
    "package_ready_for_pickup" to "PackageDetailsView",
    "shipment_ready_for_pickup" to "PackageDetailsView",
    "ready_for_pickup" to "PackageDetailsView",
    "paid_ready_for_pickup" to "PackageDetailsView",
    "paid_and_ready_for_pickup" to "PackageDetailsView",
    "package_paid_and_ready_for_pickup" to "PackageDetailsView",
    "package_paid_and_ready_for_delivery" to "PackageDetailsView",
    "package_delivered" to "PackageDetailsView",
    "shipment_delivered" to "PackageDetailsView",
    "package_in_transit" to "PackageDetailsView",
    "shipment_in_transit" to "PackageDetailsView",
    "package_customs_clearance" to "PackageDetailsView",
    "shipment_customs_clearance" to "PackageDetailsView",
    "processing_at_customs" to "PackageDetailsView",
    "package_processing_at_customs" to "PackageDetailsView",
    "invoice_required" to "InvoiceViewerScreen",
    "address_required" to "ProfileView",
    "document_uploaded" to "DocumentsView",
    "storage_fee_reminder" to "PackageDetailsView",
    "storage_fee_first_notice" to "PackageDetailsView",
    "storage_fee_weekly_reminder" to "PackageDetailsView",
    "auction_warning" to "PackagesView",
    "auction_completion" to "PackagesView",
    "payment_reminder" to "PaymentsView",
    "payment_received" to "PaymentsView",
    "payment_failed" to "PaymentsView",
    "promotional" to "PromotionsView",
    "system_announcement" to "HomeView",
    "system_maintenance" to "HomeView",
    "account_verification" to "ProfileView",
    "password_reset" to "ProfileView",
)
