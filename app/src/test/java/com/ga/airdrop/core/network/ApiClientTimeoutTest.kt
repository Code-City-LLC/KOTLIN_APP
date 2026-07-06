package com.ga.airdrop.core.network

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BUG_AUDIT H8 regression guard.
 *
 * ApiClient.okHttp set connect/read/write timeouts but no callTimeout, so a
 * trickling or half-dead server could hang a call indefinitely (callTimeout 0
 * = no overall cap). This pins an overall call timeout on the shared client,
 * which every Retrofit-backed call inherits.
 */
class ApiClientTimeoutTest {

    @Test
    fun `shared client caps overall call duration`() {
        assertEquals(
            TimeUnit.SECONDS.toMillis(120).toInt(),
            ApiClient.okHttp.callTimeoutMillis,
        )
    }
}
