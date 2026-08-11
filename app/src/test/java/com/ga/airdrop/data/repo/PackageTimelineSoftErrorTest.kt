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
        // ⚠️ CHANGED MEANING, AND THE CHANGE IS THE POINT. `entries` used to
        // default to emptyList(), so the hollow payload was INDISTINGUISHABLE
        // from a genuine empty journey — that is what let a failed read render
        // as "no history". It is now nullable, so absence reads as absence.
        assertEquals(
            "the hollow payload has NO entries key at all — it no longer " +
                "masquerades as an empty journey",
            null,
            env.data!!.entries,
        )
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
        assertEquals(null, env.data!!.entries)
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
        assertTrue("a real empty journey is a PRESENT []", env.data!!.entries!!.isEmpty())
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
        // Fixture updated for the strict contract (#95189): a real Laravel
        // empty history carries has_delivery and total:0 alongside the present
        // []. The leniency being guarded is unchanged — an honest empty journey
        // must still come through.
        val result = repo(
            """{"success":true,"data":{"package_id":41,"entries":[],
               "current_key":null,"has_delivery":false,"total":0}}""",
        ).packageTimeline(41)

        assertTrue("a real empty timeline must come through. Got: $result", result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `REPO — a real journey comes through intact and in server order`() = runBlocking {
        val result = repo(
            """
            {"success":true,"data":{"package_id":41,"entries":[
              {"key":"drop_alerted","status":1,"source":"status","label":"Drop Alerted","state":"done"},
              {"key":"received","status":3,"source":"status","label":"Shipment Received","state":"done"},
              {"key":"customs","status":5,"source":"status","label":"Processing at Customs","state":"current"}
            ],"current_key":"customs","has_delivery":false,"total":3}}
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

/**
 * One unrecognised delivery status must never blank the whole Track screen.
 *
 * `toDomain()` calls `error()` on a status outside `ACTIVE_LIST_STATUSES`, and
 * that `error()` used to run inside `map()` — so it rejected the ENTIRE payload
 * rather than the one row. Track rendered "Couldn't load" over a response that
 * was almost entirely good.
 *
 * ⚠️ This ALREADY SHIPPED once. The allow-list was `{assigned,
 * out_for_delivery}`, Laravel began returning `delivered`, and a single
 * delivered row killed every customer's Track screen. The comment on
 * `toDomain` records it. Widening the list fixed that instance and left the
 * mechanism intact for the next new status — and Laravel is adding exactly
 * that: `/packages/journeys` deliberately carries pickup and warehouse-only
 * packages whose statuses are outside this set (ORC 89528 / 89569).
 *
 * A dropped row is a visible gap in one card. `error()` is a blank screen for
 * everyone.
 */
class TrackUnknownStatusDoesNotBlankListTest {

    private fun page(vararg statuses: String): String {
        val rows = statuses.mapIndexed { i, s ->
            """{"package_id":${41 + i},"tracking_code":"AD-${41 + i}","status":"$s",
               "current_stage_key":"$s","updated_at":"2026-08-02T10:00:00+00:00"}"""
        }
        return pageWithRows(*rows.toTypedArray())
    }

    private fun pageWithRows(vararg rows: String): String {
        return """{"success":true,"data":{"deliveries":[${rows.joinToString(",")}],
            "meta":{"current_page":1,"per_page":50,"total":${rows.size},"last_page":1}}}"""
    }

    private fun repo(json: String) = DeliveryTrackingRepository(
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "activeDeliveries" -> AirdropJson.decodeFromString(
                    DataEnvelope.serializer(
                        com.ga.airdrop.data.model.ActiveDeliveriesPayload.serializer(),
                    ),
                    json,
                )
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService,
    )

    @Test
    fun `one unknown status drops ONLY that row, the rest of Track survives`() = runBlocking {
        val result = repo(page("assigned", "ready_for_pickup", "out_for_delivery"))
            .activeDeliveries(page = 1, perPage = 50)

        assertTrue(
            "an unrecognised status must not reject the whole payload — that is a " +
                "blank Track screen for every customer. Got: $result",
            result.isSuccess,
        )
        val kept = result.getOrNull()!!.deliveries
        assertEquals("the two known rows must survive", 2, kept.size)
        assertEquals(listOf("assigned", "out_for_delivery"), kept.map { it.status })
    }

    @Test
    fun `a page of ONLY unknown statuses is an empty list, not a failure`() = runBlocking {
        // Forward-compatibility: when journeys lands, an all-pickup page must
        // render as "nothing to track here" rather than an error screen.
        val result = repo(page("ready_for_pickup", "at_warehouse"))
            .activeDeliveries(page = 1, perPage = 50)

        assertTrue("got: $result", result.isSuccess)
        assertEquals(0, result.getOrNull()!!.deliveries.size)
    }

    @Test
    fun `a genuinely BROKEN payload still fails — this is not blanket tolerance`() = runBlocking {
        // A soft-error envelope is corruption, not an unknown status. It must
        // still be rejected, or this fix would trade one silent lie for another.
        val result = repo("""{"success":false,"message":"Unauthorized","data":null}""")
            .activeDeliveries(page = 1, perPage = 50)

        assertTrue("a failed read must NOT become an empty list. Got: $result", result.isFailure)
    }

    @Test
    fun `a missing package id is corruption and fails the page visibly`() = runBlocking {
        val result = repo(
            pageWithRows(
                """{"package_id":41,"tracking_code":"AD-41","status":"assigned"}""",
                """{"tracking_code":"AD-MISSING","status":"out_for_delivery"}""",
            ),
        ).activeDeliveries(page = 1, perPage = 50)

        assertTrue(
            "a required identity must not disappear through mapNotNull into an " +
                "apparently successful partial/empty list. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `an invalid package id fails even when its status is forward-compatible`() = runBlocking {
        val result = repo(
            pageWithRows(
                """{"package_id":0,"tracking_code":"AD-ZERO","status":"ready_for_pickup"}""",
            ),
        ).activeDeliveries(page = 1, perPage = 50)

        assertTrue(
            "required identity validation must run before unknown-status tolerance. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `a blank status is malformed rather than an unknown future status`() = runBlocking {
        val result = repo(
            pageWithRows(
                """{"package_id":41,"tracking_code":"AD-41","status":"   "}""",
            ),
        ).activeDeliveries(page = 1, perPage = 50)

        assertTrue("blank required status must fail visibly. Got: $result", result.isFailure)
    }

    @Test
    fun `all-known statuses are completely unaffected`() = runBlocking {
        val result = repo(page("assigned", "out_for_delivery", "delivered"))
            .activeDeliveries(page = 1, perPage = 50)
        assertTrue("got: $result", result.isSuccess)
        assertEquals(3, result.getOrNull()!!.deliveries.size)
    }
}
