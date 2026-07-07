package com.ga.airdrop.feature.delivery

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import java.util.Locale

/**
 * Map card for the Delivery Method screen — Swift buildMapCard parity slot
 * (335×201, rounded 5, no border; Figma 40008740:28298).
 *
 * Phase-1 static — swap to Google Maps Compose behind this exact signature
 * once a MAPS_API_KEY is provisioned (Kemar follow-up); all geocoding is
 * server-side so the flow is fully functional without the interactive map.
 *
 * The GoogleMap swap maps 1:1 onto these parameters:
 * [center] → rememberCameraPositionState, [marker] → Marker(draggable),
 * [addressLabel] → marker title, [onPointPicked] → onMapClick + drag-end.
 * In Phase-1 the card renders a stylized theme-colored placeholder (pin +
 * selected address) and [onPointPicked] is intentionally not invoked — the
 * user selects a point via search or Use Current Location instead.
 */
@Composable
fun DeliveryMapView(
    center: Pair<Double, Double>?,
    marker: Pair<Double, Double>?,
    addressLabel: String?,
    onPointPicked: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    // Phase-1: onPointPicked/center are part of the stable GoogleMap-ready
    // API but unused by the static card.
    @Suppress("UNUSED_EXPRESSION")
    onPointPicked

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
        // Faint horizon band — keeps the card reading as a "map" surface
        // rather than an empty box, using only theme tokens.
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
                text = addressLabel
                    ?: "Search or use your location to select a delivery point",
                style = AirdropType.body3,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            val coord = marker ?: center
            if (marker != null && coord != null) {
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f", coord.first, coord.second),
                    style = AirdropType.subtitle3,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
