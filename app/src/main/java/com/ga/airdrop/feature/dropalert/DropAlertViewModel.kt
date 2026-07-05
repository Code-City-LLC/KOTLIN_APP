package com.ga.airdrop.feature.dropalert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** OK-dialog payload (Swift presentSimpleAlert). */
data class DropAlertDialog(val title: String, val message: String)

data class DropAlertUiState(
    val courierNumber: String = "",
    val shippingMethod: String = "",
    val shipper: String = "",
    val consignee: String = "",
    val packageValue: String = "",
    val courierCompany: String = "",
    val description: String = "",
    val invoices: List<DropAlertInvoice> = emptyList(),
    val submitting: Boolean = false,
    val dialog: DropAlertDialog? = null,
)

class DropAlertViewModel(
    private val repository: DropAlertRepository = RemoteDropAlertRepository(),
) : ViewModel() {

    companion object {
        const val MAX_INVOICES = 3
        const val MAX_INVOICE_BYTES = 10L * 1024 * 1024 // "size below 10 MB"

        // RN DropAlertView picker `values` — Express is intentionally omitted
        // from the create-alert flow even though the API supports it.
        val SHIPPING_METHOD_OPTIONS = listOf("Airdrop standard", "SeaDrop Standard")

        // RN DropAlertView courierOptions, verbatim.
        val COURIER_COMPANY_OPTIONS = listOf(
            "3rd Party Shipper", "Airborne Express", "Amazon Logistics",
            "China Post / International Mail", "DHL / Airborne", "FedEx",
            "FedEx Freight", "Lasership", "Other", "Pitney Bowes", "SpeedBox",
            "StratAir", "Streamlite", "TNT", "UPS", "UPS Mail Innovations",
            "UPS Next Day", "USPS", "WN Direct", "Walk-In",
        )
    }

    private val _state = MutableStateFlow(DropAlertUiState())
    val state: StateFlow<DropAlertUiState> = _state

    init {
        prefillConsignee()
    }

    /** Swift prefillConsignee — left blank when the profile fetch fails. */
    private fun prefillConsignee() {
        viewModelScope.launch {
            repository.consigneeName()?.let { name ->
                _state.update { it.copy(consignee = name) }
            }
        }
    }

    fun onCourierNumberChange(value: String) = _state.update { it.copy(courierNumber = value) }
    fun onShippingMethodSelected(value: String) = _state.update { it.copy(shippingMethod = value) }
    fun onShipperChange(value: String) = _state.update { it.copy(shipper = value) }
    fun onConsigneeChange(value: String) = _state.update { it.copy(consignee = value) }
    fun onPackageValueChange(value: String) = _state.update { it.copy(packageValue = value) }
    fun onCourierCompanySelected(value: String) = _state.update { it.copy(courierCompany = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    /**
     * Gate for the upload card tap — Swift onUploadTapped. Returns true when
     * the picker may open; otherwise raises the "Maximum reached" dialog.
     */
    fun onUploadTapped(): Boolean {
        if (_state.value.invoices.size < MAX_INVOICES) return true
        _state.update {
            it.copy(dialog = DropAlertDialog("Maximum reached", "You can upload up to $MAX_INVOICES files."))
        }
        return false
    }

    fun addInvoice(invoice: DropAlertInvoice) {
        val current = _state.value
        if (current.invoices.size >= MAX_INVOICES) return
        if (invoice.bytes.size > MAX_INVOICE_BYTES) {
            _state.update {
                it.copy(dialog = DropAlertDialog("File too large", "Each file must be below 10 MB."))
            }
            return
        }
        _state.update { it.copy(invoices = it.invoices + invoice) }
    }

    fun removeInvoice(invoice: DropAlertInvoice) {
        _state.update { s -> s.copy(invoices = s.invoices.filterNot { it === invoice }) }
    }

    /** Port of FigmaDropAlertViewController.onSubmit. */
    fun submit() {
        val form = _state.value
        if (form.submitting) return
        val missingRequired = form.courierNumber.isBlank() ||
            form.shippingMethod.isBlank() ||
            form.shipper.isBlank() ||
            form.courierCompany.isBlank() ||
            form.consignee.isBlank() ||
            form.packageValue.isBlank()
        if (missingRequired) {
            _state.update {
                it.copy(dialog = DropAlertDialog("Missing fields", "Please fill the required fields marked with *."))
            }
            return
        }

        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            runCatching {
                repository.createDropAlert(
                    DropAlertSubmission(
                        courierNumber = form.courierNumber,
                        shippingMethod = DropAlertShippingMethod.fromDisplayName(form.shippingMethod),
                        shipper = form.shipper,
                        store = form.courierCompany,
                        packageAmount = form.packageValue,
                        consignee = form.consignee,
                        description = form.description.ifBlank { null },
                        invoices = form.invoices,
                    )
                )
            }.onSuccess { result ->
                val confirmation = result.message?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Your drop alert was created."
                // Swift resetFormAfterSubmit clears every field, including Consignee.
                _state.update {
                    DropAlertUiState(
                        dialog = DropAlertDialog("Submitted", confirmation),
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        submitting = false,
                        dialog = DropAlertDialog("Submit failed", e.message ?: "Please try again."),
                    )
                }
            }
        }
    }
}
