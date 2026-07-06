package com.ga.airdrop.core.session

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * BUG_AUDIT H30 regression guard for SessionStore.
 *
 * `update()` used to run `_header.value = transform(_header.value)`, a
 * non-atomic read-modify-write, so concurrent header writers could lose each
 * other's changes.
 */
class SessionStoreConcurrencyTest {

    @Before
    fun reset() {
        SessionStore.clear()
    }

    @Test
    fun `concurrent increments never lose an update`() {
        val n = 1000
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val done = CountDownLatch(n)
        try {
            repeat(n) {
                pool.execute {
                    start.await()
                    SessionStore.update { it.copy(cartCount = it.cartCount + 1) }
                    done.countDown()
                }
            }
            start.countDown()
            assertEquals(true, done.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(n, SessionStore.header.value.cartCount)
    }
}
