package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
            contentScale = ContentScale.FillBounds,
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
            .width(336.dp)
            .height(51.dp)
            .testTag("aircoin-balance-conversion-row"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversionPill(
            text = "1 AirCoin",
            testTag = "aircoin-balance-left-pill",
            modifier = Modifier.weight(1f),
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
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConversionPill(text: String, testTag: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
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
            .width(336.dp)
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
            .width(336.dp)
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
            .background(colors.gray150)
            .testTag("aircoin-history-root")
    ) {
        HistoryRays(colors)

        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(
                title = "History",
                onBack = onBack,
                containerColor = if (colors.isDark) colors.gray150 else colors.glassOverlay70,
                titleStyle = AirdropType.subtitle1,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("aircoin-history-list"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(297.dp)
                        .testTag("aircoin-history-hero-wrap"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_homedet_history_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .size(287.dp)
                            .testTag("aircoin-history-hero-image"),
                    )
                }
                LedgerCard(state)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(34.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun HistoryRays(colors: AirdropColorScheme) {
    val size = if (colors.isDark) 686.dp else 652.dp
    val offsetX = if (colors.isDark) (-145).dp else (-128).dp
    val offsetY = if (colors.isDark) (-85).dp else (-72).dp
    val image = if (colors.isDark) {
        R.drawable.img_homedet_history_rays_dark
    } else {
        R.drawable.img_homedet_history_rays_light
    }
    Image(
        painter = painterResource(image),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .size(size)
            .offset(x = offsetX, y = offsetY),
    )
}

@Composable
private fun LedgerCard(state: AirCoinHistoryUiState) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .width(345.dp)
            .height(206.dp)
            .testTag("aircoin-history-table-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(historyCardBackground(colors))
            .border(1.dp, historyBorderColor(colors), RoundedCornerShape(Radius.s)),
    ) {
        LedgerRow(
            invoice = "Invoice No.",
            amount = "Air Coin Used",
            date = "Date",
            isHeader = true,
            modifier = Modifier.testTag("aircoin-history-header-row"),
        )
        ledgerRows(state).forEachIndexed { index, row ->
            LedgerRow(
                invoice = row.invoice,
                amount = row.amount,
                date = row.date,
                isLast = index == AIRCOIN_VISIBLE_HISTORY_ROW_COUNT - 1,
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
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val border = historyBorderColor(colors)
    Row(
        modifier
            .fillMaxWidth()
            .height(if (isHeader) 43.dp else 40.dp)
            .background(if (isHeader) historyHeaderBackground(colors) else historyBodyBackground(colors))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = if (isHeader) AirdropType.subtitle2 else AirdropType.body3
        Text(
            text = invoice,
            style = style,
            color = colors.textDarkTitle,
            modifier = Modifier.width(113.dp),
        )
        Text(
            text = amount,
            style = style,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = date,
            style = style,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(88.dp),
        )
    }
    if (!isLast) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(border))
    }
}

private data class LedgerDisplayRow(
    val invoice: String,
    val amount: String,
    val date: String,
)

private const val AIRCOIN_VISIBLE_HISTORY_ROW_COUNT = 4

private fun ledgerRows(state: AirCoinHistoryUiState): List<LedgerDisplayRow> {
    val fromTransactions = state.transactions.take(4).map { tx ->
        LedgerDisplayRow(
            invoice = tx.referenceId ?: "-",
            amount = formatCoinAmount(abs(tx.amount ?: 0.0)),
            date = formatLedgerDate(tx.createdAt),
        )
    }
    val fallback = when {
        state.error != null && state.transactions.isEmpty() -> LedgerDisplayRow("Unable to load", "-", state.error)
        state.transactions.isEmpty() && state.loadedOnce -> LedgerDisplayRow("-", "-", "-")
        else -> null
    }
    val rows = if (fromTransactions.isNotEmpty()) {
        fromTransactions
    } else {
        listOfNotNull(fallback)
    }
    return rows.plus(
        List((AIRCOIN_VISIBLE_HISTORY_ROW_COUNT - rows.size).coerceAtLeast(0)) {
            LedgerDisplayRow("", "", "")
        }
    ).take(AIRCOIN_VISIBLE_HISTORY_ROW_COUNT)
}

@Composable
private fun airCoinBorderColor(colors: AirdropColorScheme): Color =
    if (colors.isDark) Color(0xFF4D4D4D) else colors.iconShape

@Composable
private fun historyBorderColor(colors: AirdropColorScheme): Color =
    if (colors.isDark) Color(0xFF4C4C4C) else colors.iconShape

@Composable
private fun historyCardBackground(colors: AirdropColorScheme): Color =
    if (colors.isDark) Color(0x33383838) else colors.gray100

@Composable
private fun historyHeaderBackground(colors: AirdropColorScheme): Color =
    if (colors.isDark) colors.gray100 else colors.gray150

@Composable
private fun historyBodyBackground(colors: AirdropColorScheme): Color =
    if (colors.isDark) Color(0x662E2E2E) else colors.gray100

private fun formatCoinAmount(value: Double): String =
    if (abs(value - value.roundToLong()) < 0.001) {
        value.roundToLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }

private fun formatLedgerDate(raw: String?): String {
    if (raw.isNullOrEmpty()) return "-"
    val output = SimpleDateFormat("dMMM yyyy", Locale.US)
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
