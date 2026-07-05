package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuctionProduct(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    val title: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val description: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val price: String? = null,
    @SerialName("price_usd")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val priceUsd: Double? = null,
    @SerialName("current_price")
    @Serializable(with = FlexibleStringSerializer::class)
    val currentPrice: String? = null,
    @SerialName("regular_price")
    @Serializable(with = FlexibleStringSerializer::class)
    val regularPrice: String? = null,
    @SerialName("sale_price")
    @Serializable(with = FlexibleStringSerializer::class)
    val salePrice: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val inventory: Int? = null,
    @SerialName("discount_percentage")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val discountPercentage: Double? = null,
    @SerialName("is_on_sale")
    val isOnSale: Boolean? = null,
    val status: String? = null,
    @SerialName("amazon_url")
    val amazonUrl: String? = null,
    @SerialName("package_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val packageId: Int? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("primary_image")
    val primaryImage: AuctionProductImage? = null,
    val images: List<AuctionProductImage>? = null,
    @SerialName("package")
    val productPackage: AuctionProductPackage? = null,
    @SerialName("created_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val createdAt: String? = null,
    @SerialName("updated_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val updatedAt: String? = null,
) {
    val displayTitle: String get() = name ?: title ?: "Product"

    val displayImageUrl: String?
        get() = primaryImage?.url
            ?: imageUrl
            ?: images?.firstOrNull { it.isPrimary == true }?.url
            ?: images?.firstOrNull()?.url

    val displayPriceUsd: Double
        get() = currencyDouble(currentPrice)
            ?: currencyDouble(salePrice)
            ?: currencyDouble(price)
            ?: currencyDouble(regularPrice)
            ?: priceUsd
            ?: 0.0

    val isAvailable: Boolean get() = status == null || status == "available"

    val checkoutPackageId: Int?
        get() = packageId ?: productPackage?.packageId ?: productPackage?.id

    private fun currencyDouble(raw: String?): Double? {
        if (raw.isNullOrEmpty()) return null
        return raw.replace(",", "").trim { it == '$' || it == ' ' }.toDoubleOrNull()
    }
}

@Serializable
data class AuctionProductImage(
    val id: Int? = null,
    @SerialName("product_id") val productId: Int? = null,
    val url: String? = null,
    val path: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class AuctionProductPackage(
    val id: Int? = null,
    @SerialName("package_id") val packageId: Int? = null,
    @SerialName("tracking_code") val trackingCode: String? = null,
    val status: String? = null,
    @SerialName("account_number") val accountNumber: String? = null,
)

/** Envelope for GET /products/{slug}: { data: { product: {...} } }. */
@Serializable
data class ProductDetailEnvelope(val data: ProductDetailData? = null)

@Serializable
data class ProductDetailData(val product: AuctionProduct? = null)

data class ProductFilters(
    val search: String? = null,
    val order: String? = null,
    val direction: String? = null,
    val slug: String? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val inventoryMin: Int? = null,
    val inventoryMax: Int? = null,
    val inStock: Boolean? = null,
    val onSale: Boolean? = null,
    val createdAfter: String? = null,
    val createdBefore: String? = null,
) {
    fun toQueryMap(page: Int, perPage: Int): Map<String, String> = buildMap {
        put("page", page.toString())
        put("per_page", perPage.toString())
        search?.trim()?.takeIf { it.isNotEmpty() }?.let { put("search", it) }
        order?.takeIf { it.isNotEmpty() }?.let { put("order", it) }
        direction?.takeIf { it.isNotEmpty() }?.let { put("direction", it) }
        slug?.takeIf { it.isNotEmpty() }?.let { put("slug", it) }
        priceMin?.let { put("price_min", it.toString()) }
        priceMax?.let { put("price_max", it.toString()) }
        inventoryMin?.let { put("inventory_min", it.toString()) }
        inventoryMax?.let { put("inventory_max", it.toString()) }
        inStock?.let { put("in_stock", if (it) "1" else "0") }
        onSale?.let { put("on_sale", if (it) "1" else "0") }
        createdAfter?.takeIf { it.isNotEmpty() }?.let { put("created_after", it) }
        createdBefore?.takeIf { it.isNotEmpty() }?.let { put("created_before", it) }
    }
}
