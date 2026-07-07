package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.model.Paginated
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductsRepositoryShortlistTest {

    @Test
    fun featuredShortlistAlwaysSendsSwiftServerFilters() = runBlocking {
        val capture = CapturedProductCalls()
        val repo = ProductsRepository(productService(capture))

        val emptySearch = repo.featuredProductsShortlist().getOrThrow()
        val searched = repo.featuredProductsShortlist("  shoes  ").getOrThrow()

        assertEquals(listOf("featured-product"), emptySearch.map { it.slug })
        assertEquals(listOf("featured-product"), searched.map { it.slug })
        assertEquals(
            mapOf(
                "page" to "1",
                "per_page" to "4",
                "order" to "created_at",
                "direction" to "desc",
                "in_stock" to "1",
                "on_sale" to "1",
            ),
            capture.featuredParams[0],
        )
        assertEquals(
            mapOf(
                "page" to "1",
                "per_page" to "4",
                "order" to "created_at",
                "direction" to "desc",
                "in_stock" to "1",
                "on_sale" to "1",
                "search" to "shoes",
            ),
            capture.featuredParams[1],
        )
    }

    @Test
    fun auctionShortlistKeepsExistingInStockServerFilter() = runBlocking {
        val capture = CapturedProductCalls()
        val repo = ProductsRepository(productService(capture))

        repo.auctionProductsShortlist().getOrThrow()

        assertEquals(
            mapOf(
                "page" to "1",
                "per_page" to "4",
                "order" to "created_at",
                "direction" to "desc",
                "in_stock" to "1",
            ),
            capture.productsParams.single(),
        )
    }

    private class CapturedProductCalls {
        val productsParams = mutableListOf<Map<String, String>>()
        val featuredParams = mutableListOf<Map<String, String>>()
    }

    @Suppress("UNCHECKED_CAST")
    private fun productService(capture: CapturedProductCalls): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "products" -> {
                    capture.productsParams += args?.getOrNull(0) as Map<String, String>
                    Paginated(listOf(AuctionProduct(id = 1, slug = "auction-product")))
                }
                "featuredProducts" -> {
                    capture.featuredParams += args?.getOrNull(0) as Map<String, String>
                    Paginated(listOf(AuctionProduct(id = 2, slug = "featured-product")))
                }
                else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService
}
