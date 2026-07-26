package com.ga.airdrop.feature.delivery

import com.ga.airdrop.R
import com.ga.airdrop.data.model.PackageTimelineEntry
import java.time.OffsetDateTime

/**
 * The Track journey, as Laravel projects it.
 *
 * ⚠️ WHAT THIS REPLACED, TWICE OVER. Kotlin used to build this rail itself,
 * from `/packages/{id}` history joined to `/delivery-tracking`, in THREE
 * screens, each with its own copy of the rules. Before that, two of those
 * screens rendered fixed status ladders that both INVENTED events (a package
 * with zero history rows printed seven completed steps) and DELETED them (no
 * rung existed for Released From Customs or Processing at our Warehouse).
 *
 * `GET /packages/{id}/timeline` now owns ordering, labels, icon keys, state,
 * duplicate collapse, last-mile composition and the single pending step. This
 * file is the mapping layer and nothing more: no reordering, no filtering, no
 * capping, no label rewriting. If a row is wrong now, it is wrong on the server
 * and gets fixed once for every platform.
 *
 * The one thing still decided here is which statuses offer the customer a way
 * out — see [NEEDS_HELP_STATUSES].
 */
internal object TrackJourney {

    /**
     * Statuses where the journey has gone wrong and the customer needs a human.
     *
     * Kemar 2026-07-26, asked whether these should appear at all: show them,
     * **with a Contact us action on that row**. A customer whose package is
     * detained at customs should be told by the app, not by silence — hiding it
     * means the rail goes quiet for days with no explanation and they call in
     * anyway.
     *
     * This is the last piece of journey policy left on the client. Offered to
     * BronzeMountain: if the server projection grows a per-entry flag, this set
     * goes away rather than being maintained on three platforms.
     */
    val NEEDS_HELP_STATUSES = setOf(
        10, // Detained at Customs
        15, // Uncollected Packages
        16, // Dangerous Goods
        19, // Returned to Merchant
    )

    fun needsHelp(statusId: Int?): Boolean = statusId != null && statusId in NEEDS_HELP_STATUSES

    /**
     * ⏳ TEMPORARY, AND DELIBERATELY ALMOST ALWAYS A NO-OP (Kemar, 2026-07-26).
     *
     * `package_status_datetime` is a **varchar**, and ~1,069 legacy rows hold
     * `YYYY/MM/DD`, which string-sorts AFTER `YYYY-MM-DD`. If the projection
     * orders on that column as text, a 2024 row can render last. Kemar's call
     * was to keep a client guard until BronzeMountain confirms the sort key.
     *
     * It does NOT reorder by a hardcoded status catalogue — that would be the
     * client second-guessing the server with a fixed list, the exact bug class
     * that invented and deleted rows here twice. It sorts on `at_iso`, which is
     * the **server's own normalized datetime** (measured on pre-staging:
     * `2026-07-07T20:10:07-04:00`), and only when that disagrees with the order
     * sent. On every package sampled live the two already agree, so this
     * returns the list untouched.
     *
     * Bail out entirely unless the shape is unambiguous: every dated row parses
     * and every undated row (a pending step) trails the dated ones. Anything
     * else passes through — a guard that fires on input it does not understand
     * is worse than no guard.
     *
     * DELETE THIS the moment the projection's ORDER BY is confirmed.
     */
    internal fun applyOrderGuard(entries: List<PackageTimelineEntry>): List<PackageTimelineEntry> {
        val lastDated = entries.indexOfLast { !it.atIso.isNullOrBlank() }
        if (lastDated < 0) return entries
        // An undated row before the final dated one means we cannot place it.
        if (entries.take(lastDated).any { it.atIso.isNullOrBlank() }) return entries

        val dated = entries.take(lastDated + 1)
        val instants = dated.map { runCatching { OffsetDateTime.parse(it.atIso) }.getOrNull() }
        if (instants.any { it == null }) return entries

        val sorted = dated.indices.sortedBy { instants[it] }        // stable: ties keep server order
        if (sorted == dated.indices.toList()) return entries        // the normal case — already right
        return sorted.map { dated[it] } + entries.drop(lastDated + 1)
    }

    /** Server entries → rendered rows, in the order given. */
    fun rows(entries: List<PackageTimelineEntry>): List<TrackRow> =
        applyOrderGuard(entries).mapIndexedNotNull { index, entry ->
            val label = entry.label?.trim()?.takeIf(String::isNotEmpty) ?: return@mapIndexedNotNull null
            TrackRow(
                key = entry.key?.trim()?.takeIf(String::isNotEmpty) ?: "entry_$index",
                label = label,
                state = entry.state?.trim()?.takeIf(String::isNotEmpty) ?: "done",
                at = entry.at?.trim()?.takeIf(String::isNotEmpty),
                statusId = entry.status,
                iconKey = entry.icon?.trim()?.takeIf(String::isNotEmpty),
                needsHelp = needsHelp(entry.status),
            )
        }

    /**
     * The server's glyph key → a drawable.
     *
     * ⚠️ The vocabulary is still moving: within minutes on 2026-07-26 the same
     * "Out for Delivery" row came back as `in_transit` on one package and
     * `out_for_delivery` on another. Both are mapped. An unrecognised key falls
     * back to the status catalogue when there is a status id, and to a neutral
     * package glyph otherwise — it never guesses at a shape. Asked BrightHarbor
     * to publish the closed set (#79945).
     */
    fun iconRes(iconKey: String?, statusId: Int?, dark: Boolean): Int = when (iconKey) {
        "drop_alerted" ->
            if (dark) R.drawable.ic_shipments_status_drop_alerted_dark else R.drawable.ic_shipments_status_drop_alerted
        "shipment_received" ->
            if (dark) R.drawable.ic_shipments_status_shipment_received_dark else R.drawable.ic_shipments_status_shipment_received
        "port_departure" ->
            if (dark) R.drawable.ic_shipments_status_port_departure_mia_dark else R.drawable.ic_shipments_status_port_departure_mia
        "port_arrived" ->
            if (dark) R.drawable.ic_shipments_status_arrived_port_jam_dark else R.drawable.ic_shipments_status_arrived_port_jam
        "customs_processing" ->
            if (dark) R.drawable.ic_shipments_status_processing_customs_dark else R.drawable.ic_shipments_status_processing_customs
        "customs_detained" ->
            if (dark) R.drawable.ic_shipments_status_detained_customs_dark else R.drawable.ic_shipments_status_detained_customs
        "customs_released" ->
            if (dark) R.drawable.ic_shipments_status_released_customs_dark else R.drawable.ic_shipments_status_released_customs
        "warehouse" ->
            if (dark) R.drawable.ic_shipments_status_processing_warehouse_dark else R.drawable.ic_shipments_status_processing_warehouse
        "ready_pickup" ->
            if (dark) R.drawable.ic_shipments_status_ready_for_pickup_dark else R.drawable.ic_shipments_status_ready_for_pickup
        "paid_ready_pickup" ->
            if (dark) R.drawable.ic_shipments_status_paid_ready_pickup_dark else R.drawable.ic_shipments_status_paid_ready_pickup
        "uncollected" ->
            if (dark) R.drawable.ic_shipments_status_uncollected_dark else R.drawable.ic_shipments_status_uncollected
        "dangerous_goods" ->
            if (dark) R.drawable.ic_shipments_status_dangerous_goods_dark else R.drawable.ic_shipments_status_dangerous_goods
        "returned_merchant" ->
            if (dark) R.drawable.ic_shipments_status_returned_merchant_dark else R.drawable.ic_shipments_status_returned_merchant
        "counter", "in_transit_counter" ->
            if (dark) R.drawable.ic_shipments_status_in_transit_counter_dark else R.drawable.ic_shipments_status_in_transit_counter
        // Last-mile legs.
        "dispatch" -> R.drawable.ic_shipments_status_dispatch
        // Both spellings have been observed for the same row; see the note above.
        "out_for_delivery", "in_transit" ->
            if (dark) R.drawable.ic_shipments_status_out_for_delivery_dark else R.drawable.ic_shipments_status_out_for_delivery
        "delivered" ->
            if (dark) R.drawable.ic_shipments_status_delivered_dark else R.drawable.ic_shipments_status_delivered
        else -> statusId
            ?.let { com.ga.airdrop.feature.shipments.ShipmentStatusCatalog.iconRes(it, dark) }
            ?: R.drawable.ic_packages
    }
}

/** One rendered row of the Track journey. */
internal data class TrackRow(
    val key: String,
    val label: String,
    val state: String,
    val at: String?,
    /** Warehouse status id, null on last-mile legs. */
    val statusId: Int?,
    /** The server's glyph key. */
    val iconKey: String? = null,
    /** This row is bad news — render a Contact us action beside it. */
    val needsHelp: Boolean = false,
)
