package com.ga.airdrop.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class OrderSummaryChargesUiState(
    val identity: CheckoutChargeCaptureIdentity? = null,
    val snapshots: List<CheckoutShipmentChargeSnapshot> = emptyList(),
    val paymentCurrency: String? = null,
    val deliveryFee: Double? = null,
    val deliveryFeeCurrency: String? = null,
    val loadingKeys: Set<CartStore.CartLineKey> = emptySet(),
    val failures: Map<CartStore.CartLineKey, String> = emptyMap(),
) {
    val loading: Boolean get() = loadingKeys.isNotEmpty()
    val canonicalFlowAvailable: Boolean get() = identity != null
}

internal interface OrderSummaryChargeFlowAccess {
    fun current(owner: AuthenticatedSessionOwner): CheckoutFlow?

    fun record(
        owner: AuthenticatedSessionOwner,
        expectedIdentity: CheckoutChargeCaptureIdentity,
        snapshots: List<CheckoutShipmentChargeSnapshot>,
    ): CheckoutFlow?
}

private object CheckoutStoreOrderSummaryChargeFlowAccess : OrderSummaryChargeFlowAccess {
    override fun current(owner: AuthenticatedSessionOwner): CheckoutFlow? =
        CheckoutFlowStore.current(owner)

    override fun record(
        owner: AuthenticatedSessionOwner,
        expectedIdentity: CheckoutChargeCaptureIdentity,
        snapshots: List<CheckoutShipmentChargeSnapshot>,
    ): CheckoutFlow? = CheckoutFlowStore.recordShipmentChargeSnapshots(
        owner = owner,
        expectedIdentity = expectedIdentity,
        snapshots = snapshots,
    )
}

/**
 * Hydrates package charges for the immutable checkout capture. This is a
 * read-only companion to CartViewModel: payment ownership and checkout route
 * transitions remain on the existing merged checkout architecture.
 */
internal class OrderSummaryChargesViewModel @JvmOverloads constructor(
    private val repository: OrderSummaryChargesRepository = DataOrderSummaryChargesRepository(),
    private val sessionBoundary: AuthenticatedSessionBoundary =
        DefaultAuthenticatedSessionBoundary,
    private val flowAccess: OrderSummaryChargeFlowAccess =
        CheckoutStoreOrderSummaryChargeFlowAccess,
) : ViewModel() {

    private data class Target(
        val key: CartStore.CartLineKey,
        val packageId: Int,
    )

    private val _state = MutableStateFlow(OrderSummaryChargesUiState())
    val state: StateFlow<OrderSummaryChargesUiState> = _state.asStateFlow()

    private var observedOwner = sessionBoundary.capture()
    private var activeJob: Job? = null
    private var requestGeneration = 0L
    private var loadedIdentity: CheckoutChargeCaptureIdentity? = null
    private var capturedLines: List<CartStore.CartLine> = emptyList()

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { owner ->
                if (owner != observedOwner) {
                    observedOwner = owner
                    invalidateForOwnerChange()
                }
            }
        }
    }

    fun hydrate(lines: List<CartStore.CartLine>) {
        val exactLines = lines.toList()
        val owner = sessionBoundary.capture()
        val flow = owner?.let(flowAccess::current)
            ?.takeIf { it.matchesCapturedLines(exactLines) }
        if (owner == null || flow == null) {
            invalidateForMissingFlow()
            return
        }
        val identity = flow.chargeCaptureIdentity()
        capturedLines = exactLines
        if (identity == loadedIdentity && activeJob?.isActive == true) return
        if (identity == loadedIdentity && _state.value.identity == identity) return

        val targets = flow.shipmentTargets()
        loadedIdentity = identity
        if (targets.isEmpty()) {
            activeJob?.cancel()
            _state.value = flow.toChargesState()
            return
        }
        launchLoad(owner, flow, exactLines, targets)
    }

    fun retryFailed() {
        val failedKeys = _state.value.failures.keys
        if (failedKeys.isEmpty()) return
        val owner = sessionBoundary.capture() ?: return
        val flow = flowAccess.current(owner)
            ?.takeIf { it.matchesCapturedLines(capturedLines) }
            ?: return
        val identity = flow.chargeCaptureIdentity()
        if (_state.value.identity != identity) return
        val targets = flow.shipmentTargets().filter { it.key in failedKeys }
        if (targets.isNotEmpty()) launchLoad(owner, flow, capturedLines, targets)
    }

    private fun launchLoad(
        owner: AuthenticatedSessionOwner,
        flow: CheckoutFlow,
        lines: List<CartStore.CartLine>,
        targets: List<Target>,
    ) {
        activeJob?.cancel()
        val generation = ++requestGeneration
        val identity = flow.chargeCaptureIdentity()
        val requestOwner = sessionBoundary.requestOwner(owner) ?: run {
            invalidateForMissingFlow()
            return
        }
        _state.value = flow.toChargesState(
            loadingKeys = targets.map(Target::key).toSet(),
            failures = _state.value.failures - targets.map(Target::key).toSet(),
        )
        activeJob = viewModelScope.launch {
            val results = try {
                coroutineScope {
                    targets.map { target ->
                        async {
                            target to try {
                                repository.packageDetail(
                                    cartKey = target.key,
                                    packageId = target.packageId,
                                    expectedSession = requestOwner.provenance,
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                        }
                    }.awaitAll()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            applyResponse(
                generation = generation,
                owner = requestOwner.session,
                identity = identity,
                lines = lines,
                results = results,
            )
        }
    }

    private fun applyResponse(
        generation: Long,
        owner: AuthenticatedSessionOwner,
        identity: CheckoutChargeCaptureIdentity,
        lines: List<CartStore.CartLine>,
        results: List<Pair<Target, Result<CheckoutShipmentChargeSnapshot>>>,
    ) {
        sessionBoundary.runWhileCurrent(owner) {
            if (generation != requestGeneration) return@runWhileCurrent false
            val latestFlow = flowAccess.current(owner)
            if (
                latestFlow == null ||
                latestFlow.chargeCaptureIdentity() != identity ||
                !latestFlow.matchesCapturedLines(lines)
            ) {
                loadedIdentity = null
                _state.value = OrderSummaryChargesUiState()
                return@runWhileCurrent true
            }

            val successes = results.mapNotNull { (_, result) -> result.getOrNull() }
            val failed = results.mapNotNull { (target, result) ->
                result.exceptionOrNull()?.let { error -> target.key to error.displayMessage() }
            }.toMap()
            val persisted = when {
                successes.isEmpty() -> latestFlow
                else -> flowAccess.record(owner, identity, successes)
            }
            if (persisted == null) {
                _state.value = latestFlow.toChargesState(
                    failures = _state.value.failures + results.associate { (target, _) ->
                        target.key to "Package details could not be attached to this checkout."
                    },
                )
                return@runWhileCurrent true
            }

            val completedKeys = results.map { it.first.key }.toSet()
            _state.value = persisted.toChargesState(
                failures = (_state.value.failures - completedKeys) + failed,
            )
            true
        }
    }

    private fun invalidateForOwnerChange() {
        activeJob?.cancel()
        activeJob = null
        requestGeneration++
        loadedIdentity = null
        capturedLines = emptyList()
        _state.value = OrderSummaryChargesUiState()
    }

    private fun invalidateForMissingFlow() {
        if (_state.value == OrderSummaryChargesUiState() && activeJob == null) return
        invalidateForOwnerChange()
    }

    private fun CheckoutFlow.matchesCapturedLines(lines: List<CartStore.CartLine>): Boolean {
        if (phase != CheckoutPhase.ORDER_SUMMARY || cartKeys.size != packageIds.size) return false
        if (lines.size != cartKeys.size) return false
        val linesByKey = lines.associateBy(CartStore.CartLine::key)
        if (linesByKey.size != lines.size || linesByKey.keys != cartKeys.toSet()) return false
        return cartKeys.map { key -> linesByKey[key]?.packageId } == packageIds &&
            cartKeys.all { key -> linesByKey[key]?.resolvedKind == key.kind }
    }

    private fun CheckoutFlow.shipmentTargets(): List<Target> =
        cartKeys.zip(packageIds).mapNotNull { (key, packageId) ->
            Target(key, packageId).takeIf { key.kind == CartStore.CartLineKind.PACKAGE }
        }

    private fun CheckoutFlow.toChargesState(
        loadingKeys: Set<CartStore.CartLineKey> = emptySet(),
        failures: Map<CartStore.CartLineKey, String> = emptyMap(),
    ): OrderSummaryChargesUiState {
        val shipmentPairs = cartKeys.zip(packageIds)
            .filter { (key, _) -> key.kind == CartStore.CartLineKind.PACKAGE }
            .toMap()
        return OrderSummaryChargesUiState(
            identity = chargeCaptureIdentity(),
            snapshots = shipmentChargeSnapshots.filter { snapshot ->
                shipmentPairs[snapshot.cartKey] == snapshot.packageId
            },
            paymentCurrency = currency,
            deliveryFee = deliveryFee,
            deliveryFeeCurrency = deliveryFeeCurrency,
            loadingKeys = loadingKeys,
            failures = failures.filterKeys(shipmentPairs::containsKey),
        )
    }

    private fun Throwable.displayMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Package details are temporarily unavailable."
}
