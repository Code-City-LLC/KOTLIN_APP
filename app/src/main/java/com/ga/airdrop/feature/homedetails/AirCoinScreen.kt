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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.AirCoinTransaction
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
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            // Swift: view bg gray100 under the full-screen AirCoin art.
            .background(colors.gray100)
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
                Spacer(Modifier.height(280.dp))
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
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversionPill(text = "1 AirCoin", modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(51.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(100.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_small_arrow_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(-90f),
            )
        }
        ConversionPill(text = "1 USD", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ConversionPill(text: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .height(51.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(100.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AirdropType.h6,
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
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
    ) {
        StatRow(R.drawable.img_homedet_wallet_2, "Accumulated AirCoin", accumulated)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
        StatRow(R.drawable.img_homedet_wallet_3, "Redeemed AirCoin", redeemed)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
        StatRow(R.drawable.img_homedet_wallet_4, "Available AirCoin", available)
    }
}

@Composable
private fun StatRow(iconRes: Int, label: String, amount: Int) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
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
            modifier = Modifier.size(40.dp),
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
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.transactions.size) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Swift FigmaAirCoinTransactionsViewController: plain gray100
            // page — no full-screen coin art on the ledger (Figma
            // 40006461:26563 shows only the hero illustration up top).
            .background(colors.gray100)
    ) {
        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(
                title = "History",
                onBack = onBack,
                containerColor = colors.gray100,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.lg,
                ),
            ) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(190.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.img_homedet_history_hero),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(170.dp),
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
                item(key = "tableHeader") {
                    LedgerRow(
                        invoice = "Invoice No.",
                        amount = "Air Coin Used",
                        date = "Date",
                        isHeader = true,
                        isFirst = true,
                        isLast = state.transactions.isEmpty() && !state.loading,
                    )
                }
                if (state.loading && !state.loadedOnce) {
                    item(key = "loading") {
                        LedgerMessageRow("Loading…", isLast = true)
                    }
                } else if (state.error != null && state.transactions.isEmpty()) {
                    item(key = "error") {
                        LedgerMessageRow("Unable to load — ${state.error}", isLast = true)
                    }
                } else if (state.transactions.isEmpty()) {
                    item(key = "empty") {
                        LedgerMessageRow("No transactions found", isLast = true)
                    }
                } else {
                    items(state.transactions.size, key = { "${state.transactions[it].id}-$it" }) { index ->
                        val tx = state.transactions[index]
                        LedgerRow(
                            invoice = tx.referenceId ?: "-",
                            amount = formatCoinAmount(abs(tx.amount ?: 0.0)),
                            date = formatLedgerDate(tx.createdAt),
                            credit = tx.isEarn,
                            isLast = index == state.transactions.lastIndex && !state.loadingMore,
                        )
                    }
                }
                if (state.loadingMore) {
                    item(key = "loadingMore") {
                        LedgerMessageRow("Loading…", isLast = true)
                    }
                }
            }
        }
    }
}

// Table rows share the card chrome: the first row rounds the top corners,
// the last rounds the bottom, side borders in between — one visual card.
@Composable
private fun LedgerRow(
    invoice: String,
    amount: String,
    date: String,
    isHeader: Boolean = false,
    credit: Boolean? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val colors = AirdropTheme.colors
    val shape = ledgerShape(isFirst || isHeader, isLast)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isHeader) colors.gray200 else colors.gray100)
            .border(1.dp, colors.iconShape, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val style = if (isHeader) AirdropType.subtitle2 else AirdropType.body3
        Text(
            text = invoice,
            style = style,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = if (isHeader || credit == null) amount else (if (credit) "+$amount" else "-$amount"),
            style = style,
            color = when {
                isHeader || credit == null -> colors.textDarkTitle
                credit -> AlertPalette.Completed
                else -> AlertPalette.Error
            },
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

@Composable
private fun LedgerMessageRow(message: String, isLast: Boolean) {
    val colors = AirdropTheme.colors
    val shape = ledgerShape(first = false, last = isLast)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, shape)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = AirdropType.body3,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ledgerShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 10.dp else 0.dp,
    topEnd = if (first) 10.dp else 0.dp,
    bottomStart = if (last) 10.dp else 0.dp,
    bottomEnd = if (last) 10.dp else 0.dp,
)

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
