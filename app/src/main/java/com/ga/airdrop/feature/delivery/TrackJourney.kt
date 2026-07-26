package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.repo.TrackedDelivery
import com.ga.airdrop.feature.shipments.PackageHistoryItem
import com.ga.airdrop.feature.shipments.ShipmentStatusCatalog

/**
 * The FULL Track journey: the package's real warehouse history joined to the
 * last-mile delivery legs.
 *
 * ⚠️ WHY THIS EXISTS. Track used to render a hardcoded three-stage skeleton
 * that began at "Preparing for Dispatch" with copy the client made up
 * ("Packing your items for the courier."). A customer whose package had been
 * received, cleared customs and processed at the warehouse saw none of it —
 * their journey appeared to begin at dispatch. Kemar: *"It starts at shipment
 * received or drop alerted... It needs to show the REAL statuses"* and
 * *"use only real generated info not static"*.
 *
 * Nothing here is invented. Every row comes from a recorded event, and every
 * label is the server's own `status_name`.
 *
 * Rules, all from the shared handoffs:
 *  - **Order by canonical progression, NEVER the timestamp.**
 *    `package_status_datetime` is a varchar and ~1,069 legacy rows use
 *    `YYYY/MM/DD`, which sorts AFTER `YYYY-MM-DD` lexicographically.
 *  - **Exclude status 8 from the warehouse rail.** `markDelivered()` writes
 *    BOTH a status-8 history row and a `package_deliveries` row, so keeping it
 *    prints "Delivered" twice.
 *  - **Never surface `comment` / `changed_by`** — internal staff notes and
 *    names.
 *  - **Unknown statuses still render**, in recorded order, using the server's
 *    label. A fixed list may ORDER; it must never filter (#79761 §1).
 *  - **Exactly one upcoming step** (#79650 rule 3).
 */
internal object TrackJourney {

    /** Written by markDelivered alongside the delivery leg — would duplicate. */
    private const val STATUS_DELIVERED = 8

    /**
     * Statuses where the journey has gone wrong and the customer needs a human.
     *
     * Kemar 2026-07-26, asked whether these should appear at all: show them,
     * **with a Contact us action on that row**. A customer whose package is
     * detained at customs should be told by the app, not by silence — hiding
     * it means the rail goes quiet for days with no explanation and they call
     * in anyway. So we surface the server's own label and attach the way out.
     */
    val NEEDS_HELP_STATUSES = setOf(
        10, // Detained at Customs
        15, // Uncollected Packages
        16, // Dangerous Goods
        19, // Returned to Merchant
    )

    fun needsHelp(statusId: Int?): Boolean = statusId != null && statusId in NEEDS_HELP_STATUSES

    /**
     * Canonical position of a warehouse status. Known ids keep catalogue order;
     * unknown ids sort after all known ones, preserving their recorded order.
     */
    private fun progressionIndex(statusId: Int?): Int =
        ShipmentStatusCatalog.defaults.firstOrNull { it.id == statusId }?.order ?: Int.MAX_VALUE

    /**
     * The warehouse rail — every recorded history row, in canonical order.
     * These are all in the past by definition, so every row is `done`.
     */
    fun warehouseRail(history: List<PackageHistoryItem>): List<TrackRow> =
        history
            .filter { it.status != STATUS_DELIVERED }
            .mapIndexed { recordedIndex, item -> recordedIndex to item }
            .sortedWith(
                compareBy(
                    { (_, item) -> progressionIndex(item.status) },
                    { (recordedIndex, _) -> recordedIndex },
                ),
            )
            .map { (_, item) ->
                TrackRow(
                    // Key off the server status id, never the label.
                    key = "status_${item.status ?: "unknown"}",
                    // The server's own name. Unknown status -> still shown.
                    label = item.statusName?.trim()?.takeIf(String::isNotEmpty)
                        ?: ShipmentStatusCatalog.defaults
                            .firstOrNull { it.id == item.status }?.name
                        ?: "Update",
                    state = "done",
                    at = item.changedDate,
                    statusId = item.status,
                    needsHelp = needsHelp(item.status),
                )
            }
            // A status can legitimately recur (warehouse -> detained ->
            // warehouse); collapse only CONSECUTIVE repeats, mirroring Laravel.
            .fold(mutableListOf<TrackRow>()) { acc, row ->
                if (acc.lastOrNull()?.key != row.key) acc.add(row)
                acc
            }

    /** The last mile, exactly as the server projects it. */
    fun lastMileRail(delivery: TrackedDelivery?): List<TrackRow> =
        delivery?.stages.orEmpty().map { stage ->
            TrackRow(
                key = stage.key,
                // customerSafeStageLabel keeps operations wording ("Driver
                // Assigned") off the customer's screen.
                label = customerSafeStageLabelForTest(stage),
                state = stage.state,
                at = stage.at,
                statusId = null,
                needsHelp = false,
            )
        }

    /**
     * The whole journey: warehouse history, then the last mile, truncated to a
     * single upcoming step.
     */
    fun build(
        history: List<PackageHistoryItem>,
        delivery: TrackedDelivery?,
    ): List<TrackRow> {
        val rows = warehouseRail(history) + lastMileRail(delivery)
        return truncateAfterFirstPending(rows) { it.state == "pending" }
    }
}

/** One rendered row of the Track journey. */
internal data class TrackRow(
    val key: String,
    val label: String,
    val state: String,
    val at: String?,
    /** Warehouse status id, for icon lookup. Null on last-mile legs. */
    val statusId: Int?,
    /** This row is bad news — render a Contact us action beside it. */
    val needsHelp: Boolean = false,
)
