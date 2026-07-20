package com.ga.airdrop.feature.delivery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Map card for the Delivery Method screen — Swift buildMapCard parity slot
 * (335×201, rounded 5, no border; Figma 40008740:28298).
 *
 * Renders a real, interactive OpenStreetMap (osmdroid) map — keyless, so it
 * has no Google Cloud dependency (the provisioned Google key has the Maps
 * Static API disabled at the project level and we cannot enable it). The
 * signature is deliberately GoogleMap-swap-ready: swap the AndroidView body
 * to Google Maps Compose behind this exact API once a MAPS_API_KEY is
 * provisioned for the Android SDK.
 *
 * [center] → initial camera, [marker] → draggable-style pin, [addressLabel] →
 * marker title, [onPointPicked] → single-tap on the map (reverse-geocode +
 * fee validation in the ViewModel). All heavy geocoding is server-side, so
 * the map is a selection aid, not the source of truth.
 */
@Composable
fun DeliveryMapView(
    center: Pair<Double, Double>?,
    marker: Pair<Double, Double>?,
    addressLabel: String?,
    onPointPicked: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // osmdroid needs a non-default User-Agent (OSM tile servers reject the
    // default) and a writable cache; pin both before the first tile request.
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = File(context.cacheDir, "osmdroid-tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }

    // MapView is a plain Android View — drive its lifecycle off the composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxWidth()
            // Swift buildMapCard — 201pt tall, rounded 5, NO border.
            .height(201.dp)
            .clip(RoundedCornerShape(5.dp))
            .testTag("delivery-map"),
        update = { mv ->
            val coord = marker ?: center
            if (coord != null) {
                mv.controller.setZoom(if (marker != null) 15.0 else 8.5)
                mv.controller.setCenter(GeoPoint(coord.first, coord.second))
            }

            // Refresh the selected-point pin.
            mv.overlays.removeAll { it is Marker }
            if (marker != null) {
                mv.overlays.add(
                    Marker(mv).apply {
                        position = GeoPoint(marker.first, marker.second)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = addressLabel
                        setInfoWindow(null)
                    },
                )
            }

            // Single-tap → pick a delivery point (server reverse-geocode + fee).
            mv.overlays.removeAll { it is MapEventsOverlay }
            if (onPointPicked != null) {
                mv.overlays.add(
                    0,
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                if (p != null) onPointPicked(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        },
                    ),
                )
            }
            mv.invalidate()
        },
    )
}
