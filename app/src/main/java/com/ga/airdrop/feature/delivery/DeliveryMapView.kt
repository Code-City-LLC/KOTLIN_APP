package com.ga.airdrop.feature.delivery

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import java.util.Locale

/**
 * Map card for the Delivery Method screen — Swift buildMapCard parity slot
 * (335×201, rounded 5, no border; Figma 40008740:28298).
 *
 * Swift shows MKMapView (key-free Apple tiles) with a draggable marker.
 * Android has no key-free map, so this card embeds the BACKEND's own picker
 * page — `GET {API}/delivery/picker?embed=ios` — which carries the server's
 * Google Maps key and Jamaica-restricted geocoding. Every map interaction
 * (tap, marker drag) reverse-geocodes in-page and reaches [onPointPicked]
 * via the [JS_BRIDGE_NAME] bridge; native picks (search dropdown, GPS pill,
 * saved-preference rehydrate) sync back through `window.moveMarkerTo`.
 * Contract + shim live in DeliveryPickerBridge.kt. One mapping
 * implementation, owned by Laravel — no duplicated geocode paths.
 *
 * Until the page reports `map-ready` (or if its Google script fails) the
 * previous stylized placeholder shows, and search / Use Current Location
 * keep the flow fully functional without the interactive map.
 */
@Composable
fun DeliveryMapView(
    center: Pair<Double, Double>?,
    marker: Pair<Double, Double>?,
    addressLabel: String?,
    onPointPicked: ((latitude: Double, longitude: Double, address: String?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val currentOnPointPicked by rememberUpdatedState(onPointPicked)

    // Built once: anchor on an already-known marker (rehydrated preference),
    // else the page opens on its Jamaica-wide view. Later marker changes
    // sync via moveMarkerTo, never via reload.
    val initialUrl = remember { deliveryPickerUrl(BuildConfig.API_BASE_URL, marker) }
    val pickerHost = remember(initialUrl) { Uri.parse(initialUrl).host }

    var mapLive by remember { mutableStateOf(false) }
    var scriptFailed by remember { mutableStateOf(false) }
    // Page's current marker + the coord we just pushed via moveMarkerTo —
    // its reverse-geocode echo must not re-enter the ViewModel.
    val pageCoord = remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val suppressedEcho = remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    fun onPickerEvent(event: PickerEvent) {
        when (event) {
            PickerEvent.MapReady -> {
                mapLive = true
                scriptFailed = false
                // Rescue a 0-height layout viewport (see fixMapHeightJs).
                webViewHolder.value?.let { wv ->
                    if (wv.height > 0) {
                        val cssH = (wv.height / wv.resources.displayMetrics.density).toInt()
                        wv.evaluateJavascript(fixMapHeightJs(cssH), null)
                    }
                }
            }

            is PickerEvent.ScriptError -> scriptFailed = true

            is PickerEvent.LocationSelected -> {
                mapLive = true
                val coord = event.latitude to event.longitude
                pageCoord.value = coord
                val pushed = suppressedEcho.value
                suppressedEcho.value = null
                if (pushed == null || !coordsMatch(pushed, coord)) {
                    currentOnPointPicked?.invoke(event.latitude, event.longitude, event.address)
                }
            }

            PickerEvent.Ignored -> Unit
        }
    }

    // Native-side picks drive the page marker (echo suppressed above).
    val syncMarker = marker
    androidx.compose.runtime.LaunchedEffect(syncMarker, mapLive) {
        val webView = webViewHolder.value ?: return@LaunchedEffect
        val target = syncMarker ?: return@LaunchedEffect
        if (!mapLive) return@LaunchedEffect
        val onPage = pageCoord.value
        if (onPage != null && coordsMatch(onPage, target)) return@LaunchedEffect
        suppressedEcho.value = target
        webView.evaluateJavascript(moveMarkerJs(target.first, target.second), null)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Swift buildMapCard — 201pt tall, rounded 5, NO border.
            .height(201.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.gray200)
            .testTag("delivery-map"),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // NOTE: never make this WebView transparent — a
                    // TRANSPARENT background forces a compositing path that
                    // blanks Google-Maps tiles (white card) on real WebViews.
                    // The backend page is a Google-Maps JS app; JS is the
                    // whole point. Only our own https host is ever loaded,
                    // and the bridge exposes a single String sink.
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            // Early shim: the page loads Maps synchronously,
                            // so map-ready can fire before onPageFinished.
                            view.evaluateJavascript(DELIVERY_PICKER_WEBKIT_SHIM, null)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(DELIVERY_PICKER_WEBKIT_SHIM, null)
                            // Recover a map-ready that beat both injections.
                            view.evaluateJavascript(MAP_READY_PROBE_JS) { result ->
                                if (result?.contains("ready") == true) {
                                    onPickerEvent(PickerEvent.MapReady)
                                }
                            }
                            if (BuildConfig.DEBUG) {
                                view.postDelayed({
                                    view.evaluateJavascript(
                                        """
                                        (function(){var m=document.getElementById('map');
                                        return JSON.stringify({mapW:m&&m.offsetWidth,
                                        mapH:m&&m.offsetHeight,
                                        tiles:m?m.querySelectorAll('img').length:-1,
                                        canvases:m?m.querySelectorAll('canvas').length:-1,
                                        body:document.body.className,
                                        gm:!!(window.google&&window.google.maps)});})()
                                        """.trimIndent(),
                                    ) { snap ->
                                        android.util.Log.d("DeliveryPicker", "snapshot=$snap")
                                    }
                                }, 2500)
                            }
                        }

                        // Keep the card on the picker origin — the page has
                        // no outbound links; anything else is dropped.
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = request.url?.host != pickerHost
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun postMessage(json: String) {
                                if (BuildConfig.DEBUG) {
                                    android.util.Log.d("DeliveryPicker", "event=$json")
                                }
                                val event = parsePickerEvent(json) ?: return
                                post { onPickerEvent(event) }
                            }
                        },
                        JS_BRIDGE_NAME,
                    )
                    // Map pans/marker drags must not be stolen by the
                    // scrolling checkout column (standard map-in-scroll fix).
                    setOnTouchListener { view, _ ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    // Every real layout re-pins the map height — recovers
                    // the page whenever an early 0-height measure froze it.
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        if (v.height > 0) {
                            val cssH = (v.height / v.resources.displayMetrics.density).toInt()
                            (v as WebView).evaluateJavascript(fixMapHeightJs(cssH), null)
                        }
                    }
                    loadUrl(initialUrl)
                    webViewHolder.value = this
                }
            },
            onRelease = { webView ->
                webViewHolder.value = null
                webView.destroy()
            },
        )

        if (!mapLive) {
            // Loading / Google-script-failure fallback — the pre-map
            // stylized card (theme tokens only), flow stays functional.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.gray200),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(colors.gray200)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(colors.gray150)
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_location),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = when {
                            scriptFailed ->
                                "Map unavailable right now — search or use your " +
                                    "location to select a delivery point"

                            else -> addressLabel
                                ?: "Search or use your location to select a delivery point"
                        },
                        style = AirdropType.body3,
                        color = colors.textDescription,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                    val coord = marker ?: center
                    if (marker != null && coord != null) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                coord.first,
                                coord.second,
                            ),
                            style = AirdropType.subtitle3,
                            color = colors.textDarkTitle,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
