package com.ga.airdrop.feature.shop

import com.ga.airdrop.feature.cart.CartStore

/*
 * SHOP data contracts.
 *
 * The shared data layer (app/src/main/java/com/ga/airdrop/data/) was not
 * present while this feature was written, so per the multi-agent playbook the
 * ViewModels constructor-inject the minimal repo interfaces below. The
 * orchestrator binds real implementations once in [ShopRepoProvider]
 * (single reconcile point). Every method documents the exact endpoint +
 * fields (source of truth: SWIFT_APP AirdropAPI.swift / APIEndpoints.swift).
 */

/**
 * UI model for auction/featured products. Field mapping from the API product
 * (Swift `AirdropAPI.AuctionProduct`):
 *  - id                       ← `id`
 *  - slug                     ← `slug`
 *  - title                    ← `name` ?? `title` ?? "Product"
 *  - imageUrl                 ← `primary_image.url` ?? `image_url` ?? first `images[].url` (prefer `is_primary`)
 *  - priceUsd                 ← first non-empty of `current_price`/`sale_price`/`price`/`regular_price`/`price_usd`
 *  - regularPriceUsd          ← `regular_price` (strikethrough shown only when > priceUsd)
 *  - inventory                ← `inventory`
 *  - description              ← `description`
 *  - amazonUrl                ← `amazon_url` (featured products purchase link)
 *  - packageId                ← `package_id` ?? `package.package_id` ?? `package.id` (Stripe checkout id)
 *  - createdAt                ← `created_at` (used for "Newest" sort)
 */
data class ShopProduct(
    val id: Int,
    val slug: String? = null,
    val title: String = "",
    val imageUrl: String? = null,
    val priceUsd: Double = 0.0,
    val regularPriceUsd: Double? = null,
    val inventory: Int? = null,
    val description: String? = null,
    val amazonUrl: String? = null,
    val packageId: Int? = null,
    val createdAt: String? = null,
)

fun ShopProduct.toCartLine(qty: Int = 1): CartStore.CartLine = CartStore.CartLine(
    id = id,
    packageId = packageId,
    imageUrl = imageUrl,
    title = title,
    qty = qty,
    priceUsd = priceUsd,
    isAuction = true,
)

/** Route argument for details: slug when present, else the numeric id. */
val ShopProduct.routeSlug: String get() = slug?.takeIf { it.isNotBlank() } ?: id.toString()

interface ShopProductsRepository {

    /**
     * RECONCILE: GET /products
     * query: page, per_page, search (only when >= 3 chars), order=created_at,
     * direction=desc, in_stock=1 — response `{ data: [product...] }` or
     * `{ data: { items: [...] } }` (Swift AuctionListResponse handles both).
     */
    suspend fun auctionProducts(page: Int, perPage: Int, search: String? = null): Result<List<ShopProduct>>

    /**
     * RECONCILE: GET /featured-products
     * query: page, per_page, search (when >= 3 chars, plus in_stock=1 and
     * on_sale=1 like Swift `featuredProductsShortlist`). Same envelope as
     * /products; identical product shape.
     */
    suspend fun featuredProducts(page: Int, perPage: Int, search: String? = null): Result<List<ShopProduct>>

    /**
     * RECONCILE: GET /products/{slug}
     * (fallback used by Swift: GET /products?slug={slug}&page=1&per_page=1 →
     * first item). `featured` selects GET /featured-products/{slug} instead.
     * Response fields per [ShopProduct] mapping.
     */
    suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct>
}

/** Billing prefill for the cart form — Swift `AirdropAPI.currentUser()`. */
data class ShopBillingProfile(
    val firstName: String = "",
    val lastName: String = "",
    val address1: String = "",
    val state: String = "",
    val city: String = "",
    val country: String = "",
)

interface ShopCheckoutRepository {

    /**
     * RECONCILE: POST /payments/create-checkout
     * body: { "package_ids": [Int], "currency": "USD"|"JMD", "is_auction": true }
     * response: { "success": Bool, "data": { "checkout_url": String,
     * "session_id": String, "amount": Double, "currency": String,
     * "package_count": Int }, "message": String? } → returns checkout_url.
     */
    suspend fun createCheckout(packageIds: List<Int>, currency: String, isAuction: Boolean = true): Result<String>

    /**
     * RECONCILE: GET /exchange-rates (no auth) → { "usd_to_jmd": Double }.
     */
    suspend fun exchangeRate(): Result<Double>

    /**
     * RECONCILE: GET /user (Swift currentUser) → fields first_name,
     * last_name, address, state, city, country (autofill for the cart
     * billing form).
     */
    suspend fun billingProfile(): Result<ShopBillingProfile>
}

/**
 * Single binding point for the shop/cart repos.
 * RECONCILE: the data-layer agent (or orchestrator) assigns real
 * implementations backed by `com.ga.airdrop.data.*`, e.g. in Application
 * startup:
 *     ShopRepoProvider.products = DataShopProductsRepository()
 *     ShopRepoProvider.checkout = DataShopCheckoutRepository()
 */
object ShopRepoProvider {
    @Volatile var products: ShopProductsRepository = UnboundShopProductsRepository
    @Volatile var checkout: ShopCheckoutRepository = UnboundShopCheckoutRepository
}

private val unbound = IllegalStateException(
    "Shop data layer not wired — bind ShopRepoProvider to com.ga.airdrop.data repositories"
)

private object UnboundShopProductsRepository : ShopProductsRepository {
    override suspend fun auctionProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
        Result.failure(unbound)

    override suspend fun featuredProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
        Result.failure(unbound)

    override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
        Result.failure(unbound)
}

private object UnboundShopCheckoutRepository : ShopCheckoutRepository {
    override suspend fun createCheckout(packageIds: List<Int>, currency: String, isAuction: Boolean): Result<String> =
        Result.failure(unbound)

    override suspend fun exchangeRate(): Result<Double> = Result.failure(unbound)

    override suspend fun billingProfile(): Result<ShopBillingProfile> = Result.failure(unbound)
}

/**
 * Hand-off holder for the Auction Checkout screen. The `auctionCheckout`
 * route carries no argument (mirrors Swift FigmaRouteResolver pushing
 * `FigmaAuctionProductCheckoutViewController(product:)`), so the navigating
 * screen stores the product here right before `navigate(AUCTION_CHECKOUT)`.
 */
object ShopCheckoutStore {
    @Volatile var product: ShopProduct? = null

    /**
     * Deep-link fallback — Swift FigmaRouteViewController:727 constructs the
     * checkout VC with `product: nil` plus an id/slug reference and lets the
     * VC resolve the product itself. When a notification routes to
     * AUCTION_CHECKOUT with no in-memory [product], the referenceID lands
     * here and AuctionCheckoutViewModel fetches by it (WORK ORDER B4 —
     * previously nothing wrote this store, so the deep-linked checkout was
     * always "Product unavailable").
     */
    @Volatile var pendingRef: String? = null
}

/**
 * Hand-off holder for Product Details (VERIFICATION_LEDGER P1). Swift pushes
 * `FigmaFeatureProductDetailsViewController(product:)` — the object travels
 * with the navigation. Laravel has NO featured show route (`/products/{slug}`
 * 404s for featured slugs; `featured-products?slug=` returns 200-but-empty),
 * so re-fetching by slug broke every featured detail. The list stores the
 * tapped product here right before navigating; the details ViewModel consumes
 * it keyed by routeSlug, so a stale entry can never serve the wrong product
 * and deep links (no entry) still use the network path.
 */
object ShopProductHandoffStore {
    @Volatile
    private var entry: ShopProduct? = null

    fun put(product: ShopProduct) {
        entry = product
    }

    /** One-shot: returns the stored product only if it matches [routeSlug]. */
    fun consume(routeSlug: String): ShopProduct? {
        val handed = entry ?: return null
        entry = null
        return handed.takeIf { it.routeSlug == routeSlug }
    }
}
