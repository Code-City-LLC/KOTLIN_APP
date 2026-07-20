package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropColorScheme
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * AirCoin Balance — Figma 40001911:22972 (light) / 40001911:23111 (dark),
 * exact Figma page: full-screen coin art, 1:1 conversion strip, three compact
 * balance rows, and the earn-at-counter card. The top-right document icon opens
 * the separate Figma History ledger.
 */
@Composable
fun AirCoinBalanceScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: AirCoinBalanceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    AirCoinBalanceContent(
        state = state,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
    )
}

@Composable
internal fun AirCoinBalanceContent(
    state: AirCoinBalanceUiState,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val colors = AirdropTheme.colors

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .testTag("aircoin-balance-root")
    ) {
        Image(
            painter = painterResource(R.drawable.img_homedet_aircoin_bg),
            contentDescription = null,
            // Swift .scaleAspectFill: preserve aspect + top-anchor the baked-in coin
            // cluster + crop, instead of FillBounds stretching the 1125×2436 art.
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(
                title = "AirCoin Balance",
                onBack = onBack,
                containerColor = colors.gray150,
                titleStyle = AirdropType.subtitle1,
                showDivider = false,
                trailingIconRes = R.drawable.ic_document_list,
                trailingContentDescription = "AirCoin history",
                onTrailingClick = onOpenHistory,
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(
                    Modifier
                        .height(325.dp)
                        .testTag("aircoin-balance-hero-spacer")
                )
                ConversionRow()
                Spacer(Modifier.height(20.dp))
                StatsCard(
                    accumulated = state.accumulated,
                    redeemed = state.redeemed,
                    available = state.available,
                )
                Spacer(Modifier.height(10.dp))
                TipCard()
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

// ─── "1 AirCoin → 1 USD" strip (Figma 40001911:23033) ─────────────────────

@Composable
private fun ConversionRow() {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(51.dp)
            .testTag("aircoin-balance-conversion-row"),
        // Swift equalCentering: pills pinned to the strip edges, real air around
        // the arrow — not two weighted pills fused to it.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversionPill(
            text = "1 AirCoin",
            testTag = "aircoin-balance-left-pill",
            modifier = Modifier.width(120.dp),
        )
        Box(
            modifier = Modifier
                .size(51.dp)
                .testTag("aircoin-balance-arrow-circle")
                .clip(RoundedCornerShape(100.dp))
                .background(colors.gray100)
                .border(1.dp, airCoinBorderColor(colors), RoundedCornerShape(100.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_small_arrow_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(-90f),
            )
        }
        ConversionPill(
            text = "1 USD",
            testTag = "aircoin-balance-right-pill",
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun ConversionPill(text: String, testTag: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .height(51.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.gray100)
            .border(1.dp, airCoinBorderColor(colors), RoundedCornerShape(100.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
    }
}

// ─── Stats card (Figma 40001911:23041) ─────────────────────────────────────

@Composable
private fun StatsCard(accumulated: Int, redeemed: Int, available: Int) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .testTag("aircoin-balance-stats-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, airCoinBorderColor(colors), RoundedCornerShape(Radius.s))
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatRow(
            R.drawable.img_homedet_wallet_2,
            "Accumulated AirCoin",
            accumulated,
            "aircoin-stat-accumulated",
        )
        StatRow(
            R.drawable.img_homedet_wallet_3,
            "Redeemed AirCoin",
            redeemed,
            "aircoin-stat-redeemed",
        )
        StatRow(
            R.drawable.img_homedet_wallet_4,
            "Available AirCoin",
            available,
            "aircoin-stat-available",
        )
    }
}

@Composable
private fun StatRow(iconRes: Int, label: String, amount: Int, testTag: String) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .requiredHeight(40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = label,
                style = AirdropType.body1,
                color = colors.textDarkTitle,
            )
        }
        Text(
            text = "A₡ $amount",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
    }
}

// ─── Tip card (Figma 40001911:23057) ───────────────────────────────────────

@Composable
private fun TipCard() {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(82.dp)
            .testTag("aircoin-balance-tip-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, airCoinBorderColor(colors), RoundedCornerShape(Radius.s))
            .padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.img_homedet_wallet_1),
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .testTag("aircoin-balance-tip-icon"),
        )
        Text(
            text = "Earn 0.5 AirCoin for each package collected at the counter.",
            style = AirdropType.subtitle2,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
    }
}

// ═══ History ledger — Figma 40006461:26563 (light) / 40006461:26461 (dark) ═

@Composable
fun AirCoinHistoryDetailScreen(
    onBack: () -> Unit,
    viewModel: AirCoinHistoryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    AirCoinHistoryDetailContent(
        state = state,
        onBack = onBack,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
internal fun AirCoinHistoryDetailContent(
    state: AirCoinHistoryUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = AirdropTheme.colors

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .testTag("aircoin-history-root")
    ) {
        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(
                title = "History",
                onBack = onBack,
                containerColor = colors.gray100,
                titleStyle = AirdropType.subtitle1,
                showDivider = false,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider)
            )

            val scrollState = rememberScrollState()
            LaunchedEffect(
                scrollState.value,
                scrollState.maxValue,
                state.loadingMore,
                state.endReached,
            ) {
                if (
                    scrollState.maxValue > 0 &&
                    scrollState.value >= scrollState.maxValue - 120 &&
                    !state.loadingMore &&
                    !state.endReached
                ) {
                    onLoadMore()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .testTag("aircoin-history-list"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // Swift edf2573: place the ledger 55pt into the hero instead
                    // of leaving the stale positive 20pt gap.
                    verticalArrangement = Arrangement.spacedBy((-55).dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(375f / 332f)
                            .testTag("aircoin-history-hero-wrap"),
                    ) {
                        Image(
                            painter = painterResource(
                                if (colors.isDark) {
                                    R.drawable.img_homedet_history_hero_band_dark
                                } else {
                                    R.drawable.img_homedet_history_hero_band_light
                                }
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("aircoin-history-hero-image"),
                        )
                    }
                    LedgerCard(
                        state = state,
                        modifier = Modifier.padding(horizontal = 15.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LedgerCard(state: AirCoinHistoryUiState, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .testTag("aircoin-history-table-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.cardHairline, RoundedCornerShape(Radius.s)),
    ) {
        LedgerRow(
            invoice = "Invoice No.",
            amount = "Air Coin Used",
            date = "Date",
            isHeader = true,
            modifier = Modifier.testTag("aircoin-history-header-row"),
        )
        ledgerRows(state).forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.divider)
                        .testTag("aircoin-history-divider-$index")
                )
            }
            LedgerRow(
                invoice = row.invoice,
                amount = row.amount,
                date = row.date,
                modifier = Modifier.testTag("aircoin-history-row-$index"),
            )
        }
    }
}

@Composable
private fun LedgerRow(
    invoice: String,
    amount: String,
    date: String,
    isHeader: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(if (isHeader) 43.dp else 40.dp)
            .background(if (isHeader) colors.gray200 else colors.gray100)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = if (isHeader) AirdropType.subtitle2 else AirdropType.body3
        Text(
            text = invoice,
            style = style,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Text(
            text = amount,
            style = style,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Text(
            text = date,
            style = style,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
    }
}

private data class LedgerDisplayRow(
    val invoice: String,
    val amount: String,
    val date: String,
)

private fun ledgerRows(state: AirCoinHistoryUiState): List<LedgerDisplayRow> {
    val fromTransactions = state.transactions.map { tx ->
        LedgerDisplayRow(
            invoice = tx.referenceId ?: "-",
            amount = formatCoinAmount(abs(tx.amount ?: 0.0)),
            date = formatLedgerDate(tx.createdAt),
        )
    }
    if (fromTransactions.isNotEmpty()) return fromTransactions
    return when {
        state.error != null -> listOf(LedgerDisplayRow("Unable to load", "-", state.error))
        state.loadedOnce -> listOf(LedgerDisplayRow("No transactions found", "-", "-"))
        else -> emptyList()
    }
}

@Composable
private fun airCoinBorderColor(colors: AirdropColorScheme) = colors.cardHairline

private fun formatCoinAmount(value: Double): String =
    if (abs(value - value.roundToLong()) < 0.001) {
        value.roundToLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }

private fun formatLedgerDate(raw: String?): String {
    if (raw.isNullOrEmpty()) return "-"
    val output = SimpleDateFormat("d MMM yyyy", Locale.US)
    val inputs = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (pattern in inputs) {
        runCatching {
            val parser = SimpleDateFormat(pattern, Locale.US)
            return output.format(parser.parse(raw)!!)
        }
    }
    return raw
}
