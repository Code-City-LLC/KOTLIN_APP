package com.ga.airdrop.feature.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire contract with Laravel's delivery-picker page
 * (resources/views/delivery-picker.blade.php on origin/pre_staging):
 * the iOS-shaped `webkit.messageHandlers.deliveryPicker` shim, the
 * `?embed=ios` URL, and every payload shape `postToNative` emits.
 */
class DeliveryPickerBridgeTest {

    /* ── URL ──────────────────────────────────────────────────────────── */

    @Test
    fun `picker url hides in-page chrome and tolerates trailing slash`() {
        assertEquals(
            "https://pre-staging.airdropja.com/api/v1/delivery/picker?embed=ios",
            deliveryPickerUrl("https://pre-staging.airdropja.com/api/v1/"),
        )
        assertEquals(
            "https://pre-staging.airdropja.com/api/v1/delivery/picker?embed=ios",
            deliveryPickerUrl("https://pre-staging.airdropja.com/api/v1"),
        )
    }

    @Test
    fun `picker url anchors a saved coordinate via lat lng params`() {
        assertEquals(
            "https://x/api/v1/delivery/picker?embed=ios&lat=18.012000&lng=-76.793000",
            deliveryPickerUrl("https://x/api/v1", 18.012 to -76.793),
        )
    }

    /* ── Native → page ────────────────────────────────────────────────── */

    @Test
    fun `moveMarker js is locale-stable decimal-dot`() {
        assertEquals(
            "window.moveMarkerTo(18.470000, -77.918000);",
            moveMarkerJs(18.47, -77.918),
        )
    }

    @Test
    fun `readiness probe keys on moveMarkerTo — assigned last in map init`() {
        assertTrue(MAP_READY_PROBE_JS.contains("window.moveMarkerTo"))
        assertTrue(MAP_READY_PROBE_JS.contains("'ready'"))
    }

    @Test
    fun `height rescue pins explicit px height and triggers maps resize`() {
        val js = fixMapHeightJs(201)
        assertTrue(js.contains("getElementById('map')"))
        assertTrue(js.contains("height='201px'"))
        assertTrue(js.contains("trigger(window.map,'resize')"))
    }

    @Test
    fun `shim forwards the iOS handler to the android bridge exactly once`() {
        assertTrue(DELIVERY_PICKER_WEBKIT_SHIM.contains("__airdropPickerShim"))
        assertTrue(
            DELIVERY_PICKER_WEBKIT_SHIM
                .contains("window.webkit.messageHandlers.deliveryPicker"),
        )
        assertTrue(
            DELIVERY_PICKER_WEBKIT_SHIM
                .contains("window.$JS_BRIDGE_NAME.postMessage(JSON.stringify(payload))"),
        )
    }

    /* ── Page → native payloads ───────────────────────────────────────── */

    @Test
    fun `location-selected parses coordinates and address`() {
        val event = parsePickerEvent(
            """{"event":"location-selected","latitude":18.0179,""" +
                """"longitude":-76.8099,"address":"Half Way Tree, Kingston"}""",
        )
        assertEquals(
            PickerEvent.LocationSelected(18.0179, -76.8099, "Half Way Tree, Kingston"),
            event,
        )
    }

    @Test
    fun `location-selected without a usable address carries null`() {
        val missing = parsePickerEvent(
            """{"event":"location-selected","latitude":18.0,"longitude":-76.8}""",
        )
        assertEquals(PickerEvent.LocationSelected(18.0, -76.8, null), missing)

        val blank = parsePickerEvent(
            """{"event":"location-selected","latitude":18.0,"longitude":-76.8,"address":"  "}""",
        )
        assertEquals(PickerEvent.LocationSelected(18.0, -76.8, null), blank)
    }

    @Test
    fun `location-selected with unusable coordinates is ignored`() {
        assertEquals(
            PickerEvent.Ignored,
            parsePickerEvent("""{"event":"location-selected","address":"x"}"""),
        )
    }

    @Test
    fun `map-ready and script errors map to their events`() {
        assertEquals(PickerEvent.MapReady, parsePickerEvent("""{"event":"map-ready"}"""))
        assertEquals(
            PickerEvent.ScriptError("boom"),
            parsePickerEvent("""{"event":"js-error","message":"boom"}"""),
        )
        assertEquals(
            PickerEvent.ScriptError(
                "Google Maps JS never loaded — likely a referer-restricted API key " +
                    "or a network block in the WebView.",
            ),
            parsePickerEvent(
                """{"event":"maps-script-error","message":"Google Maps JS never """ +
                    """loaded — likely a referer-restricted API key or a network """ +
                    """block in the WebView."}""",
            ),
        )
    }

    @Test
    fun `gps chatter and unknown events are ignored, malformed is null`() {
        assertEquals(PickerEvent.Ignored, parsePickerEvent("""{"event":"gps-requested"}"""))
        assertEquals(
            PickerEvent.Ignored,
            parsePickerEvent("""{"event":"gps-error","message":"denied"}"""),
        )
        assertEquals(
            PickerEvent.Ignored,
            parsePickerEvent("""{"event":"search-no-match","query":"Atlantis"}"""),
        )
        assertEquals(PickerEvent.Ignored, parsePickerEvent("""{"latitude":1.0}"""))
        assertNull(parsePickerEvent("not json"))
        assertNull(parsePickerEvent("[1,2,3]"))
    }

    /* ── Echo suppression ─────────────────────────────────────────────── */

    @Test
    fun `coordsMatch tolerates the six-decimal round-trip and nothing more`() {
        assertTrue(coordsMatch(18.0179 to -76.8099, 18.017903 to -76.809897))
        assertFalse(coordsMatch(18.0179 to -76.8099, 18.0181 to -76.8099))
    }
}
