package com.ga.airdrop.feature.calculator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Shipping Calculator — Figma nodes 40001464:29102 (Standard),
 * 40001464:30381 (SeaDrop), 40001464:30723 (Express). Behavior from
 * FigmaCalculatorViewController + RN CalculatorView:
 *  • Standard: full-width Invoice + full-width Actual Weight (Swift wins over
 *    Figma 40001464:29102's stale Select Unit / Total Weight columns)
 *  • SeaDrop:  [Invoice | length unit] + [L | W | H] + dimensions info card
 *  • Express:  [Invoice | length unit] + [Actual Weight | weight unit] + [L|W|H]
 */
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onShowResults: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    var methodPicker by remember { mutableStateOf(false) }
    var lengthUnitPicker by remember { mutableStateOf(false) }
    var weightUnitPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.navigateToResults) {
        if (state.navigateToResults) {
            viewModel.onNavigatedToResults()
            onShowResults()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .imePadding()
            .testTag("calculator-root")
    ) {
        InnerScreenHeader(title = "Shipping Calculator", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("calculator-scroll")
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Method + delivery-time info (Figma group gap 10)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CalcSelectField(
                    label = "Shipping Method",
                    value = state.method.label,
                    required = true,
                    onClick = { methodPicker = true },
                )
                BlueInfoCard(text = state.method.info)
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CalcInputField(
                    label = "Product",
                    value = state.product,
                    onValueChange = viewModel::onProductChange,
                    placeholder = "Search",
                    required = true,
                    trailing = {
                        Image(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.textDarkTitle),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                ProductResultsPanel(
                    searchState = state.searchState,
                    onSelect = viewModel::onProductSelected,
                )
            }

            CalcInputField(
                label = "Number of Packages",
                value = state.packages,
                onValueChange = viewModel::onPackagesChange,
                placeholder = "e.g. 843454534",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            // ─── Method-dependent rows ───
            when (state.method) {
                ShippingMethod.STANDARD -> {
                    InvoiceField(
                        value = state.invoiceUsd,
                        onValueChange = viewModel::onInvoiceChange,
                        modifier = Modifier.testTag("calculator-invoice-field"),
                    )
                    CalcInputField(
                        label = "Actual Weight (lbs)",
                        value = state.actualWeight,
                        onValueChange = viewModel::onActualWeightChange,
                        placeholder = "e.g. 843",
                        required = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("calculator-actual-weight-field"),
                    )
                }

                ShippingMethod.SEADROP -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        InvoiceField(state.invoiceUsd, viewModel::onInvoiceChange, Modifier.weight(1f))
                        CalcSelectField(
                            label = "Select Unit",
                            value = state.lengthUnit.label,
                            required = true,
                            placeholder = "eg: Inch",
                            onClick = { lengthUnitPicker = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DimensionsRow(state, viewModel)
                    PackageDimensionsCard()
                }

                ShippingMethod.EXPRESS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        InvoiceField(state.invoiceUsd, viewModel::onInvoiceChange, Modifier.weight(1f))
                        CalcSelectField(
                            label = "Select Unit",
                            value = state.lengthUnit.label,
                            required = true,
                            placeholder = "eg: Inch",
                            onClick = { lengthUnitPicker = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CalcInputField(
                            label = "Actual Weight (lbs)", // Swift FigmaCalculatorViewController.swift:198
                            value = state.actualWeight,
                            onValueChange = viewModel::onActualWeightChange,
                            placeholder = "e.g. 843",
                            required = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        CalcSelectField(
                            label = "Select Unit",
                            value = state.weightUnit.label,
                            required = true,
                            placeholder = "eg: lbs",
                            onClick = { weightUnitPicker = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DimensionsRow(state, viewModel)
                    PackageDimensionsCard()
                }
            }

            CalculatorPrimaryButton(
                text = if (state.calculating) "Calculating..." else "Calculate",
                loading = state.calculating,
                onClick = viewModel::calculate,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .testTag("calculator-calculate-button"),
            )
        }
    }

    if (methodPicker) {
        OptionPickerSheet(
            options = ShippingMethod.entries.map { it.label },
            selected = state.method.label,
            onSelect = { label ->
                ShippingMethod.entries.firstOrNull { it.label == label }
                    ?.let(viewModel::onMethodSelected)
            },
            onDismiss = { methodPicker = false },
        )
    }
    if (lengthUnitPicker) {
        OptionPickerSheet(
            options = LengthUnit.entries.map { it.label },
            selected = state.lengthUnit.label,
            onSelect = { label ->
                LengthUnit.entries.firstOrNull { it.label == label }
                    ?.let(viewModel::onLengthUnitSelected)
            },
            onDismiss = { lengthUnitPicker = false },
        )
    }
    if (weightUnitPicker) {
        OptionPickerSheet(
            options = WeightUnit.entries.map { it.label },
            selected = state.weightUnit.label,
            onSelect = { label ->
                WeightUnit.entries.firstOrNull { it.label == label }
                    ?.let(viewModel::onWeightUnitSelected)
            },
            onDismiss = { weightUnitPicker = false },
        )
    }

    state.alert?.let { alert ->
        SimpleAlertDialog(title = alert.title, message = alert.message, onDismiss = viewModel::dismissAlert)
    }
}

@Composable
private fun CalculatorPrimaryButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(Radius.xs))
            // Swift setLoading (FigmaCalculatorViewController.swift:855-859): dim to
            // 0.7 alpha + swap title to "Calculating..." — NO spinner.
            .background(BrandPalette.OrangeMain.copy(alpha = if (loading) 0.7f else 1f))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) "Calculating..." else text,
            style = AirdropType.button,
            color = BrandPalette.White,
        )
    }
}

@Composable
private fun InvoiceField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    CalcInputField(
        label = "Invoice Amount USD",
        value = value,
        onValueChange = onValueChange,
        placeholder = "e.g. 843",
        required = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailing = { DollarTrailing() },
        modifier = modifier,
    )
}

@Composable
private fun DimensionsRow(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CalcInputField(
            label = "Length",
            value = state.length,
            onValueChange = viewModel::onLengthChange,
            placeholder = "e.g. 843",
            required = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        CalcInputField(
            label = "Width",
            value = state.width,
            onValueChange = viewModel::onWidthChange,
            placeholder = "e.g. 843",
            required = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        CalcInputField(
            label = "Height",
            value = state.height,
            onValueChange = viewModel::onHeightChange,
            placeholder = "e.g. 843",
            required = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PackageDimensionsCard() {
    val colors = AirdropTheme.colors
    BlueInfoCard(
        title = "Package Dimensions",
        text = {
            Text(
                text = "Can be found in the product description of your online purchases. " +
                    "Please note that all packages will be measured by the package dimensions, " +
                    "not item dimensions.",
                style = AirdropType.body2,
                color = colors.textDarkTitle,
            )
        },
    )
}

/**
 * Product search dropdown — Swift renderProductResults: bordered panel,
 * "N results found" header on gray150, 62dp rows (shop icon bubble + title +
 * price), dividers between rows.
 */
@Composable
private fun ProductResultsPanel(
    searchState: ProductSearchState,
    onSelect: (CalcProduct) -> Unit,
) {
    if (searchState is ProductSearchState.Hidden) return
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs)),
    ) {
        when (searchState) {
            is ProductSearchState.Loading -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BrandPalette.OrangeMain,
                        strokeWidth = 2.dp,
                    )
                    Text(text = "Searching...", style = AirdropType.body2, color = colors.textDescription)
                }
            }

            is ProductSearchState.Results -> {
                val products = searchState.products
                if (products.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "No products found", style = AirdropType.body2, color = colors.textDescription)
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(colors.gray150),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${products.size} result${if (products.size == 1) "" else "s"} found",
                            style = AirdropType.subtitle3,
                            color = colors.textDescription,
                            textAlign = TextAlign.Center,
                        )
                    }
                    products.forEachIndexed { index, product ->
                        ProductResultRow(product = product, onClick = { onSelect(product) })
                        if (index < products.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 60.dp)
                                    .height(1.dp)
                                    .background(colors.iconShape)
                            )
                        }
                    }
                }
            }

            ProductSearchState.Hidden -> Unit
        }
    }
}

@Composable
private fun ProductResultRow(product: CalcProduct, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(colors.gray150, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_shop),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textDescription),
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = product.title,
                style = AirdropType.body2,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = product.displayPrice,
                style = AirdropType.subtitle3,
                color = colors.textDescription,
                maxLines = 1,
            )
        }
    }
}
