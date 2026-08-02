package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.PackageTimelinePayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * The soft-error envelope that turned a failed read into "this package has no
 * history".
 *
 * Laravel can return HTTP 200 with `{"success":false,"message":"…","data":null}`.
 * When `data` is null or absent, `DataEnvelopeSerializer` re-decodes the WHOLE
 * envelope as the payload — and because every `PackageTimelinePayload` field has
 * a default and `ignoreUnknownKeys` is on, that fallback CANNOT fail. It yields
 * `entries = []` and a non-null `data`.
 *
 * So `.data?.entries.orEmpty()` returned `Result.success(emptyList())` for an
 * error, `timelineOutcome` became LOADED, and a customer whose package had a
 * full journey was told it had none — no error, no retry. Exactly what the
 * outcome flag was added to prevent, bypassed one layer below it.
 *
 * ⚠️ THE FIRST FOUR TESTS DO NOT GUARD THE FIX AND NEVER DID. They pin the
 * DECODER, which is the trap's mechanism — but they never call
 * [DeliveryTrackingRepository.packageTimeline], so reverting the repository
 * guard to `service.packageTimeline(id).data?.entries.orEmpty()` leaves all
 * four GREEN. Proven, not assumed: reverted the guard, forced a clean
 * recompile, 4/4 still passed.
 *
 * That is the same false-guard defect this codebase keeps producing — a test
 * that documents a bug reads exactly like a test that prevents it. They are
 * kept because the decode behaviour is genuinely surprising and worth pinning,
 * but the tests that actually hold the fix are the repository ones below,
 * which drive the real [DeliveryTrackingRepository] through a fake service.
 */
class PackageTimelineSoftErrorTest {

    private fun decode(json: String) =
        AirdropJson.decodeFromString(
            DataEnvelope.serializer(PackageTimelinePayload.serializer()),
            json,
        )

    @Test
    fun `a soft-error envelope decodes with a NON-null hollow payload — the trap`() {
        val env = decode("""{"success":false,"message":"Package not found","data":null}""")

        assertEquals(false, env.success)
        assertNotNull(
            "the serializer re-decodes the envelope as the payload, so data is " +
                "NOT null — which is why a null-check alone cannot catch this",
            env.data,
        )
        assertTrue("and it yields an EMPTY journey", env.data!!.entries.isEmpty())
        assertEquals(
            "the hollow payload carries no package id — the tell the guard uses",
            null,
            env.data!!.packageId,
        )
    }

    @Test
    fun `an absent data key behaves identically`() {
        val env = decode("""{"success":false,"message":"Unauthorized"}""")
        assertNotNull(env.data)
        assertTrue(env.data!!.entries.isEmpty())
        assertEquals(null, env.data!!.packageId)
    }

    @Test
    fun `a genuine empty journey is distinguishable — it carries its package id`() {
        val env = decode("""{"success":true,"data":{"package_id":41,"entries":[]}}""")
        assertEquals(true, env.success)
        assertEquals(
            "a real 'no history yet' response identifies the package, so the " +
                "guard must NOT reject it",
            41,
            env.data!!.packageId,
        )
        assertTrue(env.data!!.entries.isEmpty())
    }

    @Test
    fun `a response for a DIFFERENT package is detectable`() {
        val env = decode("""{"success":true,"data":{"package_id":999,"entries":[]}}""")
        assertEquals(
            "showing package 999's timeline under package 41 would be a privacy " +
                "and correctness failure; the id cross-check exists for this",
            999,
            env.data!!.packageId,
        )
    }

    // ── The tests that actually hold the fix ────────────────────────────────
    //
    // These drive the REAL DeliveryTrackingRepository through a fake service, so
    // reverting the guard turns them red. That is the difference between
    // documenting a bug and preventing it.

    private fun repo(json: String) = DeliveryTrackingRepository(
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "packageTimeline" -> decode(json)
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService,
    )

    @Test
    fun `REPO — a soft error is a FAILURE, not an empty journey`() = runBlocking {
        val result = repo("""{"success":false,"message":"Package not found","data":null}""")
            .packageTimeline(41)

        assertTrue(
            "a failed read must not surface as Result.success(emptyList()) — that " +
                "is what told a customer their package had no history. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `REPO — an absent data key is a FAILURE too`() = runBlocking {
        val result = repo("""{"success":false,"message":"Unauthorized"}""").packageTimeline(41)
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `REPO — a genuine empty journey still succeeds`() = runBlocking {
        // The guard must not be so strict it breaks the honest "no history yet"
        // case. A real response identifies its package.
        val result = repo("""{"success":true,"data":{"package_id":41,"entries":[]}}""")
            .packageTimeline(41)

        assertTrue("a real empty timeline must come through. Got: $result", result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `REPO — a real journey comes through intact and in server order`() = runBlocking {
        val result = repo(
            """
            {"success":true,"data":{"package_id":41,"entries":[
              {"label":"Drop Alerted","state":"done"},
              {"label":"Shipment Received","state":"done"},
              {"label":"Processing at Customs","state":"current"}
            ]}}
            """.trimIndent(),
        ).packageTimeline(41)

        val entries = result.getOrNull()
        assertNotNull("got: $result", entries)
        assertEquals(3, entries!!.size)
        assertEquals(
            "the client does not reorder or relabel Laravel's journey",
            listOf("Drop Alerted", "Shipment Received", "Processing at Customs"),
            entries.map { it.label },
        )
    }

    @Test
    fun `REPO — another package's timeline is REJECTED, not rendered`() = runBlocking {
        val result = repo("""{"success":true,"data":{"package_id":999,"entries":[
              {"label":"Delivered","state":"done"}
            ]}}""").packageTimeline(41)

        assertTrue(
            "showing package 999's journey under package 41 is a privacy and " +
                "correctness failure. Got: $result",
            result.isFailure,
        )
    }
}
