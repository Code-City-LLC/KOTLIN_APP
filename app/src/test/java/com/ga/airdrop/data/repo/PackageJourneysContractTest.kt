package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.PackageJourneysPayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * The Track root that finally shows a package held for collection.
 *
 * `/deliveries/active` reads the `package_deliveries` table. A pickup package
 * has no row there, so it could not appear no matter what the client did — a
 * customer whose package was sitting ready for collection opened Track and was
 * told "No shipments to track". `/packages/journeys` is the superset.
 *
 * Every test here drives the REAL [DeliveryTrackingRepository] through a fake
 * service, so removing a guard turns one of them red. That is the distinction
 * this codebase keeps having to relearn: a test that decodes a payload and
 * asserts on the DTO documents the trap; only a test that calls the thing under
 * test prevents it.
 *
 * Revert-checked, one guard at a time — see the header on each block for which
 * line it holds.
 */
class PackageJourneysContractTest {

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun stage(
        key: String = "received",
        label: String = "Received at Warehouse",
        state: String = "done",
        status: Int? = 3,
    ) = """{"key":"$key","label":"$label","icon":"box","status":$status,
           "state":"$state","at":"Aug 1"}"""

    private fun row(
        id: Int = 41,
        status: Int? = 7,
        statusName: String? = "Ready for Pickup",
        fulfilment: String? = "pickup",
        stages: String = stage(),
    ) = """{"id":$id,"ard_number":"AIR-$id","description":"Sneakers",
           "value_usd":"120.00","weight_lbs":"3.4","shipper":"DHL",
           "status":$status,${statusName?.let { """"status_name":"$it",""" } ?: ""}
           "status_icon":"box","fulfilment":${fulfilment?.let { "\"$it\"" } ?: "null"},
           "current_stage":"ready_pickup","stages":[$stages]}"""

    private fun body(
        rows: String,
        page: Int = 1,
        lastPage: Int = 1,
        total: Int = 1,
    ) = """{"success":true,"data":[$rows],
           "meta":{"current_page":$page,"per_page":50,"total":$total,"last_page":$lastPage}}"""

    private fun repo(json: String) = DeliveryTrackingRepository(
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "packageJourneys" -> AirdropJson.decodeFromString(
                    PackageJourneysPayload.serializer(),
                    json,
                )
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService,
    )

    private fun load(json: String, page: Int = 1) = runBlocking {
        repo(json).packageJourneys(page = page, perPage = 50)
    }

    // ── The gap this endpoint closes ────────────────────────────────────────

    @Test
    fun `a PICKUP package appears on Track — the whole point of the change`() {
        val result = load(body(row(fulfilment = "pickup")))

        val page = result.getOrNull()
        assertNotNull("a pickup journey must load. Got: $result", page)
        val journey = page!!.journeys.single()
        assertEquals(
            "a package held for collection is invisible on /deliveries/active; " +
                "if this row does not survive, the customer still sees nothing",
            JourneyFulfilment.Pickup,
            journey.fulfilment,
        )
        assertEquals(41, journey.packageId)
        assertEquals("Ready for Pickup", journey.statusName)
        assertEquals("the server-composed rail comes through", 1, journey.stages.size)
    }

    @Test
    fun `pickup and delivery rows load side by side, in server order`() {
        val result = load(
            body(
                """${row(id = 44, fulfilment = "pickup")},
                   ${row(id = 22, fulfilment = "delivery", statusName = "Out for Delivery")}""",
                total = 2,
            ),
        )

        val journeys = result.getOrNull()?.journeys
        assertNotNull("got: $result", journeys)
        assertEquals(
            "the client does not reorder Track",
            listOf(44, 22),
            journeys!!.map { it.packageId },
        )
        assertEquals(
            listOf(JourneyFulfilment.Pickup, JourneyFulfilment.Delivery),
            journeys.map { it.fulfilment },
        )
    }

    @Test
    fun `an explicit empty list is the honest empty state, not a failure`() {
        // "You have nothing to track" is a fact and must render. The guard must
        // not be so strict it turns an honest empty page into an error screen.
        val result = load("""{"success":true,"data":[],"meta":{"current_page":1,"per_page":50,"total":0,"last_page":1}}""")

        assertTrue("an explicit [] must come through. Got: $result", result.isSuccess)
        assertEquals(0, result.getOrNull()!!.journeys.size)
    }

    // ── Holds: `payload.data ?: error(...)` ─────────────────────────────────

    @Test
    fun `an ABSENT data key is a FAILURE, never an empty Track screen`() {
        // The defect class this codebase keeps producing: a failed read that
        // renders as "you have nothing". A customer with five packages must not
        // be told they have none, silently, with no error and no retry.
        val result = load("""{"success":false,"message":"Unauthorized"}""")

        assertTrue(
            "a missing data key must NOT become an empty list. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `a soft-error envelope with a null data key is a FAILURE too`() {
        val result = load("""{"success":false,"message":"Server error","data":null}""")
        assertTrue("got: $result", result.isFailure)
    }

    /**
     * ⚠️ THE TWO TESTS ABOVE DO NOT HOLD THE `data` GUARD, AND I ONLY KNOW THAT
     * BECAUSE I REVERT-CHECKED IT.
     *
     * Both carry `success:false`, so the success guard rejects them one line
     * earlier. Replacing `payload.data ?: error(...)` with `.orEmpty()` left
     * both GREEN — they assert the right outcome, but a different guard is
     * producing it. That is the false-guard shape this codebase keeps growing:
     * the test reads like protection and is documentation.
     *
     * These two are the real thing. `success` is true (or absent, as Laravel's
     * 200-with-no-envelope shape can be) and `data` is simply not there, so
     * nothing upstream can catch it. Reverting the guard turns them red.
     */
    @Test
    fun `a SUCCESSFUL envelope with no data key is still a failure`() {
        val result = load(
            """{"success":true,"meta":{"current_page":1,"per_page":50,"total":3,"last_page":1}}""",
        )

        assertTrue(
            "success:true with no rows key is a shape change, not an empty " +
                "Track. `meta.total` even says there are 3 packages. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `a null data key with no success flag is a failure, not an empty Track`() {
        val result = load(
            """{"data":null,"meta":{"current_page":1,"per_page":50,"total":0,"last_page":1}}""",
        )
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `success false is rejected even when rows are present`() {
        val result = load(
            """{"success":false,"message":"Stale","data":[${row()}],
               "meta":{"current_page":1,"per_page":50,"total":1,"last_page":1}}""",
        )
        assertTrue("got: $result", result.isFailure)
    }

    // ── Holds: `payload.meta ?: error(...)` and the page cross-check ────────

    @Test
    fun `a missing meta is a failure — pagination is not optional here`() {
        val result = load("""{"success":true,"data":[${row()}]}""")
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `a page echo that disagrees with the request is REJECTED`() {
        // Rendering page 2's packages as page 1 shows the customer the wrong
        // shipments, so the echo must match rather than be defaulted away.
        val result = load(body(row(), page = 2, lastPage = 2), page = 1)
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `hasNextPage follows the server last_page`() {
        val one = load(body(row(), page = 1, lastPage = 2), page = 1)
        assertTrue("got: $one", one.getOrNull()!!.hasNextPage)

        val last = load(body(row(), page = 1, lastPage = 1), page = 1)
        assertTrue("got: $last", !last.getOrNull()!!.hasNextPage)
    }

    // ── Holds: the duplicate-id check ───────────────────────────────────────

    @Test
    fun `duplicate package ids are rejected, not silently deduped`() {
        val result = load(body("""${row(id = 41)},${row(id = 41)}""", total = 2))
        assertTrue(
            "the list needs a stable identity per row; a repeated id means the " +
                "page itself is wrong. Got: $result",
            result.isFailure,
        )
    }

    // ── Holds: PackageJourneySummary.toDomain() required fields ─────────────

    @Test
    fun `a row with no id is corruption and fails visibly`() {
        val result = load(body("""{"status":7,"fulfilment":"pickup","stages":[${stage()}]}"""))
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `a MISSING status is a failure — it is not status zero`() {
        val result = load(body(row(status = null)))
        assertTrue(
            "defaulting an absent status to 0 renders some other status's " +
                "identity. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `an UNRECOGNISED status is accepted — statuses are never allow-listed`() {
        // The lesson from /deliveries/active, kept: Laravel adds statuses
        // constantly, and a client allow-list means a customer silently stops
        // seeing their package the day the backend ships a new one.
        val result = load(body(row(status = 9999, statusName = "Awaiting Broker Review")))

        assertTrue("an unknown status must load. Got: $result", result.isSuccess)
        val journey = result.getOrNull()!!.journeys.single()
        assertEquals(9999, journey.status)
        assertEquals("Awaiting Broker Review", journey.statusName)
    }

    @Test
    fun `a blank status name falls back to the server's own wording`() {
        val result = load(body(row(statusName = "   ")))
        assertEquals(
            "a blank name must not render an empty status pill",
            "Unknown Status",
            result.getOrNull()!!.journeys.single().statusName,
        )
    }

    @Test
    fun `an absent status name falls back the same way`() {
        val result = load(body(row(statusName = null)))
        assertEquals("Unknown Status", result.getOrNull()!!.journeys.single().statusName)
    }

    @Test
    fun `a missing fulfilment is REJECTED rather than assumed to be delivery`() {
        val result = load(body(row(fulfilment = null)))
        assertTrue(
            "guessing 'delivery' would route a package we are holding for " +
                "collection through the delivery stages and tell the customer " +
                "it is on a van. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `an unknown fulfilment literal is rejected`() {
        val result = load(body(row(fulfilment = "courier")))
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `fulfilment matching is case and whitespace tolerant`() {
        val result = load(body(row(fulfilment = " Pickup ")))
        assertEquals(
            JourneyFulfilment.Pickup,
            result.getOrNull()!!.journeys.single().fulfilment,
        )
    }

    // ── Holds: the non-empty stages rail ────────────────────────────────────

    @Test
    fun `an EMPTY stages rail is a schema regression, not a quiet row`() {
        val result = load(body("""{"id":41,"status":7,"fulfilment":"pickup","stages":[]}"""))
        assertTrue(
            "railFor() always emits a progressive rail, so [] means the schema " +
                "changed under us. Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `an ABSENT stages field fails the same way`() {
        val result = load(body("""{"id":41,"status":7,"fulfilment":"pickup"}"""))
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `duplicate stage keys within a rail are rejected`() {
        val result = load(
            body(row(stages = """${stage(key = "received")},${stage(key = "received")}""")),
        )
        assertTrue("got: $result", result.isFailure)
    }

    // ── Holds: JourneyStageDto.toDomain() ──────────────────────────────────

    @Test
    fun `a stage with a blank key or label is rejected`() {
        assertTrue(load(body(row(stages = stage(key = "  ")))).isFailure)
        assertTrue(load(body(row(stages = stage(label = "  ")))).isFailure)
    }

    @Test
    fun `an UNKNOWN stage state is rejected, NOT coerced to pending`() {
        // The sibling DeliveryTrackingStage degrades an odd state to "pending",
        // because there the rail's shape is already known. Here the rail IS the
        // screen: calling an unrecognised state "pending" would show a
        // completed pickup as not-yet-happened.
        val result = load(body(row(stages = stage(state = "collected"))))
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `the three server states all come through unchanged`() {
        val result = load(
            body(
                row(
                    stages = """${stage(key = "a", state = "done")},
                                ${stage(key = "b", state = "current")},
                                ${stage(key = "c", state = "pending")}""",
                ),
            ),
        )
        assertEquals(
            listOf("done", "current", "pending"),
            result.getOrNull()!!.journeys.single().stages.map { it.state },
        )
    }

    @Test
    fun `a last-mile stage carries a null numeric status without failing`() {
        val result = load(body(row(stages = stage(key = "out_for_delivery", status = null))))
        assertTrue("got: $result", result.isSuccess)
        assertEquals(null, result.getOrNull()!!.journeys.single().stages.single().status)
    }

    // ── Degenerate input ───────────────────────────────────────────────────

    @Test
    fun `a bare array with no envelope is not accepted as a page`() {
        // Without `meta` there is no page echo to validate against, so this is
        // a failure rather than a page 1 we invented.
        val result = load("""[${row()}]""")
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `an out-of-range request is rejected before any network call`() {
        assertTrue(runBlocking { repo(body(row())).packageJourneys(0, 50) }.isFailure)
        assertTrue(runBlocking { repo(body(row())).packageJourneys(1, 0) }.isFailure)
        assertTrue(runBlocking { repo(body(row())).packageJourneys(1, 51) }.isFailure)
    }
}
