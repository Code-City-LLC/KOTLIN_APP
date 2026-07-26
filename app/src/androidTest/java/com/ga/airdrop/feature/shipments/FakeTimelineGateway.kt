package com.ga.airdrop.feature.shipments

import com.ga.airdrop.data.model.PackageTimelineEntry
import com.ga.airdrop.data.repo.ActiveDeliveriesPage
import com.ga.airdrop.data.repo.DeliveryTrackingGateway
import com.ga.airdrop.data.repo.DeliveryTrackingResult

/**
 * Stands in for `GET /packages/{id}/timeline` in screen tests.
 *
 * The package screens stopped building the rail from `history` and now render
 * Laravel's projection, so a fixture that only sets `history` produces an empty
 * timeline and a blank Shipment Timeline card. Rather than hand-write a
 * timeline beside every fixture, this derives one from the fixture's history
 * the way the server does: recorded events in order, each with the catalogue's
 * name when the fixture omits one, and no `comment` — that column is internal
 * and never reaches a customer (BrightHarbor #79824).
 */
class FakeTimelineGateway(
    private val entries: List<PackageTimelineEntry>,
) : DeliveryTrackingGateway {

    constructor(detail: ShipmentPackageDetail?) : this(fromHistory(detail))

    override suspend fun activeDeliveries(page: Int, perPage: Int): Result<ActiveDeliveriesPage> =
        Result.failure(UnsupportedOperationException("not used by package screens"))

    override suspend fun deliveryTracking(packageId: Int): Result<DeliveryTrackingResult> =
        Result.failure(UnsupportedOperationException("not used by package screens"))

    override suspend fun packageTimeline(packageId: Int): Result<List<PackageTimelineEntry>> =
        Result.success(entries)

    companion object {
        fun fromHistory(detail: ShipmentPackageDetail?): List<PackageTimelineEntry> {
            val history = detail?.history.orEmpty()
            return history.mapIndexed { index, item ->
                val status = item.status
                PackageTimelineEntry(
                    key = "status_${status ?: index}_$index",
                    status = status,
                    label = item.statusName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: ShipmentStatusCatalog.defaults.firstOrNull { it.id == status }?.name
                        ?: "Update",
                    icon = null,
                    at = item.changedDate?.let { ShipmentsFormat.timelineDate(it) }
                        ?.takeIf { it != "N/A" },
                    atIso = item.changedDate,
                    state = if (index == history.lastIndex) "current" else "done",
                    source = "status",
                )
            }
        }
    }
}
