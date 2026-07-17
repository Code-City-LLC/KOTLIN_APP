package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductImageUrlValidationTest {

    @Test
    fun `sale product image accepts only absolute credential-free https`() {
        assertEquals(
            "https://cdn.airdropja.test/products/watch.png",
            validatedProductImageUrl(" https://cdn.airdropja.test/products/watch.png "),
        )
        assertNull(validatedProductImageUrl(null))
        assertNull(validatedProductImageUrl(""))
        assertNull(validatedProductImageUrl("http://cdn.airdropja.test/products/watch.png"))
        assertNull(validatedProductImageUrl("android.resource://com.ga.airdrop/drawable/image"))
        assertNull(validatedProductImageUrl("https:///missing-host.png"))
        assertNull(validatedProductImageUrl("https://user:secret@cdn.airdropja.test/watch.png"))
    }
}
