package com.ga.airdrop.core.navigation

/**
 * Central route table. Mirrors FigmaRouteResolver (Swift) / RN route names so
 * push-notification deep links (`route` + `referenceID`) resolve identically
 * on Android.
 */
object Routes {
    // Auth
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val AUTH_LANDING = "authLanding"
    const val LOGIN = "login"
    const val SIGN_UP = "signUp"
    const val FORGOT_PASSWORD = "forgotPassword"
    const val REGISTRATION_SUCCESS = "registrationSuccess"

    // Tab roots
    const val HOME = "home"
    const val SHIPMENTS = "shipments"
    const val SHOP = "shop"
    const val CONTACTS = "contacts"
    const val MORE = "more"

    // Help drill-down — Swift "LiveAgentChatView" (Trengo web chat)
    const val LIVE_CHAT = "liveChat"

    // Home drill-downs
    const val WAREHOUSES = "warehouses"
    const val SERVICES = "services"
    const val SALES_TAXES = "salesTaxes"
    const val GOLD_PRIORITY = "goldPriority"
    const val NOTIFICATIONS = "notifications"
    const val AIRCOIN_HISTORY = "airCoinHistory"
    const val AIRCOIN_HISTORY_DETAIL = "airCoinHistoryDetail"

    // Shipments drill-downs
    const val PACKAGES = "packages"
    const val PACKAGE_DETAILS = "packageDetails/{packageId}"
    const val PAYMENTS = "payments"
    const val PAYMENT_PACKAGE_DETAILS = "paymentPackageDetails/{paymentId}"
    const val PRODUCT_PAYMENT_DETAILS = "productPaymentDetails/{paymentId}"
    const val ORDERS = "orders"
    const val ORDER_DETAILS = "orderDetails/{orderId}"
    const val INVOICE_VIEWER = "invoiceViewer?url={url}&title={title}"

    // Shop drill-downs
    const val AUCTION = "auction"
    const val AUCTION_PRODUCT_DETAILS = "auctionProductDetails/{slug}?featured={featured}"
    const val AUCTION_CHECKOUT = "auctionCheckout"
    const val FEATURED_PRODUCTS = "featuredProducts"
    const val CART = "cart"
    // Cart "Make Payment" → Delivery Method → currency popup → Stripe
    // (Swift FigmaDeliveryMethodViewController; docs/PARITY_GAP_SPECS.md §4).
    const val DELIVERY_METHOD = "deliveryMethod"
    const val PROFILE_INFORMATION = "profileInformation"
    const val ORDER_SUMMARY = "orderSummary"

    // JMD rail: NCB PowerTranz card entry + 3-D Secure challenge.
    const val NCB_CARD_ENTRY = "ncbCardEntry"
    const val NCB_THREE_DS = "ncbThreeDs"

    // More drill-downs
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val DOCUMENTS = "documents"
    const val NOTIFICATION_SETTINGS = "notificationSettings"
    const val BACKGROUNDS = "backgrounds"
    const val REFER_A_FRIEND = "referAFriend"
    const val INVITE_FRIEND = "inviteFriend"
    const val REFERRED_FRIENDS = "referredFriends"
    const val PROMOTIONS = "promotions"
    const val PREFERENCES = "preferences"
    const val AUTHORIZED_USERS = "authorizedUsers"
    const val ADD_AUTHORIZED_USER = "addAuthorizedUser?editId={editId}"
    const val AUTHORIZED_USER_DETAIL = "authorizedUserDetail/{userId}"
    const val PAYMENT_METHODS = "paymentMethods"
    const val ACCOUNT_DELETION = "accountDeletion"
    const val ACCOUNT_DELETION_REASON = "accountDeletionReason"
    const val SHIPPING_RATES = "shippingRates"
    const val TERMS = "terms"
    const val PRIVACY = "privacy"
    const val FAQ = "faq"
    const val RESTRICTED_ITEMS = "restrictedItems"
    const val ABOUT = "about"

    // Tools
    const val CALCULATOR = "calculator"
    const val CALCULATOR_RESULTS = "calculatorResults"
    const val CALCULATOR_GOVERNMENT_CHARGES = "calculatorGovernmentCharges"
    const val DROP_ALERT = "dropAlert"

    fun packageDetails(id: String) = "packageDetails/$id"
    fun paymentPackageDetails(id: String) = "paymentPackageDetails/$id"
    fun productPaymentDetails(id: String) = "productPaymentDetails/$id"
    fun orderDetails(id: String) = "orderDetails/$id"
    fun auctionProductDetails(slug: String, featured: Boolean = false) =
        "auctionProductDetails/$slug?featured=$featured"
    fun authorizedUserDetail(id: String) = "authorizedUserDetail/$id"
    fun addAuthorizedUser(editId: String? = null) =
        if (editId != null) "addAuthorizedUser?editId=$editId" else "addAuthorizedUser?editId="
    fun invoiceViewer(url: String, title: String): String {
        val encoded = java.net.URLEncoder.encode(url, "UTF-8").replace("+", "%20")
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        return "invoiceViewer?url=$encoded&title=$encodedTitle"
    }

    // Stripe hosted-checkout return (Swift SceneDelegate:432 payment-return
    // pipeline): PAYMENT_RETURN verifies the session server-side, then routes
    // to PAYMENT_SUCCESS (verified paid), the cart (not paid / cancelled), or
    // Shipments (unconfirmed).
    const val PAYMENT_RETURN = "paymentReturn/{sessionId}"
    const val PAYMENT_SUCCESS = "paymentSuccess?ref={ref}&amount={amount}"
    const val PAYMENT_CANCELLED = "paymentCancelled"

    fun paymentReturn(sessionId: String?) = "paymentReturn/${sessionId.orEmpty()}"

    fun paymentSuccess(ref: String?, amount: String?): String {
        val encodedRef = java.net.URLEncoder.encode(ref.orEmpty(), "UTF-8").replace("+", "%20")
        val encodedAmount =
            java.net.URLEncoder.encode(amount.orEmpty(), "UTF-8").replace("+", "%20")
        return "paymentSuccess?ref=$encodedRef&amount=$encodedAmount"
    }

    /**
     * A cancelled checkout explains itself first (Swift "Payment cancelled"
     * alert), then lands on the cart with its contents intact.
     */
    fun paymentCancelled() = PAYMENT_CANCELLED
}
