package com.ga.airdrop.feature.delivery

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.infoBoxBackground
import com.ga.airdrop.core.designsystem.theme.infoBoxBorder
import com.ga.airdrop.data.model.DeliveryWarehouse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.shop.ShopInnerHeader
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Delivery Method — Figma 40008740:28263, behavior from
 * FigmaDeliveryMethodViewController (docs/PARITY_GAP_SPECS.md §2). Reached
 * from My Cart "Choose Delivery"; the currency popup routes JMD to Profile
 * Information and USD to Order Summary without dispatching payment.
 *
 * LOCATION: "Use Current Location" is fully wired (runtime permission +
 * FusedLocationProvider); the interactive map is a real OpenStreetMap
 * (osmdroid, keyless) — tap to pick a point (see [DeliveryMapView]).
 */
@Composable
fun DeliveryMethodScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: DeliveryMethodViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Belt-and-braces: the currency step reads CartStore.items.
        CartStore.init(context)
        viewModel.deviceGeocoder = { latitude, longitude ->
            withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                        ?.firstOrNull()
                        ?.let { placemark ->
                            formatDevicePlaceName(
                                placemark.featureName,
                                placemark.locality,
                                placemark.adminArea,
                                placemark.countryName,
                            )
                        }
                }.getOrNull()
            }
        }
    }

    // "Use Current Location" — Swift onUseCurrentLocation via
    // FusedLocationProvider (keyless play-services-location, spec §6).
    // Denied / no fix → onUseCurrentLocation(null) → the Swift-parity alert.
    fun fetchLocation() {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onUseCurrentLocation(null)
            return
        }
        runCatching {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    viewModel.onUseCurrentLocation(loc?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { viewModel.onUseCurrentLocation(null) }
        }.onFailure { viewModel.onUseCurrentLocation(null) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) fetchLocation() else viewModel.onUseCurrentLocation(null)
    }

    fun requestCurrentLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            fetchLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    val navTarget = state.navTarget
    LaunchedEffect(navTarget) {
        if (navTarget != null) {
            onNavigate(navTarget)
            viewModel.consumeNav()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // Swift viewDidLoad — page bg gray150.
            .background(colors.gray150)
    ) {
        ShopInnerHeader(title = "Delivery Method", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "How would you like to receive your packages?",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
            )

            // ─── Mode tiles — Swift makeTilesRow (104pt, spacing 12) ───
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeTile(
                    title = "Pickup",
                    subtitle = "Collect from warehouse",
                    iconRes = R.drawable.img_delivery_pickup,
                    selected = state.mode == DeliveryMode.Pickup,
                    onClick = { viewModel.onModeSelected(DeliveryMode.Pickup) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delivery-tile-pickup"),
                )
                ModeTile(
                    title = "Delivery",
                    subtitle = "Deliver to your location",
                    iconRes = R.drawable.img_delivery_deliver,
                    selected = state.mode == DeliveryMode.Delivery,
                    onClick = { viewModel.onModeSelected(DeliveryMode.Delivery) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delivery-tile-delivery"),
                )
            }

            when (state.mode) {
                DeliveryMode.Pickup -> PickupSection(
                    warehouses = state.warehouses,
                    selectedWarehouseId = state.selectedWarehouseId,
                    onWarehouseSelected = viewModel::onWarehouseSelected,
                )

                DeliveryMode.Delivery -> DeliverySection(
                    state = state,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onSubmitSearch = viewModel::onSubmitSearch,
                    onSearchResultPicked = viewModel::onSearchResultPicked,
                    onMapPointPicked = viewModel::onMapPointPicked,
                    onUseCurrentLocation = { requestCurrentLocation() },
                )
            }
        }

        // ─── Bottom CTA — Swift bottomBar "Choose Currency" gradient ───
        Column(Modifier.fillMaxWidth().background(colors.gray150)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
            GradientButton(
                text = "Choose Currency",
                onClick = viewModel::onContinue,
                loading = state.ctaState != DeliveryCtaState.Idle,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
                    .navigationBarsPadding()
                    .testTag("delivery-cta"),
            )
        }
    }

    if (state.showCurrencyPopup) {
        CurrencyChoicePopup(
            onPick = viewModel::onCurrencyChosen,
            onDismiss = viewModel::dismissCurrencyPopup,
        )
    }

    val errorTitle = state.errorTitle
    if (errorTitle != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            containerColor = colors.gray100,
            title = {
                Text(text = errorTitle, style = AirdropType.title2, color = colors.textDarkTitle)
            },
            text = {
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(text = "OK", style = AirdropType.button, color = BrandPalette.OrangeMain)
                }
            },
        )
    }
}

/** Figma light-mode peach — selected borders (Swift styleModeCard #F1855C). */
private val PeachBorder = Color(0xFFF1855C)

/* ─── Mode tile ─────────────────────────────────────────────────────────── */

@Composable
private fun ModeTile(
    title: String,
    subtitle: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    // Swift styleModeCard: selected = peach 1.5pt border + orange tint bg
    // (10% dark / 6% light); unselected = 1pt hairline on gray100.
    val background = if (selected) {
        BrandPalette.OrangeMain.copy(alpha = if (colors.isDark) 0.10f else 0.06f)
    } else {
        colors.gray100
    }
    Row(
        modifier = modifier
            .height(104.dp)
            .background(background, RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) PeachBorder else colors.iconShape,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(58.dp)
                .background(colors.gray150, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Figma 40008740:28273/28279 — photographic pickup/delivery
            // illustrations rendered untinted (Swift uses .alwaysOriginal),
            // not orange-tinted line icons.
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(46.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle)
            Text(
                // Swift tile subtitle — Cairo Regular 11.
                text = subtitle,
                style = AirdropType.body3.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.textDescription,
                maxLines = 2,
            )
        }
    }
}

/* ─── Section header with required asterisk (Swift requiredHeader) ─────── */

@Composable
private fun RequiredHeader(text: String) {
    val colors = AirdropTheme.colors
    Text(
        text = buildAnnotatedString {
            append(text)
            // Figma #D92A2A red asterisk.
            withStyle(SpanStyle(color = AlertPalette.Error)) { append("*") }
        },
        style = AirdropType.title2,
        color = colors.textDarkTitle,
    )
}

/* ─── Pickup section ───────────────────────────────────────────────────── */

@Composable
private fun PickupSection(
    warehouses: List<DeliveryWarehouse>,
    selectedWarehouseId: Int?,
    onWarehouseSelected: (DeliveryWarehouse) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Swift parity: trailing space before the red asterisk.
        RequiredHeader("Select Pickup Location ")
        warehouses.forEach { warehouse ->
            WarehouseRow(
                warehouse = warehouse,
                selected = warehouse.id != null && warehouse.id == selectedWarehouseId,
                onClick = { onWarehouseSelected(warehouse) },
            )
        }
    }
}

@Composable
private fun WarehouseRow(
    warehouse: DeliveryWarehouse,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    // Swift makeWarehouseRow — transparent row on the gray150 page, 1dp
    // border (#f1855c selected / iconShape unselected), min height 88.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .border(
                1.dp,
                if (selected) PeachBorder else colors.iconShape,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 15.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .testTag("delivery-warehouse-${warehouse.id ?: 0}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Radio 29dp — selected: SOLID orange outer + white 10dp inner dot;
        // unselected: gray outline circle (Figma 40008740:28258-28261).
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(29.dp)
                .background(
                    if (selected) BrandPalette.ButtonStatic else Color.Transparent,
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (selected) BrandPalette.OrangeMain else colors.iconShape,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(BrandPalette.White, CircleShape)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = warehouse.name ?: "Warehouse",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
            Text(
                text = warehouse.address.orEmpty(),
                style = AirdropType.subtitle2,
                color = colors.textDescription,
            )
        }
    }
}

/* ─── Delivery section ─────────────────────────────────────────────────── */

@Composable
internal fun DeliverySection(
    state: DeliveryUiState,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onSearchResultPicked: (PlaceResult) -> Unit,
    onMapPointPicked: (Double, Double) -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    val colors = AirdropTheme.colors
    // Swift deliverySectionStack spacing 12.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RequiredHeader("Select Delivery Location ")

        Text(
            // Swift supportedParishesLabel — 17 parishes − 4 shown = 13 more.
            text = "Service areas: Kingston, Montego Bay, Ocho Rios, Portmore + 13 more",
            style = AirdropType.body3,
            color = colors.textDescription,
        )

        // Search row header + right-aligned "Use Current Location" pill.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Search for your location",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
            // Swift pill — border-only orange, rounded 6, height 32,
            // navigator glyph + Cairo Regular 11 dark label.
            Row(
                Modifier
                    .height(32.dp)
                    .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(6.dp))
                    .clickable(onClick = onUseCurrentLocation)
                    .padding(horizontal = 12.dp)
                    .testTag("delivery-use-location"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(PeachBorder),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Use Current Location",
                    style = AirdropType.body3.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = colors.textDarkTitle,
                    maxLines = 1,
                )
            }
        }

        // Search field — placeholder "Search by Town or Parish" + magnifier.
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colors.gray100, RoundedCornerShape(10.dp))
                .border(1.dp, colors.iconShape, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.gray500),
                modifier = Modifier.size(18.dp),
            )
            Box(Modifier.weight(1f)) {
                if (state.searchQuery.isEmpty()) {
                    Text(
                        text = "Search by Town or Parish",
                        style = AirdropType.body2,
                        color = colors.textPlaceholder,
                    )
                }
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                    cursorBrush = SolidColor(BrandPalette.OrangeMain),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delivery-search"),
                )
            }
        }

        // Results dropdown (Figma 40008740:28370) — Swift caps visible rows.
        if (state.searchResults.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.gray100, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.iconShape, RoundedCornerShape(10.dp)),
            ) {
                visibleDeliverySearchResults(state.searchResults).forEachIndexed { index, place ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                    }
                    Text(
                        text = place.address.orEmpty(),
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSearchResultPicked(place) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("delivery-search-result-$index"),
                    )
                }
            }
        }

        // Map card — 201dp, radius 5 (real OSM map, see DeliveryMapView).
        DeliveryMapView(
            center = state.mapCenter,
            marker = state.markerCoord,
            addressLabel = state.validatedAddress,
            onPointPicked = onMapPointPicked,
        )

        // Selected-location card — hidden until validated (Swift
        // buildSelectedLocationCard, InfoNotice-styled like PromiseCard).
        val validatedAddress = state.validatedAddress
        if (validatedAddress != null && state.markerCoord != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .background(colors.infoBoxBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.infoBoxBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 15.dp, vertical = 9.dp)
                    .testTag("delivery-selected-location"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.size(24.dp).testTag("delivery-selected-location-pin"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Selected Location:",
                        style = AirdropType.subtitle2,
                        color = colors.textDarkTitle,
                        modifier = Modifier.testTag("delivery-selected-location-title"),
                    )
                    Text(
                        text = validatedAddress,
                        style = AirdropType.subtitle2,
                        color = colors.textDarkTitle,
                        maxLines = 2,
                        modifier = Modifier.testTag("delivery-selected-location-detail"),
                    )
                }
            }
        }

        Text(
            text = "You can also click or drag the marker on the map to select " +
                "your exact location.",
            style = AirdropType.body3,
            // Figma 40008740:28297 pixel round 18 — hint is textDarkTitle.
            color = colors.textDarkTitle,
        )
    }
}

internal fun visibleDeliverySearchResults(results: List<PlaceResult>): List<PlaceResult> = results.take(5)

/** Swift formatPlacemark: ordered fields, blank trimming, adjacent dedupe only. */
internal fun formatDevicePlaceName(vararg fields: String?): String? {
    val parts = fields.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .fold(mutableListOf<String>()) { result, part ->
            if (result.lastOrNull() != part) result += part
            result
        }
    return parts.joinToString(", ").ifBlank { null }
}
