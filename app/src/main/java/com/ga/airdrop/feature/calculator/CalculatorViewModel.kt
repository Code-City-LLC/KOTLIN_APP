package com.ga.airdrop.feature.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.prefs.ExchangeRateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Simple OK-dialog payload, Android stand-in for Swift presentSimpleAlert. */
data class CalcAlert(val title: String, val message: String)

sealed interface ProductSearchState {
    data object Hidden : ProductSearchState
    data object Loading : ProductSearchState
    data class Results(val products: List<CalcProduct>) : ProductSearchState
}

data class CalculatorUiState(
    val method: ShippingMethod = ShippingMethod.STANDARD,
    val product: String = "",
    val selectedProduct: CalcProduct? = null,
    val packages: String = "",
    val invoiceUsd: String = "",
    val actualWeight: String = "",
    val length: String = "",
    val width: String = "",
    val height: String = "",
    val lengthUnit: LengthUnit = LengthUnit.INCH,
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val calculating: Boolean = false,
    val searchState: ProductSearchState = ProductSearchState.Hidden,
    val alert: CalcAlert? = null,
    /** One-shot: set when a calculation is ready for the results screen. */
    val navigateToResults: Boolean = false,
)

/**
 * Shared across the calculator nav graph (form → results → government
 * charges) so [result] rides along without serializing it into route args —
 * the Swift flow passes the same values through the results-VC initializer.
 */
class CalculatorViewModel(
    private val repository: CalculatorRepository = RemoteCalculatorRepository(),
) : ViewModel() {

    // Swift §D.4: the form pre-selects the method of the last successful
    // calculation (loadLastMethod, .standard until a first save).
    private val _state = MutableStateFlow(CalculatorUiState(method = CalculatorHistory.lastMethod()))
    val state: StateFlow<CalculatorUiState> = _state

    private val _result = MutableStateFlow<CalculationResult?>(null)
    val result: StateFlow<CalculationResult?> = _result

    /** USD→JMD rate for the CIF bottom sheet; shared last-known rate until fetched. */
    private val _usdToJmd = MutableStateFlow(ExchangeRateStore.current)
    val usdToJmd: StateFlow<Double> = _usdToJmd

    private var searchJob: Job? = null
    private var rateLoaded = false

    // ─── Form updates ───

    fun onMethodSelected(method: ShippingMethod) = _state.update { it.copy(method = method) }
    fun onPackagesChange(value: String) = _state.update { it.copy(packages = value) }
    fun onInvoiceChange(value: String) = _state.update { it.copy(invoiceUsd = value) }
    fun onActualWeightChange(value: String) = _state.update { it.copy(actualWeight = value) }
    fun onLengthChange(value: String) = _state.update { it.copy(length = value) }
    fun onWidthChange(value: String) = _state.update { it.copy(width = value) }
    fun onHeightChange(value: String) = _state.update { it.copy(height = value) }
    fun onLengthUnitSelected(unit: LengthUnit) = _state.update { it.copy(lengthUnit = unit) }
    fun onWeightUnitSelected(unit: WeightUnit) = _state.update { it.copy(weightUnit = unit) }
    fun dismissAlert() = _state.update { it.copy(alert = null) }
    fun onNavigatedToResults() = _state.update { it.copy(navigateToResults = false) }

    // ─── Product search (Swift: 500ms debounce, ≥3 chars, top 8 rendered) ───

    fun onProductChange(value: String) {
        _state.update { it.copy(product = value, selectedProduct = null) }
        searchJob?.cancel()
        val query = value.trim()
        if (query.length < 3) {
            _state.update { it.copy(searchState = ProductSearchState.Hidden) }
            return
        }
        _state.update { it.copy(searchState = ProductSearchState.Loading) }
        searchJob = viewModelScope.launch {
            delay(500)
            val products = runCatching { repository.searchProducts(query) }
                .getOrDefault(emptyList())
            if (_state.value.product.trim() == query) {
                _state.update { it.copy(searchState = ProductSearchState.Results(products.take(8))) }
            }
        }
    }

    fun onProductSelected(product: CalcProduct) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                product = product.title,
                selectedProduct = product,
                searchState = ProductSearchState.Hidden,
            )
        }
    }

    // ─── Calculate — port of FigmaCalculatorViewController.onCalculate ───

    fun calculate() {
        val form = _state.value
        if (form.calculating) return

        val invoice = form.invoiceUsd.replace(',', '.').toDoubleOrNull()
        if (invoice == null || invoice <= 0) {
            _state.update {
                it.copy(alert = CalcAlert("Missing invoice amount", "Enter an invoice amount greater than zero."))
            }
            return
        }
        val packageCount = maxOf(1, form.packages.toIntOrNull() ?: 1)
        // Actual weight when supplied; SeaDrop falls back to package count as
        // a proxy weight (Swift parity).
        val parsedWeight = form.actualWeight.replace(',', '.').toDoubleOrNull() ?: 0.0
        val weightLbs = when {
            parsedWeight > 0 && form.weightUnit == WeightUnit.KG -> maxOf(0.5, parsedWeight / 0.453592)
            parsedWeight > 0 -> maxOf(0.5, parsedWeight)
            else -> maxOf(0.5, packageCount.toDouble())
        }

        when (form.method) {
            ShippingMethod.STANDARD -> {
                // Offline formula; `live = null` tells the results screen to
                // run ShippingCalculator itself so the breakdown stays there.
                runCatching { ShippingCalculator.airdropStandard(weightLbs, invoice) }
                    .onSuccess { publishResult(form, invoice, weightLbs, live = null) }
                    .onFailure { e ->
                        _state.update {
                            it.copy(alert = CalcAlert("Cannot calculate", e.message ?: "Invalid input."))
                        }
                    }
            }

            ShippingMethod.SEADROP, ShippingMethod.EXPRESS -> {
                _state.update { it.copy(calculating = true) }
                val dimensions = parseDimensions(form)
                viewModelScope.launch {
                    runCatching {
                        repository.calculateShipment(
                            shippingMethod = form.method.apiValue,
                            invoiceAmount = invoice,
                            weightLbs = weightLbs,
                            numberOfPackages = packageCount,
                            lengthInches = dimensions.first,
                            widthInches = dimensions.second,
                            heightInches = dimensions.third,
                        )
                    }.onSuccess { live ->
                        _state.update { it.copy(calculating = false) }
                        publishResult(form, invoice, weightLbs, live)
                    }.onFailure { e ->
                        _state.update { it.copy(calculating = false) }
                        // Offline fallback — Swift pushes the results screen
                        // with no live payload when the API is unreachable.
                        val fallback = runCatching {
                            ShippingCalculator.airdropStandard(weightLbs, invoice)
                        }.getOrNull()
                        if (fallback != null) {
                            publishResult(form, invoice, weightLbs, live = null)
                        } else {
                            _state.update {
                                it.copy(alert = CalcAlert("Cannot calculate", e.message ?: "Please try again."))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseDimensions(form: CalculatorUiState): Triple<Double?, Double?, Double?> {
        val factor = if (form.lengthUnit == LengthUnit.FT) 12.0 else 1.0
        val length = form.length.replace(',', '.').toDoubleOrNull()?.times(factor)
        val width = form.width.replace(',', '.').toDoubleOrNull()?.times(factor)
        val height = form.height.replace(',', '.').toDoubleOrNull()?.times(factor)
        return Triple(length, width, height)
    }

    private fun publishResult(
        form: CalculatorUiState,
        invoice: Double,
        weightLbs: Double,
        live: ShipmentCalculation?,
    ) {
        // ft → inches so the results screen always derives ft³ from inches.
        val factor = if (form.lengthUnit == LengthUnit.FT) 12.0 else 1.0
        _result.value = CalculationResult(
            method = form.method,
            productName = form.product.ifBlank { null },
            weightLbs = weightLbs,
            weightUnit = form.weightUnit,
            invoiceUsd = invoice,
            lengthIn = form.length.replace(',', '.').toDoubleOrNull()?.times(factor),
            widthIn = form.width.replace(',', '.').toDoubleOrNull()?.times(factor),
            heightIn = form.height.replace(',', '.').toDoubleOrNull()?.times(factor),
            live = live,
        )
        // Swift §D.4: remember the method that just calculated successfully so
        // the next Calculator open pre-selects it.
        CalculatorHistory.saveLastMethod(form.method)
        // Swift §B.6: record this quote in the 5-item history ring. Total comes
        // from resolveCharges (live totalWithDuty, else the offline grandTotal).
        CalculatorHistory.record(
            CalculatorHistory.Entry(
                method = form.method.name,
                weightLbs = weightLbs,
                invoiceUsd = invoice,
                totalUsd = resolveCharges(_result.value!!).totalWithDuty,
                createdAt = System.currentTimeMillis(),
            ),
        )
        _state.update { it.copy(navigateToResults = true) }
    }

    /**
     * Re-run a stored calculation — Swift repopulateForm(from:) + pushResults:
     * restore method + weight (canonical lbs) + invoice, then recompute so the
     * user lands back on the results screen. Dimensions aren't persisted.
     */
    fun repopulateFromHistory(entry: CalculatorHistory.Entry) {
        val method = ShippingMethod.entries.firstOrNull { it.name == entry.method }
            ?: ShippingMethod.STANDARD
        _state.update {
            it.copy(
                method = method,
                weightUnit = WeightUnit.LBS,
                actualWeight = formatAmount(entry.weightLbs),
                invoiceUsd = formatAmount(entry.invoiceUsd),
            )
        }
        calculate()
    }

    private fun formatAmount(value: Double): String =
        java.util.Locale.US.let { String.format(it, "%.2f", value) }

    /** Lazily fetch the USD→JMD rate for the CIF sheet (once per session). */
    fun loadExchangeRate() {
        if (rateLoaded) return
        rateLoaded = true
        viewModelScope.launch {
            _usdToJmd.value = runCatching { repository.usdToJmdRate() }
                .onSuccess { ExchangeRateStore.update(it) }
                .getOrDefault(ExchangeRateStore.current)
        }
    }
}
