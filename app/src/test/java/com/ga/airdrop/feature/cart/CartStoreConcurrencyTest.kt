package com.ga.airdrop.feature.cart

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * BUG_AUDIT H30 regression guard for CartStore.
 *
 * `mutate()` used to run `_items.value = sorted(transform(_items.value))` — a
 * non-atomic read-modify-write. Under concurrency two mutations could read the
 * same base list and one would clobber the other, silently dropping cart lines
 * (or double-adding the same id). The atomic MutableStateFlow.update CAS loop
 * fixes it.
 *
 * `init()` is never called, so `persist()` is a no-op (prefs == null); this is
 * a pure-JVM test. A `CountDownLatch` releases all workers at once to maximise
 * the interleaving that exposed the old race.
 */
class CartStoreConcurrencyTest {

    @Before
    fun reset() {
        CartStore.clear()
        SavedForLaterStore.clearAll()
    }

    @Test
    fun `concurrent adds of distinct ids never lose a line`() {
        val n = 500
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val done = CountDownLatch(n)
        try {
            for (id in 1..n) {
                pool.execute {
                    start.await()
                    CartStore.add(CartStore.CartLine(id = id, title = "P%04d".format(id)))
                    done.countDown()
                }
            }
            start.countDown()
            assertEquals(true, done.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(n, CartStore.count)
    }

    @Test
    fun `racing adds of one id stay idempotent`() {
        val racers = 200
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val done = CountDownLatch(racers)
        val addedTrue = AtomicInteger(0)
        try {
            repeat(racers) {
                pool.execute {
                    start.await()
                    if (CartStore.add(CartStore.CartLine(id = 42, title = "Only"))) {
                        addedTrue.incrementAndGet()
                    }
                    done.countDown()
                }
            }
            start.countDown()
            assertEquals(true, done.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        // Exactly one line, and exactly one racer observed the successful add.
        assertEquals(1, CartStore.count)
        assertEquals(1, addedTrue.get())
    }

    @Test
    fun `saved for later is separate newest-first and idempotent`() {
        val alpha = CartStore.CartLine(id = 1, title = "Alpha", priceUsd = 5.0)
        val beta = CartStore.CartLine(id = 2, title = "Beta", priceUsd = 7.0)

        assertEquals(true, SavedForLaterStore.save(alpha))
        assertEquals(false, SavedForLaterStore.save(alpha))
        assertEquals(true, SavedForLaterStore.save(beta))

        assertEquals(0, CartStore.count)
        assertEquals(listOf(beta, alpha), SavedForLaterStore.items.value)
        assertEquals(2, SavedForLaterStore.count)

        SavedForLaterStore.remove(beta.id)

        assertEquals(listOf(alpha), SavedForLaterStore.items.value)
    }
}
