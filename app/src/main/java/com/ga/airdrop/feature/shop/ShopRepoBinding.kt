package com.ga.airdrop.feature.shop

import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.PackagesRepository
import com.ga.airdrop.data.repo.PaymentsRepository
import com.ga.airdrop.data.repo.ProductsRepository
import com.ga.airdrop.data.repo.UserRepository

/**
 * Binds the shop feature's repo contracts to the shared data layer
 * (single reconcile point per ShopData.kt).
 */
object ShopRepoBinding {

    fun install() {
        ShopRepoProvider.products = DataShopProductsRepository()
        ShopRepoProvider.checkout = DataShopCheckoutRepository()
    }
}

private fun AuctionProduct.toShopProduct(): ShopProduct = ShopProduct(
    id = id ?: 0,
    slug = slug,
    title = displayTitle,
    imageUrl = displayImageUrl,
    priceUsd = displayPriceUsd,
    regularPriceUsd = regularPrice?.replace(",", "")?.trim { it == '$' || it == ' ' }?.toDoubleOrNull(),
    inventory = inventory,
    description = description,
    amazonUrl = amazonUrl,
    packageId = checkoutPackageId,
    createdAt = createdAt,
)

private class DataShopProductsRepository(
    private val repo: ProductsRepository = ProductsRepository(ApiClient.service),
) : ShopProductsRepository {

    override suspend fun auctionProducts(
        page: Int,
        perPage: Int,
        search: String?,
    ): Result<List<ShopProduct>> =
        // Swift auctionProducts: order=created_at desc + in_stock=1 so the
        // grid hides out-of-stock items and matches the iOS ordering.
        repo.auctionProducts(
            page = page,
            perPage = perPage,
            filters = com.ga.airdrop.data.model.ProductFilters(
                search = search,
                order = "created_at",
                direction = "desc",
                inStock = true,
            ),
        ).map { list -> list.map { it.toShopProduct() } }

    override suspend fun featuredProducts(
        page: Int,
        perPage: Int,
        search: String?,
    ): Result<List<ShopProduct>> =
        // Swift featuredProducts sends search (>=3 chars) + in_stock/on_sale
        // to the SERVER — client-side filtering broke pagination.
        repo.featuredProducts(
            page = page,
            perPage = perPage,
            filters = com.ga.airdrop.data.model.ProductFilters(
                search = search?.trim()?.takeIf { it.length >= 3 },
                order = "created_at",
                direction = "desc",
                inStock = true,
                onSale = true,
            ),
        ).map { list -> list.map { it.toShopProduct() } }

    override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
        runCatching {
            // Primary: the dedicated detail endpoint GET /products/{slug}
            // (Laravel route-model binding). The list ?slug= filter returns
            // 200 with an EMPTY data array, which is why the detail screen was
            // showing "Product not found" for every card. A featured product
            // is still a Product, so this endpoint serves both.
            val fromShow = runCatching {
                ApiClient.service.productDetail(slug).data?.product
            }.getOrNull()
            if (fromShow != null) return@runCatching fromShow.toShopProduct()

            // Fallback: list query (covers numeric-id routeSlug when a product
            // has no slug — Laravel /products supports ?id=).
            suspend fun query(params: Map<String, String>) =
                if (featured) ApiClient.service.featuredProducts(params).items
                else ApiClient.service.products(params).items
            var items = query(mapOf("slug" to slug, "page" to "1", "per_page" to "1"))
            if (items.isEmpty() && slug.all { it.isDigit() }) {
                items = query(mapOf("id" to slug, "page" to "1", "per_page" to "1"))
            }
            val match = items.firstOrNull { it.slug == slug || it.id?.toString() == slug }
                ?: items.firstOrNull()
                ?: error("Product not found")
            match.toShopProduct()
        }
}

private class DataShopCheckoutRepository(
    private val payments: PaymentsRepository = PaymentsRepository(ApiClient.service),
    private val misc: MiscRepository = MiscRepository(ApiClient.service),
    private val user: UserRepository = UserRepository(ApiClient.service),
    private val packages: PackagesRepository = PackagesRepository(ApiClient.service),
) : ShopCheckoutRepository {

    override suspend fun createCheckout(
        packageIds: List<Int>,
        currency: String,
        isAuction: Boolean,
    ): Result<String> =
        payments.createCheckout(packageIds, currency, isAuction)
            .mapCatching { it.checkoutUrl ?: error("Missing checkout URL") }

    override suspend fun syncServerCartForCheckout(packageIds: List<Int>): Result<Unit> = runCatching {
        packageIds.distinct().sorted().forEach { packageId ->
            packages.addPackageToCart(packageId).getOrThrow()
        }
    }

    override suspend fun exchangeRate(): Result<Double> =
        misc.exchangeRate().mapCatching { it.usdToJmd ?: error("Missing exchange rate") }

    override suspend fun billingProfile(): Result<ShopBillingProfile> =
        user.currentUser().map {
            ShopBillingProfile(
                firstName = it.firstName.orEmpty(),
                lastName = it.lastName.orEmpty(),
                address1 = it.address.orEmpty(),
                state = it.state.orEmpty(),
                city = it.city.orEmpty(),
                country = it.country.orEmpty(),
            )
        }
}
