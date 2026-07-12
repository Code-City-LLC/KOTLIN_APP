package com.ga.airdrop.feature.cart

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PR44 gate proof (#21974): CartLine.weightKg must survive the SharedPreferences
 * JSON round-trip (app restart), and carts persisted BEFORE the field existed
 * must still decode — legacy lines simply carry a null weight, which the
 * delivery endpoints treat as optional (same behavior as pre-PR44).
 */
class CartLineWeightPersistenceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `weightKg survives the persistence round-trip`() {
        val line = CartStore.CartLine(
            id = 42, packageId = 42, title = "Test package",
            qty = 1, priceUsd = 33.5, isAuction = false, weightKg = 1.36,
        )
        val restored = json.decodeFromString<List<CartStore.CartLine>>(
            json.encodeToString(listOf(line)),
        )
        assertEquals(1.36, restored.single().weightKg!!, 1e-9)
    }

    @Test
    fun `legacy persisted carts without weightKg still decode with null weight`() {
        // Exact shape CartStore persisted before the field existed.
        val legacy = """[{"id":7,"packageId":7,"title":"Old line","qty":1,""" +
            """"priceUsd":12.0,"isAuction":false}]"""
        val restored = json.decodeFromString<List<CartStore.CartLine>>(legacy)
        assertEquals(1, restored.size)
        assertNull(restored.single().weightKg)
    }

}
