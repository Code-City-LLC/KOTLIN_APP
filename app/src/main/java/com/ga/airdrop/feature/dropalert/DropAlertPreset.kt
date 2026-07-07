package com.ga.airdrop.feature.dropalert

import android.content.Context
import android.content.SharedPreferences

/**
 * Saved-shipper preset — Swift FigmaDropAlertViewController §B.5: after a
 * successful submit the shipper / courier company / shipping method are kept in
 * SharedPreferences and pre-filled into the empty form on the next open and
 * after a reset, so a repeat drop alert doesn't require retyping them.
 */
object DropAlertPreset {
    private const val PREFS = "dropalert_preset"
    private const val KEY_SHIPPER = "shipper"
    private const val KEY_COURIER = "courierCompany"
    private const val KEY_METHOD = "shippingMethod"

    private var prefs: SharedPreferences? = null

    data class Preset(
        val shipper: String = "",
        val courierCompany: String = "",
        val shippingMethod: String = "",
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Empty preset before [init] (e.g. in unit tests) or when nothing saved. */
    fun read(): Preset {
        val store = prefs ?: return Preset()
        return Preset(
            shipper = store.getString(KEY_SHIPPER, "").orEmpty(),
            courierCompany = store.getString(KEY_COURIER, "").orEmpty(),
            shippingMethod = store.getString(KEY_METHOD, "").orEmpty(),
        )
    }

    /** Persist NON-EMPTY values only (Swift savePreset — defensive against a
     *  submit that blanked one of the three). No-op before [init]. */
    fun save(shipper: String, courierCompany: String, shippingMethod: String) {
        val store = prefs ?: return
        store.edit().apply {
            if (shipper.isNotBlank()) putString(KEY_SHIPPER, shipper)
            if (courierCompany.isNotBlank()) putString(KEY_COURIER, courierCompany)
            if (shippingMethod.isNotBlank()) putString(KEY_METHOD, shippingMethod)
        }.apply()
    }
}

/**
 * Pre-fill the shipper / courier / method fields from [preset] only where the
 * form is still blank — Swift applySavedShipperPreset. The saved courier company
 * is applied only when it's still a valid option (a stale/renamed courier is
 * ignored rather than sticking an unselectable value in the dropdown).
 */
fun applyPreset(
    state: DropAlertUiState,
    preset: DropAlertPreset.Preset,
    courierOptions: List<String>,
): DropAlertUiState = state.copy(
    shipper = state.shipper.ifBlank { preset.shipper },
    shippingMethod = state.shippingMethod.ifBlank { preset.shippingMethod },
    courierCompany = if (state.courierCompany.isBlank() && preset.courierCompany in courierOptions) {
        preset.courierCompany
    } else {
        state.courierCompany
    },
)
