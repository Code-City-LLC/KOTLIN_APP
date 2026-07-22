package com.ga.airdrop.feature.shop

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.external.AffiliateAndMediaLinks
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.repo.ProductsRepository

interface AffiliatePromotionProductsSource {
    suspend fun featuredProducts(): Result<List<ShopProduct>>
    suspend fun saleProducts(): Result<List<ShopProduct>>
}

internal class RepositoryAffiliatePromotionProductsSource(
    private val repository: ProductsRepository = ProductsRepository(ApiClient.service),
) : AffiliatePromotionProductsSource {
    override suspend fun featuredProducts(): Result<List<ShopProduct>> =
        repository.promotionFeaturedProducts().map { rows ->
            rows.mapNotNull(AuctionProduct::toPromotionProductOrNull)
        }

    override suspend fun saleProducts(): Result<List<ShopProduct>> =
        repository.promotionSaleProducts().map { rows ->
            rows.mapNotNull(AuctionProduct::toPromotionProductOrNull)
        }
}

/**
 * Mac-family products lead, then other Apple products. The original backend
 * order is the tie-breaker; no client timestamp parsing can reorder equal
 * classes or revive stale rows.
 */
internal fun eligibleAmazonAppleProducts(
    products: List<ShopProduct>,
    limit: Int = Int.MAX_VALUE,
    canonicalWebBase: String = BuildConfig.WEB_BASE_URL,
): List<ShopProduct> =
    products
        .mapIndexedNotNull { index, product ->
            val title = product.title.trim()
            val image = AffiliateAndMediaLinks.normalizeMediaUrl(product.imageUrl, canonicalWebBase)
                ?: return@mapIndexedNotNull null
            val affiliateUrl =
                AffiliateAndMediaLinks.validateAmazonAffiliateUrl(product.amazonUrl)
                    ?: return@mapIndexedNotNull null
            if (title.isEmpty() || !product.priceUsd.isFinite() || product.priceUsd <= 0.0) {
                return@mapIndexedNotNull null
            }

            val lowercaseTitle = title.lowercase()
            if (ACCESSORY_TERMS.any(lowercaseTitle::contains)) return@mapIndexedNotNull null
            val priority = when {
                MAC_TERMS.any(lowercaseTitle::contains) -> 0
                APPLE_DEVICE_TERMS.any { term -> lowercaseTitle.containsTitleTerm(term) } -> 1
                else -> return@mapIndexedNotNull null
            }
            RankedPromotion(
                priority = priority,
                backendIndex = index,
                product = product.copy(
                    title = title,
                    imageUrl = image,
                    amazonUrl = affiliateUrl,
                ),
            )
        }
        .sortedWith(compareBy<RankedPromotion> { it.priority }.thenBy { it.backendIndex })
        .take(limit.coerceAtLeast(0))
        .map(RankedPromotion::product)

internal fun completeSalePromotionProducts(
    products: List<ShopProduct>,
    limit: Int = 4,
    canonicalWebBase: String = BuildConfig.WEB_BASE_URL,
): List<ShopProduct> =
    products.mapNotNull { product ->
        val title = product.title.trim()
        val image = AffiliateAndMediaLinks.normalizeMediaUrl(product.imageUrl, canonicalWebBase)
            ?: return@mapNotNull null
        if (title.isEmpty() || !product.priceUsd.isFinite() || product.priceUsd <= 0.0) {
            return@mapNotNull null
        }
        product.copy(title = title, imageUrl = image)
    }.take(limit.coerceAtLeast(0))

private fun AuctionProduct.toPromotionProductOrNull(): ShopProduct? {
    val productId = id ?: return null
    val rawTitle = name?.trim()?.takeIf(String::isNotEmpty)
        ?: title?.trim()?.takeIf(String::isNotEmpty)
        ?: return null
    return ShopProduct(
        id = productId,
        slug = slug,
        title = rawTitle,
        imageUrl = displayImageUrl?.trim()?.takeIf(String::isNotEmpty),
        priceUsd = displayPriceUsd,
        regularPriceUsd = regularPrice
            ?.replace(",", "")
            ?.trim { it == '$' || it == ' ' }
            ?.toDoubleOrNull(),
        inventory = inventory,
        description = description,
        amazonUrl = amazonUrl,
        packageId = checkoutPackageId,
        createdAt = createdAt,
    )
}

private data class RankedPromotion(
    val priority: Int,
    val backendIndex: Int,
    val product: ShopProduct,
)

private val MAC_TERMS = listOf("macbook", "imac", "mac mini", "mac studio", "mac pro")

private val APPLE_DEVICE_TERMS = listOf(
    "iphone",
    "ipad",
    "airpods",
    "apple watch",
    "apple",
)

private fun String.containsTitleTerm(term: String): Boolean {
    var startIndex = indexOf(term)
    while (startIndex >= 0) {
        val endIndex = startIndex + term.length
        val startsAtBoundary = startIndex == 0 || !this[startIndex - 1].isLetterOrDigit()
        val endsAtBoundary = endIndex == length || !this[endIndex].isLetterOrDigit()
        if (startsAtBoundary && endsAtBoundary) return true
        startIndex = indexOf(term, startIndex + 1)
    }
    return false
}

private val ACCESSORY_TERMS = listOf(
    "case",
    "cover",
    "sleeve",
    "skin",
    "screen protector",
    "charger",
    "adapter",
    "cable",
    "stand",
    "dock",
    "hub",
    "bag",
    "replacement",
    "keyboard protector",
    "mouse pad",
)
