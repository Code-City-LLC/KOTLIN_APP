package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductImageUrlValidationTest {

    @Test
    fun `cart and order summary media share canonical HTTPS normalization`() {
        assertEquals(
            "https://cdn.airdropja.test/products/watch.png",
            validatedProductImageUrl(
                " https://cdn.airdropja.test/products/watch.png ",
                WEB_BASE,
            ),
        )
        assertEquals(
            "https://cdn.airdropja.test/products/watch.png",
            validatedProductImageUrl(
                "http://cdn.airdropja.test/products/watch.png",
                WEB_BASE,
            ),
        )
        assertEquals(
            "https://app.airdropja.com/storage/featured-products/apple-macbook.png",
            validatedProductImageUrl(
                "http://app.airdropja.com/storage/featured-products/apple-macbook.png",
                "https://airdropja.com",
            ),
        )
        assertEquals(
            "https://airdropja.test/storage/products/watch.png",
            validatedProductImageUrl("/storage/products/watch.png", WEB_BASE),
        )
        assertEquals(
            "https://airdropja.test/products/watch.png",
            validatedProductImageUrl("products/watch.png", WEB_BASE),
        )
        assertNull(validatedProductImageUrl(null, WEB_BASE))
        assertNull(validatedProductImageUrl("", WEB_BASE))
        assertNull(
            validatedProductImageUrl(
                "android.resource://com.ga.airdrop/drawable/image",
                WEB_BASE,
            ),
        )
        assertNull(validatedProductImageUrl("https:///missing-host.png", WEB_BASE))
        assertNull(
            validatedProductImageUrl(
                "https://user:secret@cdn.airdropja.test/watch.png",
                WEB_BASE,
            ),
        )
    }

    private companion object {
        const val WEB_BASE = "https://airdropja.test"
    }
}
