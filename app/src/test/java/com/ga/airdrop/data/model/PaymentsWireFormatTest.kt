package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts against the **encoded JSON**, not the Kotlin object.
 *
 * That distinction is the whole point. `AirdropJson` runs with
 * `encodeDefaults = false`, so a request field whose value equals its default
 * is dropped from the wire silently — the object looks correct in a debugger
 * and in any test that inspects the data class, while the server receives
 * nothing. That has already cost this codebase real request fields.
 *
 * The "Your Note" bug was a layer above the same blind spot: the repository
 * accepted a `userNote` argument and never placed it in the body at all, with
 * `@Suppress("UNUSED_PARAMETER")` silencing the warning that would have caught
 * it. A customer's note was stored, trimmed, passed through three layers and
 * discarded at the last — while Laravel (`Payments.php:23` `user_note`) and iOS
 * (`AirdropAPI.swift:6039`) both supported it.
 */
class PaymentsWireFormatTest {

    private fun encode(r: CreateCheckoutRequest) =
        AirdropJson.encodeToString(CreateCheckoutRequest.serializer(), r)

    private fun request(note: String?) = CreateCheckoutRequest(
        packageIds = listOf(101, 102),
        currency = "USD",
        isAuction = false,
        returnUrl = MOBILE_CHECKOUT_RETURN_URL,
        userNote = note,
    )

    @Test
    fun `a note actually reaches the wire under the server's key`() {
        val json = encode(request("Leave with the front desk"))

        assertTrue(
            "user_note must be present in the encoded body — the object holding it " +
                "is not evidence the server receives it. Got: $json",
            json.contains("\"user_note\""),
        )
        assertTrue(json.contains("Leave with the front desk"))
    }

    /** Null must be OMITTED, not sent as an explicit null — iOS sends nil. */
    @Test
    fun `no note omits the key entirely rather than sending null`() {
        val json = encode(request(null))

        assertFalse(
            "a null note must not appear on the wire at all. Got: $json",
            json.contains("user_note"),
        )
    }

    /** The other fields must survive the addition — including the enum-ish ones. */
    @Test
    fun `the rest of the checkout body still encodes with the server's key names`() {
        val json = encode(request("hi"))

        listOf("package_ids", "currency", "is_auction", "return_url").forEach {
            assertTrue("$it missing from encoded body: $json", json.contains("\"$it\""))
        }
    }

    /**
     * `is_auction = false` is the value most at risk from `encodeDefaults`:
     * Boolean's implicit default is also false. It has no Kotlin default here,
     * so it must still be written — if someone ever gives it one, a normal cart
     * checkout would stop declaring itself non-auction.
     */
    @Test
    fun `is_auction false is still written, not swallowed as a default`() {
        val json = encode(request(null))

        assertTrue(
            "is_auction=false must survive encoding; a default would erase it. Got: $json",
            json.contains("\"is_auction\":false"),
        )
    }

    /** Round-trips, so the key names are right in both directions. */
    @Test
    fun `the encoded body decodes back to the same request`() {
        val original = request("note here")
        val decoded = AirdropJson.decodeFromString(
            CreateCheckoutRequest.serializer(),
            encode(original),
        )

        assertEquals(original, decoded)
    }
}
