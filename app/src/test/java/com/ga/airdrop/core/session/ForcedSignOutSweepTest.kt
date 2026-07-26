package com.ga.airdrop.core.session

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The seam that lets a rejected bearer run the canonical local sweep.
 *
 * Kemar 2026-07-26 ruled **"Android should sweep too"** after being shown that
 * iOS runs the full sweep on the same path (`AirdropAPI.swift:769-770`) while
 * Kotlin cleared only the token — so on a shared handset the next person to sign
 * in inherited the previous customer's AI-disclosure acceptance, biometric
 * app-lock, quiet hours, calculator history and shop search history.
 *
 * ⚠️ THE DANGEROUS HALF OF THIS CHANGE IS NOT THE SWEEP, IT IS WHEN IT RUNS.
 * `clearLocalUserSession` calls the UNGUARDED `AuthTokenStore.clear()`. The 401
 * path uses the generation-guarded `clear(expected)`, which answers **false**
 * when a newer session already owns the store. Sweeping after a superseded 401
 * would therefore wipe the data of the session that REPLACED it — turning a
 * privacy fix into data loss for an innocent account.
 *
 * `AuthInterceptor` gates the sweep on that Boolean. These tests pin the seam's
 * contract; `AuthInterceptorForcedSignOutTest` pins the gating itself.
 */
class ForcedSignOutSweepTest {

    @After
    fun tearDown() {
        ForcedSignOutSweep.handler = null
    }

    @Test
    fun `run invokes the installed handler exactly once`() {
        var calls = 0
        ForcedSignOutSweep.handler = { calls++ }

        ForcedSignOutSweep.run()

        assertEquals(1, calls)
    }

    /**
     * Unit tests and any process that never finished app startup have no
     * handler. A forced sign-out there must degrade to the old behaviour —
     * token cleared, no sweep — rather than crashing the HTTP chain.
     */
    @Test
    fun `run with no handler installed is a no-op, not a crash`() {
        ForcedSignOutSweep.handler = null

        ForcedSignOutSweep.run()   // must not throw

        assertNull(ForcedSignOutSweep.handler)
    }

    @Test
    fun `the handler can be replaced, and only the current one runs`() {
        val seen = mutableListOf<String>()
        ForcedSignOutSweep.handler = { seen += "first" }
        ForcedSignOutSweep.handler = { seen += "second" }

        ForcedSignOutSweep.run()

        assertEquals(listOf("second"), seen)
    }
}
