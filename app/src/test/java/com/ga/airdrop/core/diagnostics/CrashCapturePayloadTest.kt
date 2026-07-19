package com.ga.airdrop.core.diagnostics

import java.util.Date
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the crash payload to Laravel's DiagnosticsController validator
 * (field names shared with Swift CrashCapture; `ios_version` is the
 * server's OS slot — Android reports "Android <release>" there).
 */
class CrashCapturePayloadTest {

    @Test
    fun `payload carries every validator field with the exact names`() {
        val boom = IllegalStateException("cart drained mid-checkout")
        val payload = buildCrashPayload(
            throwable = boom,
            threadName = "main",
            occurredAtIso = "2026-07-19T12:00:00Z",
            appVersion = "9.1",
            buildNumber = "42",
            osVersion = "Android 14",
            deviceModel = "Pixel 7",
            deviceName = "Google Pixel 7",
            bundleId = "com.ga.airdrop.app.staging",
        )
        assertEquals("uncaught_exception", payload["type"]!!.jsonPrimitive.content)
        assertEquals(
            "java.lang.IllegalStateException",
            payload["exception_name"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "cart drained mid-checkout",
            payload["exception_reason"]!!.jsonPrimitive.content,
        )
        assertTrue(payload["call_stack_symbols"]!!.jsonArray.isNotEmpty())
        assertTrue(
            payload["call_stack_symbols"]!!.jsonArray.first().jsonPrimitive.content
                .contains("IllegalStateException"),
        )
        assertEquals("thread:main", payload["user_info"]!!.jsonPrimitive.content)
        assertEquals("2026-07-19T12:00:00Z", payload["occurred_at"]!!.jsonPrimitive.content)
        assertEquals("9.1", payload["app_version"]!!.jsonPrimitive.content)
        assertEquals("42", payload["build_number"]!!.jsonPrimitive.content)
        assertEquals("Android 14", payload["ios_version"]!!.jsonPrimitive.content)
        assertEquals("Pixel 7", payload["device_model"]!!.jsonPrimitive.content)
        assertEquals("com.ga.airdrop.app.staging", payload["bundle_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `oversized stacks and reasons are truncated, not rejected`() {
        val deep = RuntimeException("x".repeat(10_000))
        val payload = buildCrashPayload(
            throwable = deep,
            threadName = "worker",
            occurredAtIso = "2026-07-19T12:00:00Z",
            appVersion = "9.1",
            buildNumber = "42",
            osVersion = "Android 14",
            deviceModel = "M".repeat(100),
            deviceName = "N".repeat(100),
            bundleId = "b",
        )
        assertTrue(
            payload["exception_reason"]!!.jsonPrimitive.content.length <=
                CrashCapture.MAX_REASON_CHARS,
        )
        assertTrue(
            payload["call_stack_symbols"]!!.jsonArray.size <= CrashCapture.MAX_STACK_LINES,
        )
        assertEquals(64, payload["device_model"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun `timestamps are UTC iso8601 with filename-safe rendering`() {
        val iso = CrashCapture.iso8601Utc(Date(0))
        assertEquals("1970-01-01T00:00:00Z", iso)
    }
}
