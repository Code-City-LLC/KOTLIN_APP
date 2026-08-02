package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.NcbCompleteRequest
import com.ga.airdrop.data.model.NcbSessionResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `checkout_id` must reach the wire on completion.
 *
 * Laravel `af2e01b3` binds settlement to `{spi_token, checkout_id,
 * authenticated user}`. Kotlin **decoded** `checkout_id` on the session
 * response and then **dropped it** — `NcbCompleteRequest` carried `spi_token`
 * alone, so every completion we sent was missing a field the server now
 * requires. Found by Codex in read-only review (ORC 89144), not by me.
 *
 * ## Why Long and not Int
 *
 * The id arrives as a JSON **string**. `toInt()` returns null above
 * `Int.MAX_VALUE`, and a null id is indistinguishable from "the server sent
 * none" — so we would fail closed for the **wrong reason**, and only on the
 * highest-numbered checkouts, which are the newest ones. That is the failure
 * mode that would have reached production first and been hardest to read.
 */
class NcbCheckoutIdContractTest {

    private fun session(json: String) =
        AirdropJson.decodeFromString<NcbSessionResponse>(json)

    private fun parse(raw: String?) = raw?.trim()?.toLongOrNull()?.takeIf { it > 0L }

    // ── The wire ────────────────────────────────────────────────────────────

    @Test
    fun `a completion request puts checkout_id on the wire`() {
        val body = AirdropJson.encodeToString(
            NcbCompleteRequest(spiToken = "tok", checkoutId = 4321L),
        )
        assertTrue("checkout_id missing from the body: $body", body.contains("\"checkout_id\""))
        assertTrue("value missing: $body", body.contains("4321"))
        assertTrue("spi_token missing: $body", body.contains("\"spi_token\""))
    }

    @Test
    fun `the session response actually carries checkout_id through`() {
        val s = session("""{"spi_token":"t","redirect_data":"<html/>","checkout_id":"9911"}""")
        assertEquals("9911", s.checkoutId)
        assertEquals(9911L, parse(s.checkoutId))
    }

    // ── Codex's four cases (ORC 89119) ──────────────────────────────────────

    @Test
    fun `an id above Int MAX_VALUE survives as a Long`() {
        // THE ONE I WOULD HAVE MISSED. toInt() returns null here, which reads as
        // "no id sent" rather than "id too large" — failing closed for the wrong
        // reason on exactly the newest checkouts.
        val big = Int.MAX_VALUE.toLong() + 1L // 2_147_483_648
        val s = session("""{"spi_token":"t","checkout_id":"$big"}""")

        assertEquals(big, parse(s.checkoutId))
        assertNull(
            "proof the naive parse loses it — this is why the field is Long",
            s.checkoutId?.toIntOrNull(),
        )

        val body = AirdropJson.encodeToString(
            NcbCompleteRequest(spiToken = "t", checkoutId = parse(s.checkoutId)!!),
        )
        assertTrue("the large id must survive to the wire: $body", body.contains("$big"))
    }

    @Test
    fun `a missing id yields null and must not be sent`() {
        assertNull(parse(session("""{"spi_token":"t"}""").checkoutId))
        assertNull(parse(session("""{"spi_token":"t","checkout_id":null}""").checkoutId))
        assertNull(parse(session("""{"spi_token":"t","checkout_id":""}""").checkoutId))
        assertNull(parse(session("""{"spi_token":"t","checkout_id":"   "}""").checkoutId))
    }

    @Test
    fun `a non-numeric id yields null rather than a silent zero`() {
        assertNull(parse(session("""{"spi_token":"t","checkout_id":"abc"}""").checkoutId))
        assertNull(parse(session("""{"spi_token":"t","checkout_id":"12ab"}""").checkoutId))
        assertNull(parse(session("""{"spi_token":"t","checkout_id":"1.5"}""").checkoutId))
    }

    @Test
    fun `zero and negative ids are refused`() {
        // A zero id would serialize happily and settle against nothing.
        assertNull(parse(session("""{"spi_token":"t","checkout_id":"0"}""").checkoutId))
        assertNull(parse(session("""{"spi_token":"t","checkout_id":"-1"}""").checkoutId))
    }

    @Test
    fun `a normal id is accepted, so the guard is not merely restrictive`() {
        assertEquals(1L, parse(session("""{"spi_token":"t","checkout_id":"1"}""").checkoutId))
        assertEquals(4321L, parse(session("""{"spi_token":"t","checkout_id":" 4321 "}""").checkoutId))
    }
}
