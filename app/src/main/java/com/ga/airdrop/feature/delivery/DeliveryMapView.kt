package com.ga.airdrop.feature.delivery

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ga.airdrop.BuildConfig
import org.json.JSONObject
import java.util.Locale

/**
 * Map card for the Delivery Method screen — Swift buildMapCard parity slot
 * (335×201, rounded 5, no border; Figma 40008740:28298).
 *
 * Renders the SAME Google Maps picker the Laravel web app and the Swift app
 * use: `GET {WEB_BASE}/api/v1/delivery/picker?embed=ios` inside a WebView. The
 * server injects the Google Maps JS with its own key (`GoogleMapsService`) —
 * the Static Maps / Android-SDK Maps products are disabled on the GCP project
 * and we can't enable them, but the **JS Maps API works from an airdropja.com
 * origin**, which a WebView loading that URL has. `embed=ios` hides the page's
 * own search / GPS / selected-card chrome so the native screen keeps its
 * controls and only the map fills the card.
 *
 * Bridge: the page posts `{event:'location-selected', latitude, longitude,
 * address}` to `window.webkit.messageHandlers.deliveryPicker` (its iOS host
 * bridge). We shim that object to an Android `@JavascriptInterface` after page
 * load, so a map tap / marker drag flows to [onPointPicked] (native
 * reverse-geocode + fee validation). Native selections push back to the page
 * via its `window.moveMarkerTo(lat,lng)` hook.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeliveryMapView(
    center: Pair<Double, Double>?,
    marker: Pair<Double, Double>?,
    addressLabel: String?,
    onPointPicked: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // Break the moveMarkerTo → emit → onPointPicked → recompose → moveMarkerTo
    // cycle: never re-push a coordinate the page just emitted back to us.
    val lastPushed = remember { doubleArrayOf(Double.NaN, Double.NaN) }
    // One-shot self-heal for a transient main-frame load timeout (the Maps JS
    // payload is large; a flaky network can ERR_TIMED_OUT the first attempt).
    val loadRetries = remember { intArrayOf(0) }

    val webView = remember {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.setGeolocationEnabled(false)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onPicked(json: String) {
                        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
                        if (obj.optString("event") != "location-selected") return
                        val lat = obj.optDouble("latitude", Double.NaN)
                        val lng = obj.optDouble("longitude", Double.NaN)
                        if (lat.isNaN() || lng.isNaN()) return
                        mainHandler.post {
                            lastPushed[0] = lat
                            lastPushed[1] = lng
                            onPointPicked?.invoke(lat, lng)
                        }
                    }
                },
                "AndroidDeliveryPicker",
            )
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(BRIDGE_SHIM_JS, null)
                    // The card is 0px tall in the compose pass that triggers
                    // the load, so Google Maps can initialise into a zero-size
                    // container and paint blank. Fire a few resizes once we're
                    // laid out so the map re-reads its size and repaints.
                    view?.evaluateJavascript(RESIZE_NUDGE_JS, null)
                    // Re-assert the current marker after a (re)load.
                    val m = marker
                    if (m != null) {
                        view?.evaluateJavascript(moveMarkerJs(m.first, m.second), null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true && loadRetries[0] < 2) {
                        loadRetries[0]++
                        view?.postDelayed({ view.reload() }, 800)
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: android.webkit.RenderProcessGoneDetail?,
                ): Boolean {
                    // Survive a renderer crash (emulator WebGL can OOM) instead
                    // of letting it kill the host Activity. Detach the dead view.
                    (view?.parent as? android.view.ViewGroup)?.removeView(view)
                    view?.destroy()
                    return true
                }
            }
        }
    }

    // Load once. Initial centre comes from the current marker (zoom 15), else
    // the page falls back to a Jamaica-wide view.
    DisposableEffect(webView) {
        val base = "${BuildConfig.WEB_BASE_URL}/api/v1/delivery/picker?embed=ios"
        val url = marker?.let { (lat, lng) -> "$base&lat=${fmt(lat)}&lng=${fmt(lng)}" } ?: base
        marker?.let { lastPushed[0] = it.first; lastPushed[1] = it.second }
        // Load AFTER the view is laid out — if Google Maps initialises into a
        // 0-height card it paints blank, so wait until the WebView has real size.
        webView.post { webView.loadUrl(url) }
        onDispose { webView.destroy() }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxWidth()
            // Swift buildMapCard — 201pt tall, rounded 5, NO border.
            .height(201.dp)
            .clip(RoundedCornerShape(5.dp))
            .testTag("delivery-map"),
        update = { view ->
            // Mirror native selections (search / use-current-location) onto the
            // page — but not the ones the page itself just reported.
            val m = marker ?: return@AndroidView
            if (m.first != lastPushed[0] || m.second != lastPushed[1]) {
                lastPushed[0] = m.first
                lastPushed[1] = m.second
                view.evaluateJavascript(moveMarkerJs(m.first, m.second), null)
            }
        },
    )
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

private fun moveMarkerJs(lat: Double, lng: Double): String =
    "window.moveMarkerTo && window.moveMarkerTo(${fmt(lat)}, ${fmt(lng)});"

/**
 * The picker's `#map` uses `height:100%`, whose percentage chain collapses to
 * 0 inside the WebView (verified: the div measured height 0 while the viewport
 * was 202px), so Google Maps renders blank. Force an explicit pixel height on
 * `#map` and trigger a Maps resize once the map object exists — retried until
 * the async Maps JS has created `window.map`.
 */
private const val RESIZE_NUDGE_JS = """
(function(){
  function fix(){
    var m = document.getElementById('map');
    if (m) { m.style.height = window.innerHeight + 'px'; m.style.width = '100%'; }
    if (window.map && window.google && window.google.maps) {
      try {
        google.maps.event.trigger(window.map, 'resize');
        window.map.setCenter(window.map.getCenter());
      } catch (e) {}
      return true;
    }
    return false;
  }
  var n = 0;
  var iv = setInterval(function(){ if (fix() || ++n > 40) clearInterval(iv); }, 250);
  window.addEventListener('resize', fix);
})();
"""

/**
 * Shim the page's iOS host bridge onto our Android `@JavascriptInterface`, so
 * the unmodified Laravel picker's `postToNative(...)` reaches Kotlin. Injected
 * after each page load (the page only calls it on user interaction, well after
 * load).
 */
private const val BRIDGE_SHIM_JS = """
(function(){
  window.webkit = window.webkit || {};
  window.webkit.messageHandlers = window.webkit.messageHandlers || {};
  window.webkit.messageHandlers.deliveryPicker = {
    postMessage: function(p){
      try { AndroidDeliveryPicker.onPicked(typeof p === 'string' ? p : JSON.stringify(p)); } catch (e) {}
    }
  };
})();
"""
