package com.ga.airdrop.feature.delivery

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader

/**
 * Delivery Center — the customer-facing tracking hub for orders shipped to the
 * door (delivery fulfilment). Reached from the Home "Delivery Center" tile and
 * from the pickup/delivery payment-success "Track your package" action.
 *
 * This is the Kotlin reference UI for the shared delivery-tracking system being
 * built with Swift (parity) and Laravel (the tracking API: order_id → stage +
 * timestamps + optional courier/ETA). Until that API lands, the timeline
 * reflects the deterministic post-checkout journey — a delivery order is
 * Confirmed and being Prepared, with the door-delivery stages shown as upcoming
 * and a "contact us" path for live details.
 */

private enum class DeliveryStageState { DONE, CURRENT, PENDING }

private data class DeliveryStage(
    val title: String,
    val detail: String,
    val state: DeliveryStageState,
    val iconRes: Int,
)

@Composable
fun DeliveryCenterScreen(
    orderReference: String?,
    onBack: () -> Unit,
    onContactUs: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val hasActiveDelivery = !orderReference.isNullOrBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
            .testTag("delivery-center-root"),
    ) {
        HomeDetailsHeader(title = "Delivery Center", onBack = onBack)

        // Content sized to fit one frame (no scroll on a normal phone);
        // verticalScroll stays only as a safety net for very short screens.
        // The Contact CTA is pinned low, just above the gesture bar.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = 12.dp),
        ) {
            if (hasActiveDelivery) {
                ActiveDeliveryCard(orderReference = orderReference!!)
            } else {
                EmptyDeliveryCard()
            }
        }

        if (hasActiveDelivery) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 10.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                ContactAction(onContactUs = onContactUs)
            }
        }
    }
}

// ─── Active delivery — illustration + journey timeline ───────────────────────

@Composable
private fun ActiveDeliveryCard(orderReference: String) {
    val colors = AirdropTheme.colors
    // Stage icons reuse the Figma-exported shipment-status set so the timeline
    // speaks the same visual language as the rest of the app.
    val stages = listOf(
        DeliveryStage(
            "Order Confirmed",
            "Payment received, order booked.",
            DeliveryStageState.DONE,
            R.drawable.ic_shipments_status_shipment_received,
        ),
        DeliveryStage(
            "Preparing for Dispatch",
            "Packing your items for the courier.",
            DeliveryStageState.CURRENT,
            R.drawable.ic_shipments_status_processing_warehouse,
        ),
        DeliveryStage(
            "Out for Delivery",
            "On its way to your address.",
            DeliveryStageState.PENDING,
            R.drawable.ic_shipments_status_in_transit_counter,
        ),
        DeliveryStage(
            "Delivered",
            "Handed over at your location.",
            DeliveryStageState.PENDING,
            R.drawable.ic_shipments_status_delivered,
        ),
    )

    Column(
        Modifier
            .fillMaxWidth()
            // Drop shadow underneath the card (0/12 @10%), theme-neutral.
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(Radius.s),
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(16.dp)
            .testTag("delivery-center-active"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Delivery illustration (1000x667) inside a soft rounded well. A blurred,
        // darkened copy offset downward sits behind it as a soft drop shadow so
        // the truck lifts off the surface.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.xs))
                .background(colors.gray150)
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1000f / 667f),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_delivery_deliver),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.20f)),
                    modifier = Modifier
                        .matchParentSize()
                        .offset(y = 7.dp)
                        .blur(13.dp),
                )
                Image(
                    painter = painterResource(R.drawable.img_delivery_deliver),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        // Order heading — the stage below carries the live status, so no pill.
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Your Delivery",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = orderReference,
                style = AirdropType.body2,
                color = colors.textDescription,
            )
        }

        // Journey timeline — icon nodes joined by connectors; the segment
        // leaving the current stage animates a downward flow, showing the
        // package moving in that direction.
        Column(Modifier.fillMaxWidth()) {
            stages.forEachIndexed { index, stage ->
                TimelineStep(
                    stage = stage,
                    isLast = index == stages.lastIndex,
                )
            }
        }

        Text(
            text = "We'll notify you at each stage of your package's journey.",
            style = AirdropType.body2,
            color = colors.textDescription,
        )
    }
}

private val NODE_SIZE = 44.dp

// Timeline stage palette (shared with Swift for parity). Nodes are OUTLINED (no
// solid fill) for a chic, understated look:
//   • passed stage  → green
//   • current stage → a softened orange (the brand #F15114 reads too bright here)
//   • pending stage → grey (theme iconShape / textDescription)
private val StagePassedGreen = Color(0xFF2E9E5B)
private val StageCurrentOrange = Color(0xFFE06B3E)

/**
 * One timeline row: an icon node + a connector down to the next node (drawn to
 * the content's intrinsic height) on the left, title + detail on the right.
 * The connector leaving the CURRENT stage animates a downward flow.
 */
@Composable
private fun TimelineStep(
    stage: DeliveryStage,
    isLast: Boolean,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Rail: icon node + vertical connector.
        Column(
            Modifier
                .width(NODE_SIZE)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StageNode(stage)
            if (!isLast) {
                Connector(
                    modifier = Modifier.weight(1f),
                    // Segment leaving a DONE stage is solid orange; leaving the
                    // CURRENT stage flows; ahead of the front it is grey.
                    mode = when (stage.state) {
                        DeliveryStageState.DONE -> ConnectorMode.DONE
                        DeliveryStageState.CURRENT -> ConnectorMode.FLOWING
                        DeliveryStageState.PENDING -> ConnectorMode.PENDING
                    },
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Content — top padding aligns the title with the node centre; bottom
        // padding gives consecutive rows breathing room (the connector spans it).
        Column(
            Modifier
                .weight(1f)
                .padding(top = 8.dp, bottom = if (isLast) 0.dp else 12.dp),
        ) {
            Text(
                text = stage.title,
                style = AirdropType.subtitle1,
                color = if (stage.state == DeliveryStageState.PENDING) {
                    colors.textDescription
                } else {
                    colors.textDarkTitle
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stage.detail,
                style = AirdropType.body2,
                color = colors.textDescription,
            )
        }
    }
}

@Composable
private fun StageNode(stage: DeliveryStage) {
    val colors = AirdropTheme.colors
    // Outlined nodes — ring + icon carry the colour, the inside stays empty.
    val accent = when (stage.state) {
        DeliveryStageState.DONE -> StagePassedGreen
        DeliveryStageState.CURRENT -> StageCurrentOrange
        DeliveryStageState.PENDING -> colors.iconShape
    }
    val iconColor = when (stage.state) {
        DeliveryStageState.DONE -> StagePassedGreen
        DeliveryStageState.CURRENT -> StageCurrentOrange
        DeliveryStageState.PENDING -> colors.textDescription
    }
    Box(
        Modifier.size(NODE_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        // A soft pulsing ring radiates from the current stage.
        if (stage.state == DeliveryStageState.CURRENT) {
            val pulse = rememberInfiniteTransition(label = "node-pulse")
            val scale by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.45f,
                animationSpec = infiniteRepeatable(
                    tween(1300, easing = FastOutSlowInEasing),
                    RepeatMode.Restart,
                ),
                label = "pulse-scale",
            )
            val ringAlpha by pulse.animateFloat(
                initialValue = 0.4f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    tween(1300, easing = LinearEasing),
                    RepeatMode.Restart,
                ),
                label = "pulse-alpha",
            )
            Box(
                Modifier
                    .size(NODE_SIZE)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = ringAlpha
                    }
                    .border(2.dp, StageCurrentOrange, CircleShape),
            )
        }
        // Outlined badge — the inside stays the card surface (no fill).
        Box(
            Modifier
                .size(NODE_SIZE)
                .clip(CircleShape)
                .background(colors.gray100)
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(stage.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(iconColor),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private enum class ConnectorMode { DONE, FLOWING, PENDING }

/**
 * Vertical connector between two nodes. DONE = solid orange, PENDING = solid
 * grey, FLOWING = grey track with orange dots streaming downward (the package
 * is moving toward the next stage).
 */
@Composable
private fun Connector(modifier: Modifier, mode: ConnectorMode) {
    val colors = AirdropTheme.colors
    val track = colors.iconShape
    val flow by rememberInfiniteTransition(label = "flow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "flow-pos",
    )
    Canvas(modifier.width(NODE_SIZE)) {
        val cx = size.width / 2f
        val lineW = 2.dp.toPx()
        when (mode) {
            // Behind the front = passed = green.
            ConnectorMode.DONE -> drawLine(
                StagePassedGreen, Offset(cx, 0f), Offset(cx, size.height), lineW, StrokeCap.Round,
            )
            ConnectorMode.PENDING -> drawLine(
                track, Offset(cx, 0f), Offset(cx, size.height), lineW, StrokeCap.Round,
            )
            ConnectorMode.FLOWING -> {
                drawLine(track, Offset(cx, 0f), Offset(cx, size.height), lineW, StrokeCap.Round)
                val r = 3.5.dp.toPx()
                repeat(3) { k ->
                    val frac = (flow + k / 3f) % 1f
                    val y = frac * size.height
                    // Fade the dots in and out at the ends of the segment.
                    val a = sin(frac * Math.PI).toFloat().coerceIn(0.15f, 1f)
                    drawCircle(StageCurrentOrange.copy(alpha = a), r, Offset(cx, y))
                }
            }
        }
    }
}

// ─── Contact affordance ──────────────────────────────────────────────────────

/** Compact icon + label (no heavy button) — taps through to live chat. */
@Composable
private fun ContactAction(onContactUs: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.xs))
            .clickable(onClick = onContactUs)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("delivery-center-contact"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_phone_outline),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.orangeMain),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "Contact us for more information",
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
        )
    }
}

// ─── Empty state — reached from Home with no active delivery ──────────────────

@Composable
private fun EmptyDeliveryCard() {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(Radius.s),
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = 30.dp)
            .testTag("delivery-center-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.img_delivery_deliver),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(1000f / 667f),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "No active deliveries",
            style = AirdropType.title1,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "When you choose delivery at checkout, you'll be able to " +
                "track your package's journey right here.",
            style = AirdropType.body1,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
        )
    }
}
