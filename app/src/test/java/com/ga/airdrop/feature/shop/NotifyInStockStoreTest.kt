package com.ga.airdrop.feature.shop

import com.ga.airdrop.feature.cart.TestSharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Notify-when-in-stock (Swift §C.7 parity): the CTA shows only for
 * out-of-stock products, subscription persists, and the poll notifies +
 * unsubscribes exactly the products that came back in stock.
 */
class NotifyInStockStoreTest {

    @Before
    fun setUp() {
        NotifyInStockStore.restoreForTests(TestSharedPreferences())
        NotifyInStockStore.clearAll()
    }

    @After
    fun tearDown() = NotifyInStockStore.clearAll()

    @Test
    fun `cta shows only when out of stock and enabled`() {
        assertTrue(showNotifyInStockCta(inventory = 0, featureEnabled = true))
        assertTrue(showNotifyInStockCta(inventory = null, featureEnabled = true))
        assertFalse(showNotifyInStockCta(inventory = 3, featureEnabled = true))
        assertFalse(showNotifyInStockCta(inventory = 0, featureEnabled = false))
    }

    @Test
    fun `subscribe persists and toggles`() {
        assertFalse(NotifyInStockStore.isSubscribed(42))
        NotifyInStockStore.subscribe(42)
        assertTrue(NotifyInStockStore.isSubscribed(42))
        assertEquals(setOf(42), NotifyInStockStore.subscribedIds())
        NotifyInStockStore.unsubscribe(42)
        assertFalse(NotifyInStockStore.isSubscribed(42))
    }

    @Test
    fun `invalid ids are ignored`() {
        NotifyInStockStore.subscribe(0)
        NotifyInStockStore.subscribe(-5)
        assertTrue(NotifyInStockStore.subscribedIds().isEmpty())
    }

    @Test
    fun `decideBackInStock only fires on known positive inventory`() {
        assertTrue(decideBackInStock(1))
        assertFalse(decideBackInStock(0))
        assertFalse(decideBackInStock(null))
    }

    @Test
    fun `poll notifies and unsubscribes only restocked products`() = runBlocking {
        NotifyInStockStore.subscribe(1) // restocked
        NotifyInStockStore.subscribe(2) // still zero
        NotifyInStockStore.subscribe(3) // absent from snapshot (unknown)
        val notified = mutableListOf<Int>()
        NotifyInStockStore.poll(
            snapshot = {
                mapOf(
                    1 to NotifyInStockStore.StockSnapshot(inventory = 4, title = "Clutch Bag"),
                    2 to NotifyInStockStore.StockSnapshot(inventory = 0, title = "Air Filter"),
                )
            },
            notify = { id, _ -> notified += id },
        )
        assertEquals(listOf(1), notified)
        assertFalse(NotifyInStockStore.isSubscribed(1)) // dropped after notify
        assertTrue(NotifyInStockStore.isSubscribed(2)) // still zero, kept
        assertTrue(NotifyInStockStore.isSubscribed(3)) // unknown, kept
    }
}
