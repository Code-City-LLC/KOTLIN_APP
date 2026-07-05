package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * AirCoin Balance — Figma 40001911:22972 (light) / 40001911:23111 (dark),
 * behavior from FigmaAirCoinHistoryViewController + RN AirCoinView: the
 * AirCoin.png sunburst/coins art is a full-screen background, content scrolls
 * over it (conversion pills, stats card, tip card); the top-right document
 * icon opens the transaction history ledger.
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
            // Swift: view bg gray100 under the full-screen AirCoin art.
            .background(colors.gray100)
            .testTag("aircoin-balance-root")
    ) {
        Image(
            painter = painterResource(R.drawable.img_homedet_aircoin_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(
                title = "AirCoin Balance",
                onBack = onBack,
                // Swift makeHeader: OPAQUE gray100 (not a translucent wash).
                containerColor = colors.gray100,
                titleStyle = AirdropType.subtitle1,
                trailingIconRes = R.drawable.ic_document_list,
                trailingContentDescription = "AirCoin history",
                onTrailingClick = onOpenHistory,
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Clearance so the baked-in coin cluster shows (RN paddingTop ≈ 300).
                Spacer(
                    Modifier
                        .height(280.dp)
                        .testTag("aircoin-balance-hero-spacer")
                )
                ConversionRow()
                StatsCard(
                    accumulated = state.accumulated,
                    redeemed = state.redeemed,
                    available = state.available,
                )
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
            .testTag("aircoin-balance-conversion-row"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversionPill(text = "1 AirCoin", testTag = "aircoin-balance-left-pill")
        Spacer(Modifier.width(16.dp))
        Image(
            painter = painterResource(R.drawable.ic_small_arrow_down),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier
                .size(24.dp)
                .rotate(-90f),
        )
        Spacer(Modifier.width(16.dp))
        ConversionPill(text = "1 USD", testTag = "aircoin-balance-right-pill")
    }
}

@Composable
private fun ConversionPill(text: String, testTag: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .width(120.dp)
            .height(44.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(22.dp)),
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
    // Swift makeStatsCard: rows 64pt with full-width 1pt iconShape dividers
    // between (stack spacing 0, rows carry their own 16pt side insets).
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("aircoin-balance-stats-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
    ) {
        StatRow(
            R.drawable.img_homedet_wallet_2,
            "Accumulated AirCoin",
            accumulated,
            "aircoin-stat-accumulated",
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
        StatRow(
            R.drawable.img_homedet_wallet_3,
            "Redeemed AirCoin",
            redeemed,
            "aircoin-stat-redeemed",
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
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
            // Swift makeStatRow: 64pt row, img 40 at leading 16, label +12,
            // amount trailing -16.
            .height(64.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    // Swift makeTipCard: 40pt icon at leading 16, Body2 label with 16pt
    // vertical padding — card height wraps the label.
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("aircoin-balance-tip-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.img_homedet_wallet_1),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .testTag("aircoin-balance-tip-icon"),
        )
        Text(
            text = "Earn 0.5 AirCoin for each package collected at the counter.",
            style = AirdropType.body2,
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

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.transactions.size) {
        if (shouldLoadMore) onLoadMore()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Swift FigmaAirCoinTransactionsViewController: plain gray100
            // page — no full-screen coin art on the ledger (Figma
            // 40006461:26563 shows only the hero illustration up top).
            .background(colors.gray100)
            .testTag("aircoin-history-root")
    ) {
        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(
                title = "History",
                onBack = onBack,
                containerColor = colors.gray100,
                titleStyle = AirdropType.subtitle1,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("aircoin-history-list"),
                contentPadding = PaddingValues(
                    start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.lg,
                ),
            ) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .testTag("aircoin-history-hero-wrap"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.img_homedet_history_hero),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(150.dp)
                                    .testTag("aircoin-history-hero-image"),
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
                item(key = "table") {
                    LedgerCard(state)
                }
                if ((state.loading && !state.loadedOnce) || state.loadingMore) {
                    item(key = "spinner") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.md)
                                .testTag("aircoin-history-spinner"),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = BrandPalette.OrangeMain)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerCard(state: AirCoinHistoryUiState) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("aircoin-history-table-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
    ) {
        LedgerRow(
            invoice = "Invoice No",
            amount = "Air Coin Used",
            date = "Used Date",
            isHeader = true,
            modifier = Modifier.testTag("aircoin-history-header-row"),
        )
        when {
            state.loading && !state.loadedOnce -> Unit
            state.error != null && state.transactions.isEmpty() -> {
                LedgerRow(
                    invoice = "Unable to load",
                    amount = "-",
                    date = state.error,
                    modifier = Modifier.testTag("aircoin-history-error-row"),
                )
            }
            state.transactions.isEmpty() -> {
                LedgerRow(
                    invoice = "No transactions found",
                    amount = "-",
                    date = "-",
                    modifier = Modifier.testTag("aircoin-history-empty-row"),
                )
            }
            else -> {
                state.transactions.forEachIndexed { index, tx ->
                    LedgerRow(
                        invoice = tx.referenceId ?: "-",
                        amount = formatCoinAmount(abs(tx.amount ?: 0.0)),
                        date = formatLedgerDate(tx.createdAt),
                        modifier = Modifier.testTag("aircoin-history-row-$index"),
                    )
                }
            }
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
            .background(if (isHeader) colors.gray200 else colors.gray100)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val style = if (isHeader) AirdropType.subtitle2 else AirdropType.body3
        Text(
            text = invoice,
            style = style,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = style,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = date,
            style = style,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

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
