package com.ga.airdrop.feature.delivery

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

        // Scrollable content takes the remaining space; the Contact CTA is
        // pinned below it so it always clears the system gesture bar.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (hasActiveDelivery) {
                ActiveDeliveryCard(orderReference = orderReference!!)
            } else {
                EmptyDeliveryCard()
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        if (hasActiveDelivery) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.sm, bottom = Spacing.md)
                    .navigationBarsPadding(),
            ) {
                ContactCard(onContactUs = onContactUs)
            }
        }
    }
}

// ─── Active delivery — illustration + journey timeline ───────────────────────

@Composable
private fun ActiveDeliveryCard(orderReference: String) {
    val colors = AirdropTheme.colors
    val stages = listOf(
        DeliveryStage(
            "Order Confirmed",
            "Payment received and your order is booked.",
            DeliveryStageState.DONE,
        ),
        DeliveryStage(
            "Preparing for Dispatch",
            "Our team is packing your items for the courier.",
            DeliveryStageState.CURRENT,
        ),
        DeliveryStage(
            "Out for Delivery",
            "Your package is on its way to your address.",
            DeliveryStageState.PENDING,
        ),
        DeliveryStage(
            "Delivered",
            "Package handed over at your delivery location.",
            DeliveryStageState.PENDING,
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
            .padding(Spacing.md)
            .testTag("delivery-center-active"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Delivery illustration (1000x667) inside a soft rounded well.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.xs))
                .background(colors.gray150),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.img_delivery_deliver),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1000f / 667f),
                contentScale = ContentScale.Fit,
            )
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

        // Journey timeline.
        Column(Modifier.fillMaxWidth()) {
            stages.forEachIndexed { index, stage ->
                TimelineStep(
                    stage = stage,
                    isLast = index == stages.lastIndex,
                    connectorDone = stage.state == DeliveryStageState.DONE,
                )
            }
        }

        Text(
            text = "We'll notify you as your package moves through each stage. " +
                "Contact us any time if you need more delivery details.",
            style = AirdropType.body2,
            color = colors.textDescription,
        )
    }
}

/**
 * One timeline row: a status node + a connector down to the next node (drawn to
 * the content's intrinsic height) on the left, title + detail on the right.
 */
@Composable
private fun TimelineStep(
    stage: DeliveryStage,
    isLast: Boolean,
    connectorDone: Boolean,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Rail: node + vertical connector.
        Column(
            Modifier
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StageNode(stage.state)
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (connectorDone) colors.orangeMain else colors.iconShape),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Content — pad the bottom so consecutive rows breathe (and the
        // connector spans that gap).
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else Spacing.md),
        ) {
            Text(
                text = stage.title,
                style = AirdropType.subtitle2,
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
private fun StageNode(state: DeliveryStageState) {
    val colors = AirdropTheme.colors
    when (state) {
        DeliveryStageState.DONE -> Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.orangeMain),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(12.dp),
            )
        }
        DeliveryStageState.CURRENT -> Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.orangeMain.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.orangeMain),
            )
        }
        DeliveryStageState.PENDING -> Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.gray100)
                .border(2.dp, colors.iconShape, CircleShape),
        )
    }
}

// ─── Contact card ────────────────────────────────────────────────────────────

@Composable
private fun ContactCard(onContactUs: () -> Unit) {
    val colors = AirdropTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.orangeMain, RoundedCornerShape(Radius.xs))
            .clickable(onClick = onContactUs)
            .testTag("delivery-center-contact"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Contact us for a detailed delivery breakdown",
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
