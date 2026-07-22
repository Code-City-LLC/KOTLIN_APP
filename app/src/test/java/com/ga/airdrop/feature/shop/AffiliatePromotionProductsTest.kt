package com.ga.airdrop.feature.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffiliatePromotionProductsTest {

    @Test
    fun `ranking puts Mac before Apple and preserves backend order within each class`() {
        val ranked = eligibleAmazonAppleProducts(
            products = listOf(
                product(1, "iPhone 17 Pro"),
                product(2, "Mac Studio M4 Max"),
                product(3, "MacBook Pro M4"),
                product(4, "iPad Pro 13-inch"),
                product(5, "AirPods Pro"),
                product(6, "Apple Watch Series 10"),
                product(7, "Apple TV 4K"),
            ),
            canonicalWebBase = WEB_BASE,
        )

        assertEquals(listOf(2, 3, 1, 4, 5, 6, 7), ranked.map(ShopProduct::id))
    }

    @Test
    fun `Apple device fallback stays explicit and does not admit unrelated brands or substrings`() {
        val ranked = eligibleAmazonAppleProducts(
            products = listOf(
                product(1, "Samsung Galaxy Watch"),
                product(2, "Pineapple Slicer"),
                product(3, "Air Pod Bluetooth Earbuds"),
                product(4, "iPhone 17 Pro Case"),
                product(5, "iPad Pro Charger"),
                product(6, "AirPods Pro Cover"),
                product(7, "iPhone 17 Pro"),
                product(8, "iPad Pro"),
                product(9, "AirPods Pro"),
                product(10, "Apple Watch Ultra"),
            ),
            canonicalWebBase = WEB_BASE,
        )

        assertEquals(listOf(7, 8, 9, 10), ranked.map(ShopProduct::id))
    }

    @Test
    fun `ranking excludes accessories incomplete rows non-Apple rows and invalid links`() {
        val ranked = eligibleAmazonAppleProducts(
            products = listOf(
                product(1, "MacBook Pro Case"),
                product(2, "MacBook Charger"),
                product(3, "Windows Laptop"),
                product(4, "Mac mini", price = 0.0),
                product(5, "iMac", image = ""),
                product(6, "Apple Watch", amazon = "https://amazon.com/dp/x"),
                product(7, "MacBook Air", amazon = "https://evil.example/x?tag=partner"),
                product(8, "Apple TV", amazon = "https://a.co/d/valid"),
            ),
            canonicalWebBase = WEB_BASE,
        )

        assertEquals(listOf(8), ranked.map(ShopProduct::id))
        assertEquals("https://a.co/d/valid", ranked.single().amazonUrl)
    }

    @Test
    fun `ranking normalizes eligible media and affiliate URLs`() {
        val ranked = eligibleAmazonAppleProducts(
            products = listOf(
                product(
                    id = 10,
                    title = "MacBook Air",
                    image = "/storage/mac.png",
                    amazon = "http://www.amazon.com/dp/mac?tag=partner",
                ),
            ),
            canonicalWebBase = WEB_BASE,
        )

        assertEquals("https://airdropja.test/storage/mac.png", ranked.single().imageUrl)
        assertEquals("https://www.amazon.com/dp/mac?tag=partner", ranked.single().amazonUrl)
    }

    @Test
    fun `sale highlights keep backend order and require complete positive rows`() {
        val rows = completeSalePromotionProducts(
            products = listOf(
                product(1, "First sale", amazon = null),
                product(2, "", amazon = null),
                product(3, "No image", image = null, amazon = null),
                product(4, "No price", price = -1.0, amazon = null),
                product(5, "Second sale", image = "http://cdn.test/sale.png", amazon = null),
            ),
            canonicalWebBase = WEB_BASE,
        )

        assertEquals(listOf(1, 5), rows.map(ShopProduct::id))
        assertEquals("https://cdn.test/sale.png", rows.last().imageUrl)
        assertTrue(rows.all { it.priceUsd > 0.0 })
    }

    private fun product(
        id: Int,
        title: String,
        price: Double = 999.0,
        image: String? = "https://cdn.test/$id.png",
        amazon: String? = "https://www.amazon.com/dp/$id?tag=partner",
    ) = ShopProduct(
        id = id,
        title = title,
        priceUsd = price,
        imageUrl = image,
        amazonUrl = amazon,
    )

    private companion object {
        const val WEB_BASE = "https://airdropja.test"
    }
}
