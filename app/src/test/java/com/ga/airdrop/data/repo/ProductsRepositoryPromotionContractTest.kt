package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.model.Paginated
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductsRepositoryPromotionContractTest {

    @Test
    fun `promotion feeds use canonical featured and on-sale query composition`() = runTest {
        var featuredParams: Map<String, String>? = null
        var saleParams: Map<String, String>? = null
        val service = proxyService(
            onFeatured = { featuredParams = it },
            onProducts = { saleParams = it },
        )
        val repository = ProductsRepository(service)

        repository.promotionFeaturedProducts().getOrThrow()
        repository.promotionSaleProducts().getOrThrow()

        assertEquals(
            mapOf(
                "page" to "1",
                "per_page" to "50",
                "order" to "created_at",
                "direction" to "desc",
            ),
            featuredParams,
        )
        assertEquals(
            mapOf(
                "page" to "1",
                "per_page" to "8",
                "order" to "created_at",
                "direction" to "desc",
                "in_stock" to "1",
                "on_sale" to "1",
            ),
            saleParams,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun proxyService(
        onFeatured: (Map<String, String>) -> Unit,
        onProducts: (Map<String, String>) -> Unit,
    ): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            val params = args?.getOrNull(0) as? Map<String, String>
                ?: throw AssertionError("Missing query map for ${method.name}")
            when (method.name) {
                "featuredProducts" -> onFeatured(params)
                "products" -> onProducts(params)
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
            Paginated<AuctionProduct>(emptyList())
        } as AirdropApiService
}
