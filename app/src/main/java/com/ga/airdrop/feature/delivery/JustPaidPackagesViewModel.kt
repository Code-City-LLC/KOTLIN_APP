package com.ga.airdrop.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.feature.shipments.ShipmentsPackagesRepository
import com.ga.airdrop.feature.shipments.ShipmentsRepoProvider
import com.ga.airdrop.feature.shipments.ShipmentStatusCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The packages a checkout actually paid for, for the post-checkout screen.
 *
 * ⚠️ WHAT THIS REPLACED. The screen used to render a fixed two-stage rail —
 * "Preparing for Dispatch" / "Out for Delivery" — regardless of what had
 * actually happened. Kemar: *"use only real generated info not static"*, and
 * the post-checkout screen must **fetch the real packages**.
 *
 * ⚠️ AND WHAT IT DELIBERATELY DOES NOT DO. A package that was paid for seconds
 * ago has **no journey yet** — measured on pre-staging, status-20 packages
 * return zero timeline entries and have zero `package_change_history` rows. So
 * there is nothing truthful to draw a rail from, and this does not draw one.
 * It reports identity and present status, both server-authored, and stops.
 *
 * Inventing "Order Confirmed → Preparing for Dispatch" from the fact of payment
 * is precisely what `/packages/journeys` was held for (three `done` stages with
 * null timestamps on packages with no history). Blocking that server-side and
 * then doing it here would be indefensible.
 */
internal data class JustPaidPackage(
    val id: Int,
    /**
     * ARD only. Swift CLAUDE.md:31 — *"ARD number is the only customer-facing
     * reference. Never display `AIR-…` tracking codes to customers."* Null when
     * the server does not supply one; the row then omits the reference rather
     * than substituting a courier or internal identifier.
     */
    val ardNumber: String?,
    val description: String?,
    /**
     * The server's status label, or NULL when it did not supply one.
     *
     * ⚠️ This was `"Paid"` as a fallback. That authored a status the server
     * never sent: a successful payment proves the payment succeeded, it does
     * NOT prove the package's current status label. The screen renders a
     * truthful "Status unavailable" instead of inventing one. BrightHarbor
     * #80393. Same shape as everything else on this screen — an absence of
     * information is not information.
     */
    val statusName: String?,
    val statusId: Int?,
)

internal data class JustPaidUiState(
    val loading: Boolean = false,
    val packages: List<JustPaidPackage> = emptyList(),
    /** Every lookup failed — we know nothing, and must say so. */
    val failed: Boolean = false,
    /**
     * How many paid-for packages we could NOT read.
     *
     * ⚠️ Silently dropping these is the bug this whole thread keeps
     * rediscovering. Rendering two packages when the customer paid for three
     * asserts something false about their purchase — the absence of a lookup
     * is not evidence the package does not exist. A partial result is still
     * worth showing, but it must be labelled as partial.
     */
    val unresolvedCount: Int = 0,
)

internal class JustPaidPackagesViewModel(
    private val packageIds: List<Int>,
    private val repo: ShipmentsPackagesRepository = ShipmentsRepoProvider.packages,
) : ViewModel() {

    private val _state = MutableStateFlow(JustPaidUiState(loading = packageIds.isNotEmpty()))
    val state: StateFlow<JustPaidUiState> = _state

    init {
        if (packageIds.isNotEmpty()) load()
    }

    private fun load() {
        viewModelScope.launch {
            val resolved = packageIds.mapNotNull { id ->
                repo.packageDetails(id.toString()).getOrNull()
                    // ⚠️ A success is not proof it is the RIGHT package. Without
                    // this, a mismatched or zero-id response would display some
                    // other customer's package AND decrement unresolvedCount as
                    // though the paid-for one had been found. BrightHarbor #80393.
                    ?.takeIf { it.id > 0 && it.id == id }
                    ?.let { detail ->
                    JustPaidPackage(
                        id = detail.id,
                        // The ARD, never courierNumber — see JustPaidPackage.
                        ardNumber = detail.trackingCode?.trim()?.takeIf { it.isNotEmpty() },
                        description = detail.description?.trim()?.takeIf { it.isNotEmpty() },
                        // Null when the server said nothing. Never invented.
                        statusName = ShipmentStatusCatalog
                            .displayName(detail.statusName, detail.status)
                            .takeIf { it.isNotBlank() && it != "—" },
                        statusId = detail.status?.trim()?.toIntOrNull(),
                    )
                }
            }
            _state.update {
                it.copy(
                    loading = false,
                    packages = resolved,
                    // Only a TOTAL failure means we know nothing. A partial
                    // result is still worth showing — but the shortfall is
                    // reported rather than swallowed.
                    failed = resolved.isEmpty() && packageIds.isNotEmpty(),
                    unresolvedCount = (packageIds.size - resolved.size).coerceAtLeast(0),
                )
            }
        }
    }
}
