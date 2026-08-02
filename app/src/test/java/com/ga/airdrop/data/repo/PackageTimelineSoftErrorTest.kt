package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.PackageTimelinePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
 * These tests pin the DECODE behaviour that makes the repository guard
 * necessary. The guard itself is in [DeliveryTrackingRepository.packageTimeline].
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
}
