package com.ga.airdrop.feature.more

import android.content.Context
import androidx.lifecycle.ViewModel
import com.ga.airdrop.core.push.QuietHoursStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class QuietHoursUiState(
    val enabled: Boolean = false,
    val startMinutes: Int = QuietHoursStore.DEFAULT_START_MINUTES,
    val endMinutes: Int = QuietHoursStore.DEFAULT_END_MINUTES,
)

/**
 * Quiet Hours preference — Swift FigmaQuietHoursSheet. Per-change persistence
 * (no Save button, matching Swift); [start] loads the saved window from
 * [QuietHoursStore].
 */
class QuietHoursViewModel : ViewModel() {

    private val _state = MutableStateFlow(QuietHoursUiState())
    val state: StateFlow<QuietHoursUiState> = _state

    fun start(context: Context) {
        _state.value = QuietHoursUiState(
            enabled = QuietHoursStore.isEnabled(context),
            startMinutes = QuietHoursStore.startMinutes(context),
            endMinutes = QuietHoursStore.endMinutes(context),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        QuietHoursStore.setEnabled(context, enabled)
        _state.update { it.copy(enabled = enabled) }
    }

    fun setStart(context: Context, minutes: Int) {
        QuietHoursStore.setStartMinutes(context, minutes)
        _state.update { it.copy(startMinutes = minutes.coerceIn(0, 1439)) }
    }

    fun setEnd(context: Context, minutes: Int) {
        QuietHoursStore.setEndMinutes(context, minutes)
        _state.update { it.copy(endMinutes = minutes.coerceIn(0, 1439)) }
    }
}
