package com.ga.airdrop.feature.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeaturedPromotionCatalogTest {

    @Test
    fun `eligible catalog keeps backend order within Mac-first then Apple groups`() {
        val products = listOf(
            product(id = 1, title = "Apple iPhone 16 Pro"),
            product(id = 2, title = "MacBook Pro M4"),
            product(id = 3, title = "Apple iPad Air"),
            product(id = 4, title = "iMac 24-inch"),
        )

        assertEquals(
            listOf(2, 4, 1, 3),
            eligibleAppleAmazonProducts(products).map(ShopProduct::id),
        )
    }

    @Test
    fun `eligibility rejects accessories incomplete products and invalid associate links`() {
        val products = listOf(
            product(id = 1, title = "MacBook Pro protective case"),
            product(id = 2, title = "Apple iPhone", imageUrl = null),
            product(id = 3, title = "Apple iPad", priceUsd = 0.0),
            product(id = 4, title = "Apple Watch", amazonUrl = "https://amazon.com/dp/4"),
            product(id = 5, title = "Apple AirPods", amazonUrl = "http://amazon.com/dp/5?tag=airdrop00-20"),
            product(id = 6, title = "Apple Mac mini", amazonUrl = "https://amazon.com.evil.test/dp/6?tag=airdrop00-20"),
            product(id = 7, title = "Unrelated television"),
        )

        assertEquals(emptyList<ShopProduct>(), eligibleAppleAmazonProducts(products))
    }

    @Test
    fun `associate URL accepts approved Amazon storefront and exact tag only`() {
        assertEquals(
            "https://www.amazon.com/dp/B0TEST?ref=card&tag=airdrop00-20",
            product(
                id = 1,
                title = "MacBook Air",
                amazonUrl = "https://www.amazon.com/dp/B0TEST?ref=card&tag=airdrop00-20",
            ).amazonAssociatesUrlOrNull(),
        )
        assertNull(
            product(
                id = 2,
                title = "MacBook Air",
                amazonUrl = "https://amazon.com/dp/B0TEST?tag=other-20",
            ).amazonAssociatesUrlOrNull(),
        )
    }

    private fun product(
        id: Int,
        title: String,
        imageUrl: String? = "https://images.example.test/$id.jpg",
        priceUsd: Double = 999.0,
        amazonUrl: String? = "https://www.amazon.com/dp/$id?tag=airdrop00-20",
    ) = ShopProduct(
        id = id,
        title = title,
        imageUrl = imageUrl,
        priceUsd = priceUsd,
        amazonUrl = amazonUrl,
    )
}
