package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.model.ProductFilters

class ProductsRepository(private val service: AirdropApiService) {

    suspend fun auctionProducts(
        page: Int = 1,
        perPage: Int = 15,
        filters: ProductFilters = ProductFilters(),
    ): Result<List<AuctionProduct>> = apiResult {
        service.products(filters.toQueryMap(page, perPage)).items
    }

    suspend fun auctionProductsShortlist(search: String? = null): Result<List<AuctionProduct>> =
        apiResult {
            val params = buildMap {
                put("page", "1")
                put("per_page", "4")
                put("order", "created_at")
                put("direction", "desc")
                put("in_stock", "1")
                normalizedSearch(search)?.let { put("search", it) }
            }
            service.products(params).items
        }

    suspend fun featuredProducts(
        page: Int = 1,
        perPage: Int = 15,
        filters: ProductFilters = ProductFilters(),
    ): Result<List<AuctionProduct>> = apiResult {
        service.featuredProducts(filters.toQueryMap(page, perPage)).items
    }

    /** Laravel-curated Amazon candidates, newest-first for deterministic ranking. */
    suspend fun promotionFeaturedProducts(): Result<List<AuctionProduct>> =
        featuredProducts(
            page = 1,
            perPage = 50,
            filters = ProductFilters(order = "created_at", direction = "desc"),
        )

    /** Current AirDrop sale highlights, newest-first and server-filtered. */
    suspend fun promotionSaleProducts(): Result<List<AuctionProduct>> =
        auctionProducts(
            page = 1,
            perPage = 8,
            filters = ProductFilters(
                order = "created_at",
                direction = "desc",
                inStock = true,
                onSale = true,
            ),
        )

    suspend fun featuredProductsShortlist(search: String? = null): Result<List<AuctionProduct>> =
        apiResult {
            val params = buildMap {
                put("page", "1")
                put("per_page", "4")
                put("order", "created_at")
                put("direction", "desc")
                normalizedSearch(search)?.let {
                    put("search", it)
                    put("in_stock", "1")
                    put("on_sale", "1")
                }
            }
            service.featuredProducts(params).items
        }

    suspend fun searchProducts(query: String, limit: Int = 20): Result<List<AuctionProduct>> {
        val trimmed = query.trim()
        if (trimmed.length < 3) return Result.success(emptyList())
        return auctionProducts(
            page = 1,
            perPage = limit,
            filters = ProductFilters(
                search = trimmed,
                order = "created_at",
                direction = "desc",
                inStock = true,
            ),
        )
    }

    suspend fun searchFeaturedProducts(query: String, limit: Int = 20): Result<List<AuctionProduct>> =
        featuredProducts(page = 1, perPage = limit, filters = ProductFilters(search = query))
}
