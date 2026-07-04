package com.ga.airdrop.feature.shop

import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.repo.MiscRepository
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
        repo.auctionProducts(
            page = page,
            perPage = perPage,
            filters = com.ga.airdrop.data.model.ProductFilters(search = search),
        ).map { list -> list.map { it.toShopProduct() } }

    override suspend fun featuredProducts(
        page: Int,
        perPage: Int,
        search: String?,
    ): Result<List<ShopProduct>> =
        repo.featuredProducts(page = page, perPage = perPage)
            .map { list ->
                val mapped = list.map { it.toShopProduct() }
                val query = search?.trim()?.takeIf { it.length >= 3 }?.lowercase()
                if (query == null) mapped
                else mapped.filter { it.title.lowercase().contains(query) }
            }

    override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
        runCatching {
            val params = mapOf("slug" to slug, "page" to "1", "per_page" to "1")
            val items = if (featured) {
                ApiClient.service.featuredProducts(params).items
            } else {
                ApiClient.service.products(params).items
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
) : ShopCheckoutRepository {

    override suspend fun createCheckout(
        packageIds: List<Int>,
        currency: String,
        isAuction: Boolean,
    ): Result<String> =
        payments.createCheckout(packageIds, currency, isAuction)
            .mapCatching { it.checkoutUrl ?: error("Missing checkout URL") }

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
