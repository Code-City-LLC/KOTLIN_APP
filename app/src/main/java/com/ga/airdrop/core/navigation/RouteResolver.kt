package com.ga.airdrop.core.navigation

/**
 * RN/Swift route-name -> Android route resolution.
 *
 * Mirrors `FigmaRouteResolver.destination(for:detail:)` for notification rows
 * and push payloads that carry `route` + `referenceID`.
 */
fun resolveAirdropRoute(route: String?, referenceId: String?): String? {
    val name = route?.trim().orEmpty()
    if (name.isEmpty()) return null
    val ref = referenceId?.trim().orEmpty()

    return when (name) {
        "TabNavigator", "HomeView" -> Routes.HOME
        "ShipmentsView" -> Routes.SHIPMENTS
        "ShopView" -> Routes.SHOP
        "ContactsView", "HelpView" -> Routes.CONTACTS
        "MoreView" -> Routes.MORE

        "PackagesView", "packages" -> Routes.PACKAGES
        "PackageDetailsView", "packageDetails" ->
            if (ref.isNotEmpty()) Routes.packageDetails(ref) else Routes.PACKAGES
        "PaymentsView", "payments" -> Routes.PAYMENTS
        "PaymentPackageDetailsView", "PaymentPackageDetails", "paymentPackageDetails" ->
            if (ref.isNotEmpty()) Routes.paymentPackageDetails(ref) else Routes.PAYMENTS
        "ProductPaymentDetailsView", "ProductPaymentDetails", "productPaymentDetails" ->
            if (ref.isNotEmpty()) Routes.productPaymentDetails(ref) else Routes.PAYMENTS
        "OrdersView", "orders" -> Routes.ORDERS
        "OrderDetailsView", "OrderDetails", "orderDetails" ->
            if (ref.isNotEmpty()) Routes.orderDetails(ref) else Routes.ORDERS
        "InvoiceViewerScreen", "InvoiceViewer", "invoiceViewer" ->
            if (ref.isNotEmpty()) Routes.invoiceViewer(ref, "Invoice") else Routes.PAYMENTS

        "AuctionView" -> Routes.AUCTION
        "AuctionProductView", "AuctionProductDetailsView" ->
            if (ref.isNotEmpty()) Routes.auctionProductDetails(ref) else Routes.AUCTION
        "FeatureProductsView" -> Routes.FEATURED_PRODUCTS
        "AuctionProductCheckoutView" -> Routes.AUCTION_CHECKOUT
        "MyCartView", "CartView", "CheckoutView", "addToCart", "cart" -> Routes.CART

        "NotificationsView", "notifications" -> Routes.NOTIFICATIONS
        "NotificationSettings", "NotificationSettingsView" -> Routes.NOTIFICATION_SETTINGS
        "MembershipTierView", "GoldPriorityView" -> Routes.GOLD_PRIORITY
        "AirCoinView", "AirCoinsView", "airCoins" -> Routes.AIRCOIN_HISTORY
        "AirCoinHistoryView", "AirCoinHistoryDetailView" -> Routes.AIRCOIN_HISTORY_DETAIL
        "WarehouseView" -> Routes.WAREHOUSES
        "ServicesScreen", "ServicesView" -> Routes.SERVICES
        "SalesTaxesView" -> Routes.SALES_TAXES
        "CalculatorView" -> Routes.CALCULATOR
        "CalculatorResultsView" -> Routes.CALCULATOR_RESULTS
        "DropAlertView" -> Routes.DROP_ALERT

        "ReferView", "Refer", "ReferredFriendsView", "ReferredFriends", "refer", "referAFriend" ->
            Routes.REFER_A_FRIEND
        "InviteFriendView", "InviteFriend", "inviteFriend" -> Routes.INVITE_FRIEND
        "PreferencesView", "Preferences" -> Routes.PREFERENCES
        "PromotionsView", "promotions" -> Routes.PROMOTIONS
        "DocumentsView", "DocumentDownloadingView" -> Routes.DOCUMENTS
        "AuthorizedUsersView", "AuthorizedUsers" -> Routes.AUTHORIZED_USERS
        "AuthorizedUserDetailView", "AuthorizedUserDetail" ->
            if (ref.isNotEmpty()) Routes.authorizedUserDetail(ref) else Routes.AUTHORIZED_USERS
        "AddAuthorizedUserView", "AddAuthorizedUser", "AddUserView" -> Routes.addAuthorizedUser()
        "PaymentMethodsView", "PaymentMethods" -> Routes.PAYMENT_METHODS
        "SettingsView" -> Routes.SETTINGS
        "Backgrounds", "BackgroundImages", "BackgroundImagesView" -> Routes.BACKGROUNDS
        "AccountDeletion", "AccountDeletionView" -> Routes.ACCOUNT_DELETION
        "AccountDeletionReason", "AccountDeletionReasonView" -> Routes.ACCOUNT_DELETION_REASON
        "ShippingRatesView" -> Routes.SHIPPING_RATES
        "RestrictedItemsView", "RestrictedItemsScreenView", "RestrictedItemsInfoView",
        "RestrictedItemsInfo", "RestrictedItems" -> Routes.RESTRICTED_ITEMS
        "FAQView" -> Routes.FAQ
        "TermsConditionsView" -> Routes.TERMS
        "PrivacyPolicyView" -> Routes.PRIVACY
        "ProfileView", "EditProfileView" -> Routes.PROFILE
        else -> null
    }
}
