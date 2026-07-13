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
 * `update()` used to run `_header.value = transform(_header.value)` — a
 * non-atomic read-modify-write, so concurrent header writers (cart-count,
 * profile, AirCoins refresh) lost each other's changes. With 1000 racing
 * increments the pre-fix code lands well under 1000; the atomic
 * MutableStateFlow.update CAS loop lands exactly on 1000.
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

    @Test
    fun `replacement session clears once without erasing newer session writes`() {
        val accountA = AuthenticatedSessionOwner("account-a")
        val accountB = AuthenticatedSessionOwner("account-b")
        SessionStore.onAuthenticatedSessionChanged(accountA.sessionId)
        SessionStore.updateForSession(accountA) { it.copy(firstName = "Account A") }

        SessionStore.onAuthenticatedSessionChanged(accountB.sessionId)
        SessionStore.updateForSession(accountB) { it.copy(firstName = "Account B") }
        assertEquals("Account B", SessionStore.header.value.firstName)

        SessionStore.updateForSession(accountB) { it.copy(airCoins = "22") }
        assertEquals(
            SessionStore.HeaderInfo(firstName = "Account B", airCoins = "22"),
            SessionStore.header.value,
        )

        assertEquals(false, SessionStore.updateForSession(accountA) { it.copy(firstName = "late A") })
        assertEquals("Account B", SessionStore.header.value.firstName)
    }

    @Test
    fun `owner change classifier separates identity enrichment from replacement`() {
        val unbound = AuthenticatedSessionOwner("session-a", accountId = null)
        val bound = AuthenticatedSessionOwner("session-a", accountId = 101)
        val replacement = AuthenticatedSessionOwner("session-b", accountId = 202)

        assertEquals(AuthenticatedOwnerChange.Unchanged, unbound.changeTo(unbound))
        assertEquals(AuthenticatedOwnerChange.IdentityUpdated, unbound.changeTo(bound))
        assertEquals(AuthenticatedOwnerChange.SessionReplaced, bound.changeTo(replacement))
        assertEquals(AuthenticatedOwnerChange.SessionReplaced, replacement.changeTo(null))
    }
}
