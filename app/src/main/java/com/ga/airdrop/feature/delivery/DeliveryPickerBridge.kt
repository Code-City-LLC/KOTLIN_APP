package com.ga.airdrop.feature.delivery

import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire contract for Laravel's delivery-location picker page
 * (`GET /api/v1/delivery/picker`, resources/views/delivery-picker.blade.php).
 *
 * That page is the backend's OWN mapping surface: it carries the server's
 * Google Maps key, restricts geocoding to Jamaica, and reports every pick
 * back to the host app. Swift renders MKMapView natively (Apple tiles are
 * key-free on iOS); Android has no key-free map, so the Kotlin app embeds
 * the backend page itself — one map/geocode implementation, no duplicate.
 *
 * Page → native: `postToNative(payload)` targets
 * `window.webkit.messageHandlers.deliveryPicker.postMessage` (iOS shape).
 * The handler is resolved PER CALL, so Android injects
 * [DELIVERY_PICKER_WEBKIT_SHIM] after load to forward every payload to the
 * [JS_BRIDGE_NAME] JavascriptInterface as a JSON string.
 *
 * Native → page: `window.moveMarkerTo(lat, lng)` (see [moveMarkerJs]) keeps
 * the map in sync with natively-driven picks (search dropdown, GPS pill,
 * saved-preference rehydrate). `?embed=ios` hides the page's in-page search
 * field, GPS button and selected-card — the Kotlin screen renders those
 * natively (Swift-parity chrome).
 */

/** Name of the injected JavascriptInterface object. */
internal const val JS_BRIDGE_NAME = "AirdropDeliveryPicker"

/**
 * Injected at onPageFinished: forwards the page's iOS-shaped
 * `webkit.messageHandlers.deliveryPicker.postMessage` calls to the Android
 * bridge. Idempotent; never overrides a real iOS handler (none on Android).
 */
internal val DELIVERY_PICKER_WEBKIT_SHIM = """
    (function() {
        if (window.__airdropPickerShim) { return; }
        window.__airdropPickerShim = true;
        window.webkit = window.webkit || {};
        window.webkit.messageHandlers = window.webkit.messageHandlers || {};
        window.webkit.messageHandlers.deliveryPicker = {
            postMessage: function(payload) {
                if (window.$JS_BRIDGE_NAME) {
                    window.$JS_BRIDGE_NAME.postMessage(JSON.stringify(payload));
                }
            }
        };
    })();
""".trimIndent()

/**
 * Picker page URL. [apiBaseUrl] is BuildConfig.API_BASE_URL (…/api/v1, with
 * or without trailing slash); [initial] anchors the map on a saved delivery
 * coordinate (page zooms in), else the page opens on its Jamaica-wide view.
 */
internal fun deliveryPickerUrl(
    apiBaseUrl: String,
    initial: Pair<Double, Double>? = null,
): String {
    val base = apiBaseUrl.trimEnd('/')
    val coords = initial?.let {
        String.format(Locale.US, "&lat=%.6f&lng=%.6f", it.first, it.second)
    }.orEmpty()
    return "$base/delivery/picker?embed=ios$coords"
}

/** `window.moveMarkerTo(lat, lng)` — page re-geocodes and echoes the pick. */
internal fun moveMarkerJs(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "window.moveMarkerTo(%.6f, %.6f);", latitude, longitude)

/**
 * Readiness probe. The page loads the Maps script SYNCHRONOUSLY, so its
 * `map-ready` postToNative can run before onPageFinished injects the shim
 * and be swallowed by the absent iOS handler. `window.moveMarkerTo` is
 * assigned at the END of initializeDeliveryMap — its presence proves the
 * map fully initialized even when the map-ready event was missed.
 */
internal const val MAP_READY_PROBE_JS =
    "(typeof window.moveMarkerTo === 'function') ? 'ready' : 'pending'"

/**
 * Viewport-height rescue. When Compose measures the WebView at 0 height for
 * an early pass, the page's percentage chain (`html,body{height:100%}` →
 * `#map{height:100%}`) resolves to 0 and never recovers (observed live:
 * mapW=372 mapH=0 while the surface paints). Pinning an explicit CSS-px
 * height on #map sidesteps the dead viewport; the Maps resize + recenter
 * make the tiles paint. Idempotent — safe on every layout pass.
 */
internal fun fixMapHeightJs(cssHeightPx: Int): String =
    "(function(){var m=document.getElementById('map');if(!m){return 'no-map';}" +
        "m.style.height='" + cssHeightPx + "px';" +
        "if(window.map&&window.google&&window.google.maps&&window.google.maps.event){" +
        "window.google.maps.event.trigger(window.map,'resize');" +
        "if(window.marker&&window.marker.getPosition){" +
        "window.map.setCenter(window.marker.getPosition());}}" +
        "return 'fixed';})()"

/** Echo suppression tolerance — moveMarkerTo round-trips at 6 decimals. */
internal fun coordsMatch(
    a: Pair<Double, Double>,
    b: Pair<Double, Double>,
    epsilonDegrees: Double = 1e-5,
): Boolean = abs(a.first - b.first) <= epsilonDegrees &&
    abs(a.second - b.second) <= epsilonDegrees

/** Parsed picker payloads the native side reacts to. */
internal sealed interface PickerEvent {
    /** Map tap / marker drag / initial-anchor geocode: a candidate point. */
    data class LocationSelected(
        val latitude: Double,
        val longitude: Double,
        /** Page-side reverse-geocoded address; null when unresolved. */
        val address: String?,
    ) : PickerEvent

    /** Google Maps initialized — the live map is on screen. */
    data object MapReady : PickerEvent

    /** `js-error` / `maps-script-error` — map surface is unusable. */
    data class ScriptError(val message: String?) : PickerEvent

    /** Recognized-but-irrelevant (gps-*, search-no-match, unknown). */
    data object Ignored : PickerEvent
}

private val pickerJson = Json { ignoreUnknownKeys = true }

/**
 * Parse one `postMessage` payload. Returns null for malformed JSON (the
 * page only ever posts objects; anything else is dropped silently).
 */
internal fun parsePickerEvent(raw: String): PickerEvent? {
    val obj = try {
        pickerJson.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        return null
    }
    val event = (obj["event"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    return when (event) {
        "location-selected" -> {
            val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
            val lng = obj["longitude"]?.jsonPrimitive?.doubleOrNull
            if (lat == null || lng == null) {
                PickerEvent.Ignored
            } else {
                val address = (obj["address"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                PickerEvent.LocationSelected(lat, lng, address)
            }
        }

        "map-ready" -> PickerEvent.MapReady

        "js-error", "maps-script-error" -> PickerEvent.ScriptError(
            (obj["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
        )

        else -> PickerEvent.Ignored
    }
}
