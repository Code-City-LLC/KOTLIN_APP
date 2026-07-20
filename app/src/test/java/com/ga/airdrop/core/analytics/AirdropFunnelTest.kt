package com.ga.airdrop.core.analytics

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure buffer/batch/bridge contract of the checkout funnel (Swift 89fbb11):
 * flush at 5 buffered events, uploads in batches of ≤20 removed before the
 * request, failures dropped silently, scalar-only property bridging with
 * Boolean checked before Number.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AirdropFunnelTest {

    @After
    fun tearDown() {
        AirdropFunnel.resetForTest()
    }

    @Test
    fun logIsANoOpUntilInstalled() = runTest {
        AirdropFunnel.resetForTest(armed = false)
        AirdropFunnel.log("checkout_cart_continue")
        assertEquals(0, AirdropFunnel.pendingCount())
    }

    @Test
    fun bufferFlushesAtFiveEvents() = runTest {
        AirdropFunnel.resetForTest(armed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        AirdropFunnel.flushScope = CoroutineScope(dispatcher)
        val uploads = mutableListOf<Int>()
        AirdropFunnel.uploader = { uploads += it.size }

        repeat(4) { AirdropFunnel.log("evt_$it") }
        testScheduler.advanceUntilIdle()
        assertTrue("below threshold nothing uploads", uploads.isEmpty())
        assertEquals(4, AirdropFunnel.pendingCount())

        AirdropFunnel.log("evt_4")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(5), uploads)
        assertEquals(0, AirdropFunnel.pendingCount())
    }

    @Test
    fun batchesCapAtTwentyAndDrainRemovesBeforeUpload() = runTest {
        AirdropFunnel.resetForTest(armed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        AirdropFunnel.flushScope = CoroutineScope(dispatcher)
        val uploads = mutableListOf<Int>()
        val pendingAtUploadTime = mutableListOf<Int>()
        AirdropFunnel.uploader = { batch ->
            uploads += batch.size
            pendingAtUploadTime += AirdropFunnel.pendingCount()
        }

        // Queued dispatcher: the auto-flush launches don't run until we
        // advance, so 23 events accumulate first.
        repeat(23) { AirdropFunnel.log("evt", mapOf("i" to it)) }
        assertEquals(23, AirdropFunnel.pendingCount())
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(20, 3), uploads)
        // Each batch was removed from the buffer BEFORE its upload ran.
        assertEquals(listOf(3, 0), pendingAtUploadTime)
        assertEquals(0, AirdropFunnel.pendingCount())
    }

    @Test
    fun uploadFailuresAreDroppedSilently() = runTest {
        AirdropFunnel.resetForTest(armed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        AirdropFunnel.flushScope = CoroutineScope(dispatcher)
        var attempts = 0
        AirdropFunnel.uploader = { attempts += 1; throw IOException("offline") }

        repeat(5) { AirdropFunnel.log("evt") }
        testScheduler.advanceUntilIdle()

        assertEquals(1, attempts)
        // Failed batch is NOT re-queued (no retry, no disk).
        assertEquals(0, AirdropFunnel.pendingCount())
    }

    @Test
    fun bridgeKeepsScalarsBooleanFirstAndDropsTheRest() {
        val bridged = bridgeFunnelProperties(
            mapOf(
                "flag" to true,
                "name" to "delivery",
                "count" to 3,
                "big" to 9_000_000_000L,
                "price" to 12.5,
                "ratio" to 1.5f, // other Number → widened to Double
                "nothing" to null, // dropped
                "list" to listOf(1, 2), // non-scalar dropped
                "map" to mapOf("a" to 1), // non-scalar dropped
            ),
        )!!

        // Boolean stays Boolean (checked before any Number branch).
        assertTrue(bridged["flag"] is Boolean)
        assertEquals(true, bridged["flag"])
        assertEquals("delivery", bridged["name"])
        assertEquals(3, bridged["count"])
        assertEquals(9_000_000_000L, bridged["big"])
        assertEquals(12.5, bridged["price"])
        assertEquals(1.5, bridged["ratio"])
        assertEquals(
            setOf("flag", "name", "count", "big", "price", "ratio"),
            bridged.keys,
        )
    }

    @Test
    fun bridgeReturnsNullWhenNothingSurvives() {
        assertNull(bridgeFunnelProperties(null))
        assertNull(bridgeFunnelProperties(emptyMap()))
        assertNull(bridgeFunnelProperties(mapOf("only" to listOf("non-scalar"), "gone" to null)))
    }
}
