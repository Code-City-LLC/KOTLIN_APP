package com.ga.airdrop.feature.shop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A product with no readable price must never be sold for nothing.
 *
 * `AuctionProduct.displayPriceUsd` ended `?: 0.0`, and that zero did not stay on
 * screen. It flows:
 *
 *     displayPriceUsd -> ShopProduct.priceUsd (ShopRepoBinding.kt)
 *                     -> cart line priceUsd
 *                     -> CartStore.totalUsd()
 *
 * So an unparseable price became a **free line item in a real order total**,
 * silently, with nothing anywhere marking it as unknown.
 *
 * A genuine 0.00 from the server is still a fact and still renders. What is gone
 * is inventing one.
 */
class PricelessProductNotFreeTest {

    private fun product(price: Double, known: Boolean) =
        ShopProduct(id = 1, title = "x", priceUsd = price, priceKnown = known)

    @Test
    fun `a product whose price could not be parsed is NOT purchasable`() {
        val p = product(price = 0.0, known = false)
        assertFalse(
            "an unknown price must not be sellable — it would be charged as free",
            p.isAvailableForPurchase,
        )
    }

    @Test
    fun `a real zero price is also not purchasable, but for an honest reason`() {
        // We do not sell 0.00 items either, and this keeps the two cases from
        // being conflated: known=true says the server really said zero.
        val p = product(price = 0.0, known = true)
        assertFalse(p.isAvailableForPurchase)
        assertTrue("the distinction must survive", p.priceKnown)
    }

    @Test
    fun `a normal priced product is purchasable`() {
        assertTrue(product(price = 49.99, known = true).isAvailableForPurchase)
    }

    @Test
    fun `priceKnown defaults to true so existing construction sites are unchanged`() {
        // The flag is additive. Anything already building a ShopProduct with a
        // real price keeps working without edits.
        assertTrue(ShopProduct(id = 2, title = "y", priceUsd = 10.0).isAvailableForPurchase)
    }
}
