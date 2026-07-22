package com.ga.airdrop.feature.more2

import com.ga.airdrop.feature.shop.ShopProduct
import org.junit.Assert.assertEquals
import org.junit.Test

class PromotionsNavigationTest {

    @Test
    fun `sale highlight uses canonical product-details route with slug`() {
        val product = ShopProduct(
            id = 42,
            slug = "macbook-pro-sale",
            title = "MacBook Pro Sale",
        )

        assertEquals(
            "auctionProductDetails/macbook-pro-sale?featured=false",
            promotionSaleDetailsRoute(product),
        )
    }

    @Test
    fun `sale highlight uses canonical product-details route with id fallback`() {
        val product = ShopProduct(id = 42, slug = " ", title = "AirDrop Sale")

        assertEquals(
            "auctionProductDetails/42?featured=false",
            promotionSaleDetailsRoute(product),
        )
    }
}
