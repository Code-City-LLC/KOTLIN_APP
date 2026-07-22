package com.ga.airdrop.feature.delivery

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.DeliveryStagePalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.repo.ActiveDelivery
import com.ga.airdrop.data.repo.TrackedDelivery
import com.ga.airdrop.data.repo.TrackedDeliveryStage
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin

object DeliveryCenterTags {
    const val ROOT = "delivery-center-root"
    const val LOADING = "delivery-center-loading"
    const val ERROR = "delivery-center-error"
    const val RETRY = "delivery-center-retry"
    const val EMPTY = "delivery-center-empty"
    const val LIST = "delivery-center-list"
    const val DETAIL = "delivery-center-detail"
    const val NO_DELIVERY = "delivery-center-no-delivery"
    const val REFRESH = "delivery-center-refresh"
    const val CONTACT = "delivery-center-contact"
    const val JOURNEY = "delivery-center-journey"
    fun row(packageId: Int) = "delivery-center-row-$packageId"
    fun stage(key: String) = "delivery-center-stage-$key"
}

/**
 * Delivery Center — the customer-facing tracking hub, in Kemar's approved
 * design language (topographic backdrop, circular hero, outlined icon nodes,
 * green passed / soft-orange current, faded upcoming, animated flow).
 *
 * Two entries share this screen:
 *  • orderReference (just paid at checkout) → the deterministic post-checkout
 *    journey. No delivery exists server-side yet, so nothing is fetched and no
 *    progress is invented beyond "confirmed + preparing".
 *  • packageId / no args → the LIVE 0/1/many state machine (Codex's plumbing):
 *    Laravel's active-deliveries list, per-package tracking detail, refresh,
 *    session-boundary hygiene. Every live stage row is the ordered server
 *    projection — the client never manufactures live progress.
 */
@Composable
fun DeliveryCenterScreen(
    orderReference: String?,
    initialPackageId: Int?,
    onBack: () -> Unit,
    onContactUs: () -> Unit,
) {
    if (initialPackageId == null && !orderReference.isNullOrBlank()) {
        JustPaidJourney(
            orderReference = orderReference,
            onBack = onBack,
            onContactUs = onContactUs,
        )
        return
    }
    LiveDeliveryCenter(
        initialPackageId = initialPackageId,
        onBack = onBack,
        onContactUs = onContactUs,
    )
}

@Composable
private fun LiveDeliveryCenter(
    initialPackageId: Int?,
    onBack: () -> Unit,
    onContactUs: () -> Unit,
    viewModel: DeliveryCenterViewModel = viewModel(
        key = "delivery-center-${initialPackageId ?: "active"}",
        factory = DeliveryCenterViewModel.factory(initialPackageId),
    ),
) {
    val state by viewModel.state.collectAsState()
    val handleBack = {
        if (!viewModel.returnToList()) onBack()
    }
    BackHandler(enabled = state.canReturnToList) { viewModel.returnToList() }

    DeliveryCenterScreenContent(
        state = state,
        onBack = handleBack,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onSelectDelivery = viewModel::selectDelivery,
        onContactUs = onContactUs,
    )
}

@Composable
internal fun DeliveryCenterScreenContent(
    state: DeliveryCenterUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onSelectDelivery: (Int) -> Unit,
    onContactUs: () -> Unit,
) {
    DeliveryCenterScaffold(onBack = onBack) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.content) {
                DeliveryCenterContent.Loading -> DeliveryLoadingState()
                DeliveryCenterContent.Error -> DeliveryErrorState(
                    message = requireNotNull(state.error),
                    onRetry = onRetry,
                    onContactUs = onContactUs,
                )
                DeliveryCenterContent.Empty -> EmptyDeliveryState(onRefresh = onRefresh)
                DeliveryCenterContent.List -> ActiveDeliveriesList(
                    deliveries = state.activeDeliveries,
                    refreshing = state.refreshing,
                    onRefresh = onRefresh,
                    onSelect = onSelectDelivery,
                )
                DeliveryCenterContent.Detail -> DeliveryDetail(
                    summary = state.selectedSummary,
                    packageId = requireNotNull(state.selectedPackageId),
                    delivery = requireNotNull(state.delivery),
                    refreshing = state.refreshing,
                    onRefresh = onRefresh,
                    onContactUs = onContactUs,
                )
                DeliveryCenterContent.NoDelivery -> NoDeliveryState(
                    packageId = requireNotNull(state.selectedPackageId),
                    onRefresh = onRefresh,
                    onContactUs = onContactUs,
                )
            }
        }
    }
}

/**
 * Shared chrome: gray200 page, the approved topographic "waves" OUTSIDE the
 * cards across the whole backdrop (8%, #292929 light / white dark), and the
 * Delivery Center header.
 */
@Composable
private fun DeliveryCenterScaffold(
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
            .clipToBounds()
            .testTag(DeliveryCenterTags.ROOT),
    ) {
        Image(
            painter = painterResource(R.drawable.img_success_bg_topo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (colors.isDark) Color.White else Color(0xFF292929),
            ),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.08f),
        )

        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(title = "Delivery Center", onBack = onBack)
            content()
        }
    }
}

// ─── Just-paid journey (deterministic; reached with an invoice ref) ──────────

private data class JourneyStage(
    val key: String,
    val label: String,
    val state: String,
    val detail: String,
)

private val JUST_PAID_STAGES = listOf(
    JourneyStage(
        "order_confirmed", "Order Confirmed", "done",
        "Payment received, order booked.",
    ),
    JourneyStage(
        "preparing_dispatch", "Preparing for Dispatch", "current",
        "Packing your items for the courier.",
    ),
    JourneyStage(
        "out_for_delivery", "Out for Delivery", "pending",
        "On its way to your address.",
    ),
    JourneyStage(
        "delivered", "Delivered", "pending",
        "Handed over at your location.",
    ),
)

/**
 * Kemar's approved FOUR-stage journey is the canonical tracking display. Live
 * server data maps onto it (assigned → Preparing for Dispatch) so the customer
 * always sees the same four-step path, with server states + timestamps driving
 * each step. Unknown server projections fall back to the raw server stages.
 */
private data class CanonicalStage(
    val key: String,
    val label: String,
    val copy: String,
    val state: String,
    val at: String?,
)

private fun canonicalJourney(delivery: TrackedDelivery): List<CanonicalStage>? {
    val byKey = delivery.stages.associateBy(TrackedDeliveryStage::key)
    val assigned = byKey["assigned"]
    val outForDelivery = byKey["out_for_delivery"]
    val delivered = byKey["delivered"]
    if (assigned == null && outForDelivery == null && delivered == null) return null
    return listOf(
        CanonicalStage(
            "order_confirmed", "Order Confirmed",
            "Payment received, order booked.", "done", null,
        ),
        CanonicalStage(
            "preparing_dispatch", "Preparing for Dispatch",
            "Packing your items for the courier.",
            assigned?.state ?: "done", assigned?.at,
        ),
        CanonicalStage(
            "out_for_delivery", "Out for Delivery",
            "On its way to your address.",
            outForDelivery?.state ?: "pending", outForDelivery?.at,
        ),
        CanonicalStage(
            "delivered", "Delivered",
            "Handed over at your location.",
            delivered?.state ?: "pending", delivered?.at,
        ),
    )
}

@Composable
private fun JustPaidJourney(
    orderReference: String,
    onBack: () -> Unit,
    onContactUs: () -> Unit,
) {
    val colors = AirdropTheme.colors
    DeliveryCenterScaffold(onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = 12.dp),
        ) {
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
                    .padding(16.dp)
                    .testTag(DeliveryCenterTags.JOURNEY),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularDeliveryHero()
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
                Column(Modifier.fillMaxWidth()) {
                    JUST_PAID_STAGES.forEachIndexed { index, stage ->
                        DeliveryTimelineStep(
                            stage = TrackedDeliveryStage(
                                key = stage.key,
                                label = stage.label,
                                state = stage.state,
                                at = null,
                            ),
                            isLast = index == JUST_PAID_STAGES.lastIndex,
                            detail = stage.detail,
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            ContactAction(onContactUs)
        }
    }
}

@Composable
private fun CircularDeliveryHero() {
    // Kemar's approved hero: the truck BIG, inside its rounded box — the soft
    // gray well hugging the full-width illustration (nothing cropped).
    val colors = AirdropTheme.colors
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
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1000f / 667f),
        )
    }
}

// ─── Live states (Codex's 0/1/many machine, approved skin) ───────────────────

@Composable
private fun DeliveryLoadingState() {
    val colors = AirdropTheme.colors
    Box(
        Modifier.fillMaxSize().testTag(DeliveryCenterTags.LOADING),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = colors.orangeMain, strokeWidth = 2.dp)
    }
}

@Composable
private fun DeliveryErrorState(
    message: String,
    onRetry: () -> Unit,
    onContactUs: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.md)
            .testTag(DeliveryCenterTags.ERROR),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Unable to load deliveries",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
        )
        OutlineButton(
            text = "Try Again",
            onClick = onRetry,
            modifier = Modifier.testTag(DeliveryCenterTags.RETRY),
        )
        ContactAction(onContactUs, Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun EmptyDeliveryState(onRefresh: () -> Unit) {
    DeliveryMessageCard(
        tag = DeliveryCenterTags.EMPTY,
        title = "No active deliveries",
        message = "When a package is assigned for delivery, its live journey will appear here.",
        onRefresh = onRefresh,
    )
}

@Composable
private fun NoDeliveryState(
    packageId: Int,
    onRefresh: () -> Unit,
    onContactUs: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DeliveryMessageCard(
            tag = DeliveryCenterTags.NO_DELIVERY,
            title = "Delivery details are not available",
            message = "Package #$packageId does not have a delivery journey to show yet.",
            onRefresh = onRefresh,
        )
        ContactAction(onContactUs, Modifier.padding(bottom = Spacing.lg))
    }
}

@Composable
private fun DeliveryMessageCard(
    tag: String,
    title: String,
    message: String,
    onRefresh: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Spacing.md)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(Radius.s),
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = 30.dp)
            .testTag(tag),
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
            text = title,
            style = AirdropType.title1,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = AirdropType.body1,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
        )
        RefreshAction(onRefresh)
    }
}

@Composable
private fun ActiveDeliveriesList(
    deliveries: List<ActiveDelivery>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(DeliveryCenterTags.LIST),
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item("delivery-list-heading") {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Active deliveries",
                        style = AirdropType.title2,
                        color = AirdropTheme.colors.textDarkTitle,
                    )
                    if (refreshing) {
                        CircularProgressIndicator(
                            color = AirdropTheme.colors.orangeMain,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        RefreshAction(onRefresh)
                    }
                }
                Text(
                    text = "Choose a package to see its live delivery journey.",
                    style = AirdropType.body2,
                    color = AirdropTheme.colors.textDescription,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        items(deliveries, key = ActiveDelivery::packageId) { delivery ->
            ActiveDeliveryRow(delivery = delivery, onClick = { onSelect(delivery.packageId) })
        }
    }
}

@Composable
private fun ActiveDeliveryRow(delivery: ActiveDelivery, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.cardHairline, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(Spacing.md)
            .testTag(DeliveryCenterTags.row(delivery.packageId)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.peachLight),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_shipments_status_in_transit_counter),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.orangeMain),
                modifier = Modifier.size(25.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = delivery.trackingCode ?: "Package #${delivery.packageId}",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            delivery.description?.let {
                Text(
                    text = it,
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = humanizeDeliveryStatus(delivery.status),
                style = AirdropType.body2.copy(fontWeight = FontWeight.SemiBold),
                color = deliveryStatusColor(delivery.status),
            )
        }
        Text(text = "›", style = AirdropType.title1, color = colors.textDescription)
    }
}

/**
 * Live tracking page in the EXACT approved journey layout (Kemar): the circular
 * hero inside the card, "Your Delivery" heading + reference, the approved
 * timeline treatment, and the contact affordance pinned above the gesture bar.
 * The stages themselves stay the ordered server projection.
 */
@Composable
private fun DeliveryDetail(
    summary: ActiveDelivery?,
    packageId: Int,
    delivery: TrackedDelivery,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onContactUs: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = 8.dp)
                .testTag(DeliveryCenterTags.DETAIL),
        ) {
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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularDeliveryHero()
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Your Delivery",
                        style = AirdropType.title2,
                        color = colors.textDarkTitle,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary?.trackingCode?.let { "Tracking #$it" }
                            ?: "Package #$packageId",
                        style = AirdropType.body2,
                        color = colors.textDescription,
                    )
                    summary?.description?.let {
                        Text(
                            text = it,
                            style = AirdropType.body2,
                            color = colors.textDescription,
                        )
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    val canonical = canonicalJourney(delivery)
                    if (canonical != null) {
                        // The approved four-stage journey, driven by live data.
                        canonical.forEachIndexed { index, stage ->
                            DeliveryTimelineStep(
                                stage = TrackedDeliveryStage(
                                    key = stage.key,
                                    label = stage.label,
                                    state = stage.state,
                                    at = stage.at,
                                ),
                                isLast = index == canonical.lastIndex,
                                detail = stage.copy,
                            )
                        }
                    } else {
                        // Defensive fallback: unrecognised server projection —
                        // render the ordered server stages verbatim.
                        delivery.stages.forEachIndexed { index, stage ->
                            DeliveryTimelineStep(
                                stage = stage,
                                isLast = index == delivery.stages.lastIndex,
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    color = colors.orangeMain,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                ContactAction(onContactUs)
            }
        }
    }
}

// ─── Timeline (shared by the live detail and the just-paid journey) ──────────

/**
 * One approved-design timeline row: outlined icon node + connector on the left,
 * label + detail on the right. Live rows show the server timestamp; the
 * just-paid journey passes its canonical copy via [detail]. Upcoming stages
 * fade back (node, icon and text together) so the front of the journey pops.
 */
@Composable
private fun DeliveryTimelineStep(
    stage: TrackedDeliveryStage,
    isLast: Boolean,
    detail: String? = null,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (stage.state == "pending") 0.4f else 1f)
            .testTag(DeliveryCenterTags.stage(stage.key)),
    ) {
        Column(
            Modifier.width(44.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DeliveryStageNode(stage)
            if (!isLast) {
                DeliveryStageConnector(
                    state = stage.state,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(top = 6.dp, bottom = if (isLast) 0.dp else 12.dp),
        ) {
            Text(
                text = stage.label,
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
            detail?.let {
                Text(
                    text = it,
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            formatDeliveryTimestamp(stage.at)?.let {
                Text(
                    text = it,
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DeliveryStageNode(stage: TrackedDeliveryStage) {
    val colors = AirdropTheme.colors
    val accent = deliveryStageColor(stage.state)
    Box(
        Modifier.size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (stage.state == "current") {
            val pulse = rememberInfiniteTransition(label = "delivery-stage-pulse")
            val scale by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.45f,
                animationSpec = infiniteRepeatable(
                    tween(1_300, easing = FastOutSlowInEasing),
                    RepeatMode.Restart,
                ),
                label = "delivery-stage-pulse-scale",
            )
            val ringAlpha by pulse.animateFloat(
                initialValue = 0.4f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    tween(1_300, easing = LinearEasing),
                    RepeatMode.Restart,
                ),
                label = "delivery-stage-pulse-alpha",
            )
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = ringAlpha
                    }
                    .border(2.dp, DeliveryStagePalette.Current, CircleShape),
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.gray100)
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(deliveryStageIcon(stage.key)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accent),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DeliveryStageConnector(state: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    val track = colors.iconShape
    val flow by rememberInfiniteTransition(label = "delivery-stage-flow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1_500, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "delivery-stage-flow-position",
    )
    Canvas(modifier.width(44.dp)) {
        val centerX = size.width / 2f
        val stroke = 2.dp.toPx()
        when (state) {
            "done" -> drawLine(
                DeliveryStagePalette.Passed,
                Offset(centerX, 0f),
                Offset(centerX, size.height),
                stroke,
                StrokeCap.Round,
            )
            "current" -> {
                drawLine(
                    track,
                    Offset(centerX, 0f),
                    Offset(centerX, size.height),
                    stroke,
                    StrokeCap.Round,
                )
                val radius = 3.5.dp.toPx()
                repeat(3) { index ->
                    val fraction = (flow + index / 3f) % 1f
                    val alpha = sin(fraction * Math.PI).toFloat().coerceIn(0.15f, 1f)
                    drawCircle(
                        color = DeliveryStagePalette.Current.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(centerX, fraction * size.height),
                    )
                }
            }
            else -> drawLine(
                track,
                Offset(centerX, 0f),
                Offset(centerX, size.height),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun deliveryStageColor(state: String): Color {
    val colors = AirdropTheme.colors
    return when (state) {
        "done" -> DeliveryStagePalette.Passed
        "current" -> DeliveryStagePalette.Current
        else -> colors.iconShape
    }
}

private fun deliveryStageIcon(key: String): Int = when (key) {
    "order_confirmed" -> R.drawable.ic_shipments_status_shipment_received
    "preparing_dispatch" -> R.drawable.ic_shipments_status_processing_warehouse
    "assigned" -> R.drawable.ic_shipments_status_shipment_received
    "out_for_delivery" -> R.drawable.ic_shipments_status_in_transit_counter
    "delivered" -> R.drawable.ic_shipments_status_delivered
    else -> R.drawable.ic_tracking
}

@Composable
private fun RefreshAction(onRefresh: () -> Unit) {
    Text(
        text = "Refresh",
        style = AirdropType.subtitle1,
        color = AirdropTheme.colors.orangeMain,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.xs))
            .clickable(onClick = onRefresh)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(DeliveryCenterTags.REFRESH),
    )
}

@Composable
private fun ContactAction(onContactUs: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.xs))
            .clickable(onClick = onContactUs)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(DeliveryCenterTags.CONTACT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_phone),
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

internal fun humanizeDeliveryStatus(status: String): String =
    status
        .trim()
        .split('_')
        .filter(String::isNotBlank)
        .joinToString(" ") { word ->
            word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }

internal fun deliveryStatusColor(status: String): Color = when (status.trim().lowercase(Locale.US)) {
    "delivered" -> DeliveryStagePalette.Passed
    "failed" -> AlertPalette.Error
    "cancelled" -> AlertPalette.Cancel
    else -> DeliveryStagePalette.Current
}

internal fun formatDeliveryTimestamp(value: String?): String? {
    val timestamp = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return runCatching {
        OffsetDateTime.parse(timestamp).format(DELIVERY_TIME_FORMATTER)
    }.getOrNull()
}

private val DELIVERY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.US)
