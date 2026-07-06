package com.ga.airdrop.feature.more2

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG_AUDIT C1 regression guard for AccountDeletionFlow.
 *
 * The pre-fix bridge held two unsynchronized vars written one at a time, so a
 * concurrent reader could see one user's email paired with another user's
 * password. The fix stores a single immutable @Volatile snapshot swapped
 * atomically by set(); snapshot() is a one-read atomic pair.
 */
class AccountDeletionFlowTest {

    @After
    fun tearDown() {
        AccountDeletionFlow.clear()
    }

    @Test
    fun `set stores a paired snapshot and clear nulls it`() {
        AccountDeletionFlow.set("a@x.com", "secretA")
        assertEquals("a@x.com", AccountDeletionFlow.email)
        assertEquals("secretA", AccountDeletionFlow.password)
        assertTrue(AccountDeletionFlow.hasVerifiedCredentials())
        val snap = AccountDeletionFlow.snapshot()
        assertEquals("a@x.com", snap?.email)
        assertEquals("secretA", snap?.password)

        AccountDeletionFlow.clear()
        assertNull(AccountDeletionFlow.snapshot())
        assertFalse(AccountDeletionFlow.hasVerifiedCredentials())
    }

    /**
     * A faithful model of the PRE-FIX storage (two independent vars). A forced
     * interleave — read email, let a writer flip both fields, read password —
     * deterministically yields a torn pair. This is the exact hazard the fix
     * removes.
     */
    @Test
    fun `two-var storage tears under a forced interleave (models pre-fix)`() {
        class TwoVar {
            @Volatile var email = ""
            @Volatile var password = ""
        }
        val tv = TwoVar().apply { email = "a@x.com"; password = "secretA" }
        var readEmail = ""
        var readPassword = ""
        val emailRead = CountDownLatch(1)
        val writerDone = CountDownLatch(1)

        val reader = Thread {
            readEmail = tv.email          // "a@x.com"
            emailRead.countDown()
            writerDone.await()            // writer flips both fields here
            readPassword = tv.password    // "secretB" -> torn against "a@x.com"
        }
        val writer = Thread {
            emailRead.await()
            tv.email = "b@x.com"
            tv.password = "secretB"
            writerDone.countDown()
        }
        reader.start(); writer.start(); reader.join(); writer.join()

        // The pre-fix object could hand deactivate user A's email with user B's
        // password. (Assertion documents the tear, deterministically.)
        assertEquals("a@x.com", readEmail)
        assertEquals("secretB", readPassword)
    }

    @Test
    fun `snapshot captured before a flip stays a consistent pair (fix)`() {
        AccountDeletionFlow.set("a@x.com", "secretA")
        val snap = AccountDeletionFlow.snapshot()
        AccountDeletionFlow.set("b@x.com", "secretB") // concurrent-style flip
        // The captured snapshot is immutable and internally consistent.
        assertEquals("a@x.com", snap?.email)
        assertEquals("secretA", snap?.password)
    }

    @Test
    fun `concurrent writers never yield a torn snapshot`() {
        val pool = Executors.newFixedThreadPool(3)
        val stop = AtomicBoolean(false)
        val violations = AtomicInteger(0)
        try {
            val writerA = pool.submit { while (!stop.get()) AccountDeletionFlow.set("a@x.com", "secretA") }
            val writerB = pool.submit { while (!stop.get()) AccountDeletionFlow.set("b@x.com", "secretB") }
            val reader = pool.submit {
                repeat(2_000_000) {
                    val s = AccountDeletionFlow.snapshot() ?: return@repeat
                    val expected = if (s.email == "a@x.com") "secretA" else "secretB"
                    if (s.password != expected) violations.incrementAndGet()
                }
            }
            reader.get(30, TimeUnit.SECONDS)
            stop.set(true)
            writerA.get(5, TimeUnit.SECONDS)
            writerB.get(5, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
        assertEquals(0, violations.get())
    }
}
