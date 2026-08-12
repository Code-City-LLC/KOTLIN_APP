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

sealed interface DutyRateSearchState {
    data object Hidden : DutyRateSearchState
    data object Loading : DutyRateSearchState
    data class Results(val products: List<CalcDutyRate>) : DutyRateSearchState
}

data class CalculatorUiState(
    val method: ShippingMethod = ShippingMethod.STANDARD,
    val product: String = "",
    val selectedDutyRate: CalcDutyRate? = null,
    val packages: String = "",
    val invoiceUsd: String = "",
    val actualWeight: String = "",
    val length: String = "",
    val width: String = "",
    val height: String = "",
    val lengthUnit: LengthUnit = LengthUnit.INCH,
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val calculating: Boolean = false,
    val searchState: DutyRateSearchState = DutyRateSearchState.Hidden,
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
        _state.update { it.copy(product = value, selectedDutyRate = null) }
        searchJob?.cancel()
        val query = value.trim()
        if (query.length < 3) {
            _state.update { it.copy(searchState = DutyRateSearchState.Hidden) }
            return
        }
        _state.update { it.copy(searchState = DutyRateSearchState.Loading) }
        searchJob = viewModelScope.launch {
            delay(500)
            val products = runCatching { repository.searchDutyRates(query) }
                .getOrDefault(emptyList())
            if (_state.value.product.trim() == query) {
                _state.update { it.copy(searchState = DutyRateSearchState.Results(products.take(8))) }
            }
        }
    }

    /**
     * Retains the selected rate so its id can travel with the quote. Editing
     * the text clears it again in [onProductChange] — a stale id must never
     * outlive the name the customer can see.
     */
    fun onProductSelected(rate: CalcDutyRate) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                product = rate.itemName,
                selectedDutyRate = rate,
                searchState = DutyRateSearchState.Hidden,
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
        val parsedWeight = form.actualWeight.replace(',', '.').toDoubleOrNull() ?: 0.0
        // No weight is NO weight. This used to substitute the package count as
        // "a proxy weight", so three packages of unknown weight were priced as
        // three pounds — a number nobody entered and nothing measured.
        val weightLbs: Double? = when {
            parsedWeight <= 0 -> null
            form.weightUnit == WeightUnit.KG -> maxOf(0.5, parsedWeight / 0.453592)
            else -> maxOf(0.5, parsedWeight)
        }
        // The server requires weight for the airdrop methods (Scribe:
        // "required for airdrop methods"). Ask for it rather than invent it.
        if (weightLbs == null && form.method != ShippingMethod.SEADROP) {
            _state.update {
                it.copy(alert = CalcAlert("Missing weight", "Enter the weight of a package to price this shipment."))
            }
            return
        }

        // EVERY method now asks the server. AirDrop Standard used to run a
        // client-side formula (ShippingCalculator) that under-quoted twice over:
        //   * its rate table was stale — freight `3 + weight*3` with no rounding
        //     against the server's tiered card applied to the weight rounded UP
        //     to the next pound, and a hardcoded 1.00 fuel surcharge against the
        //     server's 1.50; and
        //   * it ignored `packageCount` entirely, so the quote was identical for
        //     1 package and for 6. Measured on pre-staging at 5.5 lb / $150,
        //     six packages quoted USD 23.50 against a real USD 165.00 — 86% low,
        //     from a number the customer had typed into a required field.
        // POST /shipping/calculate has always accepted number_of_packages and
        // returns the whole breakdown. Kemar 2026-07-26: server rates, and an
        // error if they cannot be fetched. Never quote a number no system
        // authored.
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
                    // The id, never a percentage: the server validates it is
                    // active and resolves the rate itself.
                    customDutyRateId = _state.value.selectedDutyRate?.id,
                )
            }.onSuccess { live ->
                _state.update { it.copy(calculating = false) }
                publishResult(form, invoice, weightLbs ?: 0.0, live)
            }.onFailure { e ->
                // No offline fallback. It used to push the results screen with
                // the client formula — and for SeaDrop and Express that meant
                // silently running the AIR formula while the screen still said
                // "SeaDrop Results". A wrong price shown confidently is worse
                // than no price.
                _state.update {
                    it.copy(
                        calculating = false,
                        alert = CalcAlert(
                            "Couldn't get current rates",
                            "We couldn't reach our pricing service, so we can't quote this " +
                                "shipment right now. Please check your connection and try again.",
                        ),
                    )
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
