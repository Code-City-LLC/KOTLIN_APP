package com.ga.airdrop.data.api

import com.ga.airdrop.data.model.AirCoinTransaction
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsersEnvelope
import com.ga.airdrop.data.model.CartSnapshot
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.data.model.CheckoutSessionStatus
import com.ga.airdrop.data.model.CreateCheckoutRequest
import com.ga.airdrop.data.model.CreateNcbSessionRequest
import com.ga.airdrop.data.model.NcbCompletePaymentRequest
import com.ga.airdrop.data.model.NcbCompletePaymentResponse
import com.ga.airdrop.data.model.NcbSessionResponse
import com.ga.airdrop.data.model.CurrentUserResponse
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.TierChangeRequest
import com.ga.airdrop.data.model.TierChangeResult
import com.ga.airdrop.data.model.CustomDutyRate
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.DeactivateAccountRequest
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.DeactivateDeviceTokenRequest
import com.ga.airdrop.data.model.DeviceToken
import com.ga.airdrop.data.model.DropAlertResponse
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.ExchangeRate
import com.ga.airdrop.data.model.FaqItem
import com.ga.airdrop.data.model.ForgotPasswordRequest
import com.ga.airdrop.data.model.LoginRequest
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.MarkNotificationReadRequest
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.Order
import com.ga.airdrop.data.model.OrderDetailEnvelope
import com.ga.airdrop.data.model.Package
import com.ga.airdrop.data.model.PackageCartMutation
import com.ga.airdrop.data.model.PackageCategory
import com.ga.airdrop.data.model.PackageDetail
import com.ga.airdrop.data.model.PackageInvoicesMutationResponse
import com.ga.airdrop.data.model.PackageStatus
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Payment
import com.ga.airdrop.data.model.PaymentIntentStatus
import com.ga.airdrop.data.model.PaymentSheetConfig
import com.ga.airdrop.data.model.PlaceSearchResults
import com.ga.airdrop.data.model.ProfileAssetResponse
import com.ga.airdrop.data.model.ProfileMutationResponse
import com.ga.airdrop.data.model.ProfileUpdateRequest
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.data.model.ReactivateAccountRequest
import com.ga.airdrop.data.model.ReferFriendRequest
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.data.model.RegisterDeviceTokenRequest
import com.ga.airdrop.data.model.ResetPasswordRequest
import com.ga.airdrop.data.model.ReverseGeocodeRequest
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.model.SaveDeliveryPreferenceRequest
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.SearchPlacesRequest
import com.ga.airdrop.data.model.SendTestNotificationRequest
import com.ga.airdrop.data.model.ShipmentCalculation
import com.ga.airdrop.data.model.ShipmentCalculationRequest
import com.ga.airdrop.data.model.ShipmentSummary
import com.ga.airdrop.data.model.ShippingRates
import com.ga.airdrop.data.model.SignUpRequest
import com.ga.airdrop.data.model.UserDocuments
import com.ga.airdrop.data.model.ValidateLocationRequest
import com.ga.airdrop.data.model.Warehouse
import com.ga.airdrop.core.auth.AuthTokenStore
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

// One suspend fun per Laravel /api/v1 endpoint the Swift app calls
// (AirdropAPI.swift), plus the delivery/PaymentSheet family.
interface AirdropApiService {

    // ── Auth ──

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body body: SignUpRequest): LoginResponse

    @POST("auth/refresh")
    suspend fun refreshToken(@Body body: EmptyRequest): LoginResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): MutationResponse

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): MutationResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: EmptyRequest): MutationResponse

    @POST("user/reactivate-account")
    suspend fun reactivateAccount(@Body body: ReactivateAccountRequest): LoginResponse

    @GET("service-tiers")
    suspend fun serviceTiers(): Paginated<ServiceTier>

    @GET("customers/me/tier")
    suspend fun customerTier(): DataEnvelope<CustomerTier>

    /**
     * The backend authorizes tier changes and returns an operation result.
     * Callers must confirm the applied tier with [customerTier] before
     * publishing success to the UI.
     */
    @PATCH("customers/me/tier")
    suspend fun changeCustomerTier(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
        @Body body: TierChangeRequest,
    ): DataEnvelope<TierChangeResult>

    // ── CMS / FAQ ──

    @Headers("X-Airdrop-No-Auth: true")
    @GET("faqs")
    suspend fun faqs(): Paginated<FaqItem>

    // Body may be a JSON envelope or raw HTML; parsed in MiscRepository.
    @Headers("X-Airdrop-No-Auth: true")
    @GET("content/terms-conditions")
    suspend fun termsAndConditions(): ResponseBody

    @Headers("X-Airdrop-No-Auth: true")
    @GET("content/privacy-policy")
    suspend fun privacyPolicy(): ResponseBody

    // ── User / profile ──

    @GET("user/profile")
    suspend fun currentUser(): CurrentUserResponse

    @PUT("user/profile")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): ProfileMutationResponse

    @GET("user/documents")
    suspend fun userDocuments(): UserDocuments

    // File part name must equal the document type wire name (Swift sends
    // fieldName = type.rawValue alongside the document_type field).
    @Multipart
    @POST("user/documents")
    suspend fun uploadUserDocument(
        @Part("document_type") documentType: RequestBody,
        @Part file: MultipartBody.Part,
    ): ProfileAssetResponse

    @DELETE("user/documents/{id}")
    suspend fun deleteUserDocument(@Path("id") id: String): MutationResponse

    @GET("user/profile/image")
    suspend fun profileImage(): ProfileAssetResponse

    // Part name: "image".
    @Multipart
    @POST("user/profile/image")
    suspend fun uploadProfileImage(@Part image: MultipartBody.Part): ProfileAssetResponse

    @DELETE("user/profile/image")
    suspend fun deleteProfileImage(): MutationResponse

    @POST("user/deactivate-account")
    suspend fun deactivateAccount(@Body body: DeactivateAccountRequest): MutationResponse

    // ── Products (auction / featured) ──

    @GET("products")
    suspend fun products(@QueryMap params: Map<String, String>): Paginated<AuctionProduct>

    // Dedicated single-product-by-slug detail endpoint (Laravel route-model
    // binding on slug: GET /products/{product:slug}). The list ?slug= filter
    // does NOT match, so the detail screen must use this.
    @GET("products/{slug}")
    suspend fun productDetail(@Path("slug") slug: String): com.ga.airdrop.data.model.ProductDetailEnvelope

    @GET("featured-products")
    suspend fun featuredProducts(@QueryMap params: Map<String, String>): Paginated<AuctionProduct>

    // ── Warehouses ──

    @GET("warehouse")
    suspend fun warehouses(): Paginated<Warehouse>

    // ── AirCoins ──

    @GET("aircoins/status")
    suspend fun airCoinsStatus(): AirCoinsStatus

    @GET("aircoins/history")
    suspend fun airCoinsHistory(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
    ): Paginated<AirCoinTransaction>

    // ── Notifications / device tokens ──

    @GET("user/notifications")
    suspend fun notifications(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        // Swift AirdropAPI.notifications appends unread_only=1 only when the
        // All|Unread filter is on Unread; omit it otherwise (null = no param).
        @Query("unread_only") unreadOnly: Int? = null,
    ): Paginated<AirdropNotification>

    @POST("user/notifications/mark-read")
    suspend fun markNotificationRead(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
        @Body body: MarkNotificationReadRequest,
    ): MutationResponse

    @POST("device-tokens/register")
    suspend fun registerDeviceToken(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String? = null,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String? = null,
        @Body body: RegisterDeviceTokenRequest,
    ): MutationResponse

    // Swift deactivateFCMToken (b43cec6): best-effort server-side deactivation
    // on master-toggle-off and logout. Deliberately NOT session-bound — this
    // often runs during teardown when the session is already rotating, and a
    // 401/stale session must never surface or block the local disable.
    @POST("device-tokens/deactivate")
    suspend fun deactivateDeviceToken(
        @Body body: DeactivateDeviceTokenRequest,
    ): MutationResponse

    @GET("device-tokens")
    suspend fun deviceTokens(): Paginated<DeviceToken>

    @POST("admin/notifications/send-test")
    suspend fun sendTestNotification(@Body body: SendTestNotificationRequest): MutationResponse

    // ── Exchange rates ──

    @GET("exchange-rates")
    suspend fun exchangeRates(): ExchangeRate

    // ── Shipments / packages ──

    @GET("shipments/summary")
    suspend fun shipmentsSummary(): ShipmentSummary

    @GET("packages")
    suspend fun packages(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("sort_by") sortBy: String,
        @Query("sort_order") sortOrder: String,
        @Query("status") status: Int?,
        @Query("search") search: String?,
        // Laravel accepts Standard | Seadrop | Express (PackageController).
        @Query("shipping_method") shippingMethod: String?,
    ): Paginated<Package>

    @GET("packages/{id}")
    suspend fun packageDetails(@Path("id") packageId: String): DataEnvelope<PackageDetail>

    // Each file part is named "invoices[]".
    @Multipart
    @POST("packages/{id}/invoices")
    suspend fun uploadPackageInvoices(
        @Path("id") packageId: String,
        @Part files: List<MultipartBody.Part>,
    ): PackageInvoicesMutationResponse

    @DELETE("packages/{id}/invoices/{invoiceId}")
    suspend fun deletePackageInvoice(
        @Path("id") packageId: String,
        @Path("invoiceId") invoiceId: Int,
    ): MutationResponse

    // Damage reports are Delivered-only server side. Each file part is named "photos[]".
    @Multipart
    @POST("packages/{id}/damage-reports")
    suspend fun reportPackageDamage(
        @Path("id") packageId: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photos: List<MultipartBody.Part>,
    ): MutationResponse

    @GET("package-statuses")
    suspend fun packageStatuses(): Paginated<PackageStatus>

    @GET("package-categories")
    suspend fun packageCategories(): Paginated<PackageCategory>

    @GET("custom-duty-rates")
    suspend fun customDutyRates(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("active_only") activeOnly: String,
        @Query("search") search: String?,
    ): Paginated<CustomDutyRate>

    @GET("custom-duty-rates/{id}")
    suspend fun customDutyRate(@Path("id") id: Int): DataEnvelope<CustomDutyRate>

    @GET("shipping-rates")
    suspend fun shippingRates(): DataEnvelope<ShippingRates>

    @POST("shipping/calculate")
    suspend fun calculateShipment(@Body body: ShipmentCalculationRequest): ShipmentCalculation

    // ── Orders ──

    @GET("orders")
    suspend fun orders(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("search") search: String?,
    ): Paginated<Order>

    @GET("orders/{id}")
    suspend fun orderDetails(@Path("id") orderId: Int): OrderDetailEnvelope

    // ── Payments / checkout ──

    @GET("payments")
    suspend fun payments(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("sort_by") sortBy: String,
        @Query("sort_order") sortOrder: String,
        @Query("type") type: String?,
        @Query("search") search: String?,
    ): Paginated<Payment>

    // JSON envelope with a URL, or raw PDF/image bytes (legacy route).
    @GET("payments/{id}/invoice")
    suspend fun paymentInvoice(@Path("id") invoiceId: Int): ResponseBody

    @POST("payments/create-checkout")
    suspend fun createCheckout(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
        @Body body: CreateCheckoutRequest,
    ): DataEnvelope<CheckoutResponse>

    @GET("payments/{sessionId}/status")
    suspend fun checkoutSessionStatus(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) ownerSessionId: String,
        @Path("sessionId") sessionId: String,
    ): DataEnvelope<CheckoutSessionStatus>

    // NCB PowerTranz (JMD): create the 3DS session (billing + card + delivery),
    // then complete after the WebView 3DS round-trip. Session-bound like Stripe.
    @POST("payments/create-ncb-session")
    suspend fun createNcbSession(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
        @Body body: CreateNcbSessionRequest,
    ): DataEnvelope<NcbSessionResponse>

    @POST("payments/ncb-complete-payment")
    suspend fun ncbCompletePayment(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
        @Body body: NcbCompletePaymentRequest,
    ): DataEnvelope<NcbCompletePaymentResponse>

    @POST("payments/create-payment-sheet")
    suspend fun createPaymentSheet(
        @Body body: CreateCheckoutRequest,
    ): DataEnvelope<PaymentSheetConfig>

    @GET("payments/payment-intent/{paymentIntentId}/status")
    suspend fun paymentIntentStatus(
        @Path("paymentIntentId") paymentIntentId: String,
    ): DataEnvelope<PaymentIntentStatus>

    // ── Cart ──

    @GET("cart")
    suspend fun cart(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
    ): DataEnvelope<CartSnapshot>

    @PUT("packages/{id}/cart")
    suspend fun addPackageToCart(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
        @Path("id") packageId: Int,
        @Body body: EmptyRequest,
    ): DataEnvelope<PackageCartMutation>

    @DELETE("packages/{id}/cart")
    suspend fun removePackageFromCart(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String,
        @Path("id") packageId: Int,
    ): DataEnvelope<PackageCartMutation>

    // ── Referrals ──

    @POST("refer-friend")
    suspend fun referFriend(@Body body: ReferFriendRequest): MutationResponse

    @GET("refer-friend/history")
    suspend fun referredFriends(
        @Query("limit") limit: Int,
        @Query("user_id") userId: Int?,
    ): Paginated<ReferredFriend>

    // Swift AirdropAPI.referredFriendsPage(userID:page:limit:) — paginated history
    // for the "View History" list (pull-to-refresh + load-more, pageSize 20).
    @GET("refer-friend/history")
    suspend fun referredFriendsPage(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("user_id") userId: Int?,
    ): Paginated<ReferredFriend>

    // ── Authorized users ──

    @GET("authorized-users")
    suspend fun authorizedUsers(): AuthorizedUsersEnvelope

    @GET("authorized-users/{id}")
    suspend fun authorizedUser(@Path("id") id: Int): AuthorizedUserEnvelope

    @POST("authorized-users")
    suspend fun addAuthorizedUser(@Body body: AuthorizedUserRequest): AuthorizedUserEnvelope

    @PUT("authorized-users/{id}")
    suspend fun updateAuthorizedUser(
        @Path("id") id: Int,
        @Body body: AuthorizedUserRequest,
    ): AuthorizedUserEnvelope

    @DELETE("authorized-users/{id}")
    suspend fun deleteAuthorizedUser(@Path("id") id: Int): MutationResponse

    // Swift PATCHes with no body; OkHttp requires one, so send {}.
    @PATCH("authorized-users/{id}/activate")
    suspend fun activateAuthorizedUser(@Path("id") id: Int, @Body body: EmptyRequest): MutationResponse

    @PATCH("authorized-users/{id}/deactivate")
    suspend fun deactivateAuthorizedUser(@Path("id") id: Int, @Body body: EmptyRequest): MutationResponse

    // ── Promotional banners ──

    @GET("promotional-banners")
    suspend fun promotionalBanners(): Paginated<PromotionalBanner>

    // ── Drop alerts ──

    // Text fields: package_couirer_number, shipping_method, package_shipper,
    // package_store, package_amount, package_consignee, package_description,
    // pckaage_invoice (always present, empty; both misspellings are the wire
    // contract). Files: preorder_invoice[0], preorder_invoice[1], ...
    @Multipart
    @POST("drop-alerts")
    suspend fun createDropAlert(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part files: List<MultipartBody.Part>,
    ): DropAlertResponse

    // ── Delivery / pickup (Laravel /delivery/*) ──

    @GET("delivery/settings")
    suspend fun deliverySettings(): DataEnvelope<DeliverySettingsPayload>

    @POST("delivery/validate-location")
    suspend fun validateDeliveryLocation(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String? = null,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String? = null,
        @Body body: ValidateLocationRequest,
    ): DeliveryValidationResponse

    @POST("delivery/save-preference")
    suspend fun saveDeliveryPreference(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String? = null,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String? = null,
        @Body body: SaveDeliveryPreferenceRequest,
    ): DataEnvelope<DeliveryPreference>

    @GET("delivery/preference")
    suspend fun deliveryPreference(
        @Header(AuthTokenStore.REQUEST_REVISION_HEADER) authRevision: String? = null,
        @Header(AuthTokenStore.REQUEST_SESSION_ID_HEADER) sessionId: String? = null,
    ): DataEnvelope<DeliveryPreference>

    @POST("delivery/reverse-geocode")
    suspend fun reverseGeocode(@Body body: ReverseGeocodeRequest): DataEnvelope<ReverseGeocodeResult>

    @POST("delivery/search-places")
    suspend fun searchPlaces(@Body body: SearchPlacesRequest): DataEnvelope<PlaceSearchResults>

    // Account security — Swift §D.1/D.2 parity; live on pre_staging.
    @GET("user/sessions")
    suspend fun activeSessions(): com.ga.airdrop.data.model.ActiveSessionsResponse

    @DELETE("user/sessions/{id}")
    suspend fun revokeSession(
        @Path("id") sessionId: String,
    ): kotlinx.serialization.json.JsonObject

    @POST("user/export-data")
    suspend fun requestPersonalDataExport(): com.ga.airdrop.data.model.ExportPersonalDataResponse

    /**
     * POST /diagnostics/crashes — anonymous-friendly crash intake
     * (DiagnosticsController; Swift CrashCapture parity). Raw Response so
     * the flusher can key deletion off the HTTP status alone.
     */
    @POST("diagnostics/crashes")
    suspend fun reportCrash(
        @Body body: kotlinx.serialization.json.JsonObject,
    ): retrofit2.Response<kotlinx.serialization.json.JsonObject>
}
