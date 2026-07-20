package com.ga.airdrop.feature.delivery

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import org.json.JSONObject
import java.util.Locale

/**
 * Map card for the Delivery Method screen — Swift buildMapCard parity slot
 * (335×201, rounded 5, no border; Figma 40008740:28298).
 *
 * Renders the SAME Google Maps picker the Laravel web app and the Swift app
 * use: `GET {WEB_BASE}/api/v1/delivery/picker?embed=ios` inside a WebView. The
 * server injects the Google Maps JS with its own key — the JS Maps API works
 * from an airdropja.com origin (which a WebView loading that URL has), even
 * though Static Maps / Android-SDK Maps are disabled on the GCP project.
 * `embed=ios` hides the page's own search / GPS / selected-card chrome so only
 * the map fills the card; the native screen keeps its controls.
 *
 * Bridge: the page posts `{event:'location-selected', latitude, longitude,
 * address}` to `window.webkit.messageHandlers.deliveryPicker` (its iOS host).
 * We shim that object to an Android `@JavascriptInterface` after page load, so
 * a map tap / marker drag flows to [onPointPicked] (native reverse-geocode +
 * fee validation). Native selections push back via `window.moveMarkerTo`.
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
    // Read the CURRENT marker inside the once-built WebViewClient — capturing
    // the composable param would freeze it at the first frame (always null on a
    // fresh VM) and lose the pin after a reload.
    val latestMarker = remember { arrayOfNulls<Pair<Double, Double>>(1) }
    latestMarker[0] = marker
    // Drop bridge posts that land after the card leaves the composition.
    val disposed = remember { booleanArrayOf(false) }
    // Rebuild the WebView after a renderer crash instead of leaving a dead one.
    var crashKey by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    val expectedHost = remember {
        runCatching { Uri.parse(BuildConfig.WEB_BASE_URL).host }.getOrNull()
    }

    val webView = remember(crashKey) {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        loadRetries[0] = 0
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
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
                            if (disposed[0]) return@post
                            lastPushed[0] = lat
                            lastPushed[1] = lng
                            onPointPicked?.invoke(lat, lng)
                        }
                    }
                },
                "AndroidDeliveryPicker",
            )
            webViewClient = object : WebViewClient() {
                // Keep the WebView pinned to our own origin — the JS bridge must
                // never be exposed to a redirected/injected off-host page.
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val host = request?.url?.host ?: return false
                    return host != expectedHost
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadRetries[0] = 0
                    loadFailed = false
                    view?.evaluateJavascript(BRIDGE_SHIM_JS, null)
                    view?.evaluateJavascript(RESIZE_NUDGE_JS, null)
                    // Re-assert the CURRENT marker (not a stale captured one) and
                    // reset the push-guard so update{} re-pushes after a reload.
                    lastPushed[0] = Double.NaN
                    lastPushed[1] = Double.NaN
                    latestMarker[0]?.let { (lat, lng) ->
                        lastPushed[0] = lat
                        lastPushed[1] = lng
                        view?.evaluateJavascript(moveMarkerJs(lat, lng), null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame != true) return
                    if (loadRetries[0] < 2) {
                        loadRetries[0]++
                        view?.postDelayed({ view.reload() }, 800)
                    } else {
                        loadFailed = true
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?,
                ): Boolean {
                    // Survive a renderer crash (emulator WebGL can OOM) without
                    // killing the host Activity, and rebuild a fresh WebView.
                    (view?.parent as? ViewGroup)?.removeView(view)
                    mainHandler.post { if (!disposed[0]) crashKey++ }
                    return true
                }
            }
        }
    }

    // Mark disposed only when the card truly leaves (not on a crash rebuild).
    DisposableEffect(Unit) {
        disposed[0] = false
        onDispose { disposed[0] = true }
    }

    // Load once per WebView instance. Initial centre comes from the current
    // marker (zoom 15); with none the page falls back to a Jamaica-wide view.
    DisposableEffect(webView) {
        val base = "${BuildConfig.WEB_BASE_URL}/api/v1/delivery/picker?embed=ios"
        val m = latestMarker[0]
        val url = m?.let { (lat, lng) -> "$base&lat=${fmt(lat)}&lng=${fmt(lng)}" } ?: base
        m?.let { lastPushed[0] = it.first; lastPushed[1] = it.second }
        // Load AFTER layout so Maps doesn't initialise into a 0-height card.
        webView.post { webView.loadUrl(url) }
        onDispose { webView.destroy() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Swift buildMapCard — 201pt tall, rounded 5, NO border.
            .height(201.dp)
            .clip(RoundedCornerShape(5.dp))
            .testTag("delivery-map"),
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val m = marker
                if (m == null) {
                    // De-selected: hide the page pin if we had pushed one.
                    if (!lastPushed[0].isNaN()) {
                        lastPushed[0] = Double.NaN
                        lastPushed[1] = Double.NaN
                        view.evaluateJavascript(CLEAR_MARKER_JS, null)
                    }
                } else if (m.first != lastPushed[0] || m.second != lastPushed[1]) {
                    // Mirror native selections (search / use-current-location)
                    // onto the page — but not the ones the page itself reported.
                    lastPushed[0] = m.first
                    lastPushed[1] = m.second
                    view.evaluateJavascript(moveMarkerJs(m.first, m.second), null)
                }
            },
        )
        if (loadFailed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AirdropTheme.colors.gray200)
                    .clickable {
                        loadRetries[0] = 0
                        loadFailed = false
                        crashKey++
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Map unavailable — tap to retry",
                    style = AirdropType.body3,
                    color = AirdropTheme.colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

private fun moveMarkerJs(lat: Double, lng: Double): String =
    "window.moveMarkerTo && window.moveMarkerTo(${fmt(lat)}, ${fmt(lng)});"

/** Hide the page's marker when the app de-selects the delivery point. */
private const val CLEAR_MARKER_JS =
    "if (window.marker && window.marker.setVisible) { window.marker.setVisible(false); }"

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
 * after each page load (the page only calls it on user interaction).
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
