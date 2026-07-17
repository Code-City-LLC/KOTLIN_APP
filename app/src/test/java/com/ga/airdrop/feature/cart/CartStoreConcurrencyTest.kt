package com.ga.airdrop.feature.cart

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                    CartStore.add(
                        CartStore.CartLine(
                            id = id,
                            packageId = id,
                            title = "P%04d".format(id),
                            kind = CartStore.CartLineKind.AUCTION,
                            isAuction = true,
                        ),
                    )
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
                    if (CartStore.add(
                            CartStore.CartLine(
                                id = 42,
                                packageId = 42,
                                title = "Only",
                                kind = CartStore.CartLineKind.AUCTION,
                                isAuction = true,
                            ),
                        )
                    ) {
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
        val alpha = CartStore.CartLine(
            id = 1,
            packageId = 1,
            title = "Alpha",
            priceUsd = 5.0,
            kind = CartStore.CartLineKind.PACKAGE,
        )
        val beta = CartStore.CartLine(
            id = 2,
            packageId = 2,
            title = "Beta",
            priceUsd = 7.0,
            kind = CartStore.CartLineKind.PACKAGE,
        )

        assertEquals(true, SavedForLaterStore.save(alpha))
        assertEquals(false, SavedForLaterStore.save(alpha))
        assertEquals(true, SavedForLaterStore.save(beta))

        assertEquals(0, CartStore.count)
        assertEquals(listOf(beta, alpha), SavedForLaterStore.items.value)
        assertEquals(2, SavedForLaterStore.count)

        SavedForLaterStore.remove(beta.id)

        assertEquals(listOf(alpha), SavedForLaterStore.items.value)
    }

    @Test
    fun `package and sale with equal numeric id coexist and remove independently`() {
        val pkg = CartStore.CartLine(
            id = 42,
            packageId = 42,
            title = "Package",
            kind = CartStore.CartLineKind.PACKAGE,
            statusCode = 7,
        )
        val sale = CartStore.CartLine(
            id = 42,
            packageId = 900,
            title = "Sale",
            kind = CartStore.CartLineKind.AUCTION,
            isAuction = true,
        )

        assertTrue(CartStore.add(pkg))
        assertTrue(CartStore.add(sale))
        assertEquals(setOf(pkg.key, sale.key), CartStore.items.value.map { it.key }.toSet())

        CartStore.remove(pkg.key)
        assertEquals(listOf(sale), CartStore.items.value)
    }

    @Test
    fun `two concurrent toggles of absent key cancel each other`() {
        repeat(100) {
            CartStore.clear()
            val line = CartStore.CartLine(
                id = 77,
                packageId = 77,
                title = "Toggle",
                kind = CartStore.CartLineKind.AUCTION,
                isAuction = true,
            )
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val pool = Executors.newFixedThreadPool(2)
            try {
                repeat(2) {
                    pool.execute {
                        start.await()
                        CartStore.toggle(line)
                        done.countDown()
                    }
                }
                start.countDown()
                assertTrue(done.await(5, TimeUnit.SECONDS))
            } finally {
                pool.shutdownNow()
            }
            assertFalse(CartStore.contains(line.key))
        }
    }

    @Test
    fun `suspicious empty snapshot preserves cached packages until nonempty authority subtracts`() {
        val confirmed = CartStore.CartLine(
            id = 1,
            packageId = 1,
            title = "Confirmed",
            kind = CartStore.CartLineKind.PACKAGE,
            statusCode = 7,
            serverConfirmed = true,
        )
        val legacy = CartStore.CartLine(
            id = 2,
            packageId = 2,
            title = "Legacy",
            kind = CartStore.CartLineKind.PACKAGE,
        )
        val sale = CartStore.CartLine(
            id = 3,
            packageId = 30,
            title = "Sale",
            kind = CartStore.CartLineKind.AUCTION,
            isAuction = true,
        )
        val initialSnapshot = CartStore.beginServerCartSnapshot()
        val retained = confirmed.copy(id = 4, packageId = 4, title = "Retained")
        CartStore.reconcileServerPackages(listOf(confirmed, retained, legacy), initialSnapshot)
        CartStore.add(sale)

        val snapshot = CartStore.beginServerCartSnapshot()
        CartStore.reconcileServerPackages(emptyList(), snapshot)

        assertEquals(
            setOf(confirmed.key, retained.key, legacy.key, sale.key),
            CartStore.items.value.map { it.key }.toSet(),
        )

        val nonEmptySnapshot = CartStore.beginServerCartSnapshot()
        CartStore.reconcileServerPackages(listOf(retained), nonEmptySnapshot)

        assertEquals(setOf(retained.key, legacy.key, sale.key), CartStore.items.value.map { it.key }.toSet())
    }

    @Test
    fun `delayed get cannot resurrect successful delete`() {
        val line = CartStore.CartLine(
            id = 9,
            packageId = 9,
            title = "Server package",
            kind = CartStore.CartLineKind.PACKAGE,
            statusCode = 7,
            serverConfirmed = true,
        )
        CartStore.add(line)
        val staleGet = CartStore.beginServerCartSnapshot()
        val deletion = requireNotNull(CartStore.beginPackageMutation(line, adding = false))
        assertTrue(CartStore.finishPackageMutation(deletion, succeeded = true))

        CartStore.reconcileServerPackages(listOf(line), staleGet)

        assertFalse(CartStore.contains(line.key))
    }

    @Test
    fun `paid removal invalidates delayed package add completion`() {
        val line = CartStore.CartLine(
            id = 13,
            packageId = 13,
            title = "Pending add",
            kind = CartStore.CartLineKind.PACKAGE,
            statusCode = 7,
        )
        val pendingAdd = requireNotNull(CartStore.beginPackageMutation(line, adding = true))
        CartStore.add(line)
        CartStore.synchronousCommitOverrideForTests = { true }
        try {
            assertTrue(CartStore.removePaidKeysDurably(setOf(line.key)))
            assertFalse(CartStore.finishPackageMutation(pendingAdd, succeeded = true))
            assertFalse(CartStore.contains(line.key))
        } finally {
            CartStore.synchronousCommitOverrideForTests = null
        }
    }
}
