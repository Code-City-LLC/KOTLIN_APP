package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.PackageTimelinePayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * `GET /packages/{id}/timeline` — the strict contract, matched to iOS.
 *
 * ⚠️ KOTLIN WAS ACCEPTING PAYLOADS iOS REJECTS, AND I TOLD THE SWIFT OWNER
 * OTHERWISE.
 *
 * @Codex-CodexKotlinAudit proved the drift against current source (#95189) and
 * it contradicted my own #95154 row 7, where I told @Claude-SwiftHawk that
 * Kotlin enforces `total == entries.count`. Kotlin did not decode `total` at
 * all. I stated a contract to another lane owner without re-reading my side.
 *
 * Reference is iOS `f9c95b2`, `AirdropAPI.swift` 4174-4284:
 *
 * | field | rule |
 * |---|---|
 * | `package_id` | required, > 0, must match the request |
 * | `entries` | REQUIRED. A present `[]` is a genuine empty history; a MISSING key is a schema failure |
 * | `has_delivery` | REQUIRED |
 * | `total` | REQUIRED and exactly `entries.count` |
 * | entry `key` | REQUIRED non-empty — it is the identity/dedup key |
 * | entry `label` | blank -> `"Update"`, NEVER a throw (see below) |
 * | entry `state` | REQUIRED, strictly `done` \| `current` \| `pending` |
 * | entry `status` | null ONLY when `source == "delivery"`; null anywhere else fails closed |
 *
 * The label fallback is deliberately lenient where everything else is strict:
 * the entry list decodes all-or-nothing, so throwing on one blank label would
 * error the ENTIRE timeline over a single unnamed status — a known live DB
 * condition. iOS mirrors the server's own `?? 'Update'` for exactly this
 * reason, and Kotlin now does too.
 *
 * The source/status invariant is the subtle one (@MagentaReef #89692): the
 * timeline has two rails. Warehouse entries (`source: "status"`) ALWAYS carry a
 * numeric package status; delivery-leg entries (`source: "delivery"`) ALWAYS
 * have `status: null`. Making status optional for EVERY entry was too broad —
 * it silently accepted a statusless WAREHOUSE row, hiding the status the app
 * needs for NEEDS_HELP/Contact rows.
 */
class PackageTimelineContractTest {

    private fun repo(json: String) = DeliveryTrackingRepository(
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "packageTimeline" -> AirdropJson.decodeFromString(
                    DataEnvelope.serializer(PackageTimelinePayload.serializer()),
                    json,
                )
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService,
    )

    private fun load(json: String, id: Int = 41) = runBlocking { repo(json).packageTimeline(id) }

    private fun entry(
        key: String = "received",
        label: String = "Received at Warehouse",
        state: String = "done",
        status: String = "3",
        source: String = "status",
    ) = """{"key":"$key","status":$status,"label":"$label","icon":"box",
           "at":"Aug 1","at_iso":null,"state":"$state","source":"$source"}"""

    private fun body(
        entries: String,
        total: Int,
        id: Int = 41,
        hasDelivery: Boolean = false,
    ) = """{"success":true,"data":{"package_id":$id,"tracking_code":"AIR-$id",
           "entries":[$entries],"current_key":"received",
           "has_delivery":$hasDelivery,"total":$total}}"""

    // ── The honest cases must keep working ──────────────────────────────────

    @Test
    fun `a well-formed timeline loads`() {
        val r = load(body(entry(), total = 1))
        assertTrue("got: $r", r.isSuccess)
        assertEquals(1, r.getOrNull()!!.size)
        assertEquals("Received at Warehouse", r.getOrNull()!!.single().label)
    }

    @Test
    fun `an explicit empty history is VALID — entries present, total zero`() {
        val r = load(
            """{"success":true,"data":{"package_id":41,"tracking_code":null,"entries":[],
               "current_key":null,"has_delivery":false,"total":0}}""",
        )
        assertTrue("a real 'nothing recorded yet' must come through. Got: $r", r.isSuccess)
        assertTrue(r.getOrNull()!!.isEmpty())
    }

    @Test
    fun `a delivery-rail entry may carry a null status`() {
        val r = load(
            body(entry(key = "out_for_delivery", status = "null", source = "delivery"), total = 1),
        )
        assertTrue("the delivery rail legitimately has no package status. Got: $r", r.isSuccess)
    }

    // ── total ───────────────────────────────────────────────────────────────

    @Test
    fun `a MISSING total is rejected`() {
        // This is the row I told SwiftHawk was enforced. It was not decoded.
        val r = load(
            """{"success":true,"data":{"package_id":41,"entries":[${entry()}],
               "current_key":"received","has_delivery":false}}""",
        )
        assertTrue("total is required by the contract. Got: $r", r.isFailure)
    }

    @Test
    fun `a total that disagrees with the entry count is rejected`() {
        val r = load(body(entry(), total = 9))
        assertTrue("total must equal entries.size — a mismatch is a truncated or corrupt page. Got: $r", r.isFailure)
    }

    // ── entries ─────────────────────────────────────────────────────────────

    @Test
    fun `a MISSING entries key is a schema failure, not an empty journey`() {
        // The whole defect class this codebase keeps producing: absence
        // rendered as fact. A customer with a full journey must not be told
        // there is none because the key vanished.
        val r = load(
            """{"success":true,"data":{"package_id":41,"current_key":null,
               "has_delivery":false,"total":0}}""",
        )
        assertTrue("a missing entries key must NOT read as 'no history'. Got: $r", r.isFailure)
    }

    // ── has_delivery ────────────────────────────────────────────────────────

    @Test
    fun `a MISSING has_delivery is rejected`() {
        val r = load(
            """{"success":true,"data":{"package_id":41,"entries":[${entry()}],
               "current_key":"received","total":1}}""",
        )
        assertTrue("has_delivery drives the pickup/delivery split. Got: $r", r.isFailure)
    }

    // ── entry key ───────────────────────────────────────────────────────────

    @Test
    fun `an entry with a blank or missing key is rejected, NOT given a fabricated one`() {
        // TrackJourney.rows used to invent "entry_<index>". A synthetic key is
        // not an identity: it changes when the list reorders, so dedup and
        // per-row state silently attach to the wrong row.
        assertTrue(load(body(entry(key = "   "), total = 1)).isFailure)
        assertTrue(
            load(
                """{"success":true,"data":{"package_id":41,"entries":[
                   {"status":3,"label":"Received","state":"done","source":"status"}],
                   "current_key":null,"has_delivery":false,"total":1}}""",
            ).isFailure,
        )
    }

    // ── entry label — lenient ON PURPOSE ────────────────────────────────────

    @Test
    fun `a blank label falls back to Update instead of dropping the row`() {
        // rows() used to DROP a blank-label entry, so a real recorded event
        // silently vanished from the customer's journey. Throwing would be
        // worse still: the list decodes all-or-nothing, so one unnamed status
        // would error the whole timeline. Mirror the server's own fallback.
        val r = load(body(entry(label = "   "), total = 1))
        assertTrue("got: $r", r.isSuccess)
        assertEquals("Update", r.getOrNull()!!.single().label)
    }

    // ── entry state ─────────────────────────────────────────────────────────

    @Test
    fun `a MISSING state is rejected, not defaulted to done`() {
        // rows() defaulted a missing state to "done" — which tells the customer
        // a step COMPLETED when the server never said so.
        val r = load(
            """{"success":true,"data":{"package_id":41,"entries":[
               {"key":"received","status":3,"label":"Received","source":"status"}],
               "current_key":null,"has_delivery":false,"total":1}}""",
        )
        assertTrue("a missing state must not become 'done'. Got: $r", r.isFailure)
    }

    @Test
    fun `an unrecognised state is rejected, not passed through`() {
        val r = load(body(entry(state = "collected"), total = 1))
        assertTrue("Laravel owns state and emits only done|current|pending. Got: $r", r.isFailure)
    }

    @Test
    fun `all three server states come through unchanged`() {
        val r = load(
            body(
                """${entry(key = "a", state = "done")},
                   ${entry(key = "b", state = "current")},
                   ${entry(key = "c", state = "pending")}""",
                total = 3,
            ),
        )
        assertEquals(listOf("done", "current", "pending"), r.getOrNull()!!.map { it.state })
    }

    // ── the source/status invariant ─────────────────────────────────────────

    @Test
    fun `a WAREHOUSE entry with a null status is rejected`() {
        // @MagentaReef #89692: status:null is valid ONLY on the delivery rail.
        // Accepting it on a warehouse row hides the package status the app
        // needs for NEEDS_HELP / Contact affordances — a silent capability loss.
        val r = load(body(entry(status = "null", source = "status"), total = 1))
        assertTrue("only the delivery rail may omit status. Got: $r", r.isFailure)
    }

    @Test
    fun `an entry with a null status and NO source is rejected`() {
        val r = load(
            """{"success":true,"data":{"package_id":41,"entries":[
               {"key":"k","status":null,"label":"L","state":"done"}],
               "current_key":null,"has_delivery":false,"total":1}}""",
        )
        assertTrue("absent source is not the delivery rail. Got: $r", r.isFailure)
    }

    // ── package identity still enforced ─────────────────────────────────────

    @Test
    fun `another package's timeline is still rejected`() {
        val r = load(body(entry(), total = 1, id = 999), id = 41)
        assertTrue("got: $r", r.isFailure)
    }
}
