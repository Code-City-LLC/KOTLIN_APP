package com.ga.airdrop.feature.shop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * VERIFICATION_LEDGER P1 regression guard.
 *
 * Every featured product opened "Product not found": the detail route
 * re-fetched by slug, but Laravel has no featured show route —
 * `GET /products/{slug}` 404s for featured slugs and `featured-products?slug=`
 * returns 200-but-empty. Swift never had the bug because
 * `FigmaFeatureProductDetailsViewController(product:)` receives the object
 * from the pushing list. These tests pin the Android equivalent: the list
 * seeds [ShopProductHandoffStore]; the ViewModel consumes it and performs NO
 * network fetch.
 *
 * The repo fake throws on any call, so the featured+handoff test doubles as
 * proof the network path is skipped (and, running on a plain JVM with no Main
 * dispatcher, any stray viewModelScope.launch would fail the test too).
 */
class FeaturedProductHandoffTest {

    private object ThrowingRepo : ShopProductsRepository {
        private fun die(): Nothing = throw AssertionError("network path must not run")
        override suspend fun auctionProducts(page: Int, perPage: Int, search: String?) = die()
        override suspend fun featuredProducts(page: Int, perPage: Int, search: String?) = die()
        override suspend fun productBySlug(slug: String, featured: Boolean) = die()
    }

    @After
    fun drain() {
        // One-shot store: make sure no entry leaks between tests.
        ShopProductHandoffStore.consume("drain-any")
    }

    private fun product(slug: String) = ShopProduct(
        id = 7,
        title = "AstroAI Tire Inflator",
        slug = slug,
        priceUsd = 39.99,
    )

    @Test
    fun `featured detail uses handed-off product without any fetch`() {
        val handed = product("astroai-tire-inflator")
        ShopProductHandoffStore.put(handed)

        val vm = AuctionProductDetailsViewModel(
            slug = handed.routeSlug,
            featured = true,
            products = ThrowingRepo,
        )

        val state = vm.state.value
        assertEquals(handed, state.product)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun `consume is one-shot`() {
        ShopProductHandoffStore.put(product("slug-a"))
        assertEquals("slug-a", ShopProductHandoffStore.consume("slug-a")?.routeSlug)
        assertNull(ShopProductHandoffStore.consume("slug-a"))
    }

    @Test
    fun `stale entry for a different slug is discarded, never served`() {
        ShopProductHandoffStore.put(product("slug-a"))
        // Deep link to a different product must not receive the stale object.
        assertNull(ShopProductHandoffStore.consume("slug-b"))
        // And the stale entry is dropped, not kept around.
        assertNull(ShopProductHandoffStore.consume("slug-a"))
    }
}
