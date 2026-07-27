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

    // Help drill-down — Swift "LiveAgentChatView" (native AutoPilot/Nirvana chat)
    const val LIVE_CHAT = "liveChat"

    // Home drill-downs
    const val WAREHOUSES = "warehouses"
    const val SERVICES = "services"
    // Delivery / tracking hub — reached from the Home "Delivery Center" tile
    // and the payment-success "Track your package" action. Optional ref = the
    // just-paid invoice (deterministic post-checkout journey); optional
    // packageId = a server-identified package (live tracking detail).
    const val DELIVERY_CENTER =
        "deliveryCenter?ref={ref}&packageId={packageId}&packageIds={packageIds}"
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

    // More drill-downs
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val DOCUMENTS = "documents"
    const val NOTIFICATION_SETTINGS = "notificationSettings"
    const val BACKGROUNDS = "backgrounds"
    const val ACTIVE_SESSIONS = "activeSessions"
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

    /**
     * ⚠️ NOT A NAVHOST DESTINATION — a sentinel. Nothing is registered for it
     * and `navigate()` on it would throw.
     *
     * The "App update available" notification has to leave the app for the Play
     * Store, but the whole notification pipeline is typed as "route string in,
     * navigate out". Rather than change that shape everywhere, the resolver
     * returns this and the two tap handlers intercept it — see
     * [isExternalNotificationRoute].
     *
     * The `external:` prefix is deliberate: any future non-navigating action can
     * reuse the same guard instead of adding a second special case.
     */
    const val EXTERNAL_APP_UPDATE = "external:app-update"

    /** True when [route] must be acted on rather than navigated to. */
    fun isExternalNotificationRoute(route: String?): Boolean =
        route != null && route.startsWith("external:")

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
    const val PAYMENT_SUCCESS =
        "paymentSuccess?ref={ref}&amount={amount}&fulfillment={fulfillment}&packageIds={packageIds}"
    const val PAYMENT_CANCELLED = "paymentCancelled"

    // NCB (JMD) PowerTranz checkout: card entry → 3-D Secure WebView.
    const val NCB_CARD_ENTRY = "ncbCardEntry"
    const val NCB_3DS = "ncb3ds"

    // Auction "Buy Now" NCB (JMD): the direct-purchase path's own card entry + 3DS,
    // scoped to the AUCTION_CHECKOUT VM (separate from the cart-scoped NCB routes).
    const val AUCTION_NCB_CARD_ENTRY = "auctionNcbCardEntry"
    const val AUCTION_NCB_3DS = "auctionNcb3ds"

    fun paymentReturn(sessionId: String?) = "paymentReturn/${sessionId.orEmpty()}"

    /**
     * ref = the just-paid invoice (from payment success; deterministic journey);
     * packageId = a server-identified package (live tracking detail). Both
     * optional — neither means "open on the live active-deliveries view".
     */
    fun deliveryCenter(
        ref: String? = null,
        packageId: Int? = null,
        packageIds: List<Int> = emptyList(),
    ): String {
        val encoded = java.net.URLEncoder.encode(ref.orEmpty(), "UTF-8").replace("+", "%20")
        val pkg = packageId?.takeIf { it > 0 }?.toString() ?: ""
        return "deliveryCenter?ref=$encoded&packageId=$pkg&packageIds=${encodePackageIds(packageIds)}"
    }

    /**
     * The just-paid package ids, as a comma-separated list for the route.
     *
     * Carried through the route rather than held in a store because the store
     * that knows about a checkout is cleared when the checkout commits — the
     * ids have to survive past that, and a route argument is the only thing
     * here that does. Non-positive values are dropped so a malformed id cannot
     * become a request for package 0.
     */
    fun encodePackageIds(packageIds: List<Int>): String =
        packageIds.filter { it > 0 }.distinct().joinToString(",")

    /** Inverse of [encodePackageIds]; tolerates blanks and junk without throwing. */
    fun decodePackageIds(raw: String?): List<Int> =
        raw.orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .distinct()

    fun paymentSuccess(
        ref: String?,
        amount: String?,
        fulfillment: String? = null,
        packageIds: List<Int> = emptyList(),
    ): String {
        val encodedRef = java.net.URLEncoder.encode(ref.orEmpty(), "UTF-8").replace("+", "%20")
        val encodedAmount =
            java.net.URLEncoder.encode(amount.orEmpty(), "UTF-8").replace("+", "%20")
        val encodedFulfillment =
            java.net.URLEncoder.encode(fulfillment.orEmpty(), "UTF-8").replace("+", "%20")
        return "paymentSuccess?ref=$encodedRef&amount=$encodedAmount" +
            "&fulfillment=$encodedFulfillment&packageIds=${encodePackageIds(packageIds)}"
    }

    /**
     * A cancelled checkout explains itself first (Swift "Payment cancelled"
     * alert), then lands on the cart with its contents intact.
     */
    fun paymentCancelled() = PAYMENT_CANCELLED
}
