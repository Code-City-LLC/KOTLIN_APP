package com.ga.airdrop.feature.more

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "You have 0 AirCoins" and "we could not read your AirCoins" are different
 * sentences, and the app said the first one for both.
 *
 * `GET /aircoins/status` was parsed as:
 *
 *     payload.flexInt("available") ?: payload.flexInt("balance") ?: 0
 *
 * so a payload missing BOTH fields — a shape change, a partial response, an
 * error body that still parsed as JSON — rendered a confident **0** in the
 * header. A customer with a real balance is told they have none, with no error
 * and no retry, and nothing anywhere distinguishes that from an honest zero.
 *
 * The fix makes the absent-both case an error so the caller can tell the two
 * apart. A genuine `0` still comes through as `0`, because a real zero balance
 * is a fact and must render.
 *
 * Driven through the REAL [MoreRepository] with a fake OkHttp client, so
 * removing the guard turns these red — the repository is the thing under test,
 * not a re-implementation of its parsing.
 */
class AirCoinsZeroIsNotAFactTest {

    private fun repoReturning(body: String): MoreRepository {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        return MoreRepository(client = client)
    }

    @Test
    fun `a payload carrying NEITHER field is a failure, not a confident zero`() {
        val result = runBlocking {
            repoReturning("""{"success":true,"data":{}}""").airCoinsBalance()
        }

        assertTrue(
            "neither 'available' nor 'balance' was present — reporting 0 tells a " +
                "customer with a real balance that they have none. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `an error body that still parses as JSON is a failure too`() {
        val result = runBlocking {
            repoReturning("""{"success":false,"message":"Unauthorized"}""").airCoinsBalance()
        }
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `a GENUINE zero balance still renders — it is a fact`() {
        // The guard must not turn an honest zero into an error, or it has just
        // traded one wrong answer for another.
        val result = runBlocking {
            repoReturning("""{"success":true,"data":{"available":0}}""").airCoinsBalance()
        }

        assertTrue("a real zero must come through. Got: $result", result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun `available wins, and balance is the documented fallback`() {
        val available = runBlocking {
            repoReturning("""{"success":true,"data":{"available":50,"balance":234}}""")
                .airCoinsBalance()
        }
        assertEquals(
            "'available' is the spendable figure; 'balance' is lifetime accrual",
            50,
            available.getOrNull(),
        )

        val fallback = runBlocking {
            repoReturning("""{"success":true,"data":{"balance":234}}""").airCoinsBalance()
        }
        assertEquals(234, fallback.getOrNull())
    }
}
