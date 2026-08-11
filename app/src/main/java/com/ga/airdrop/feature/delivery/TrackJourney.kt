package com.ga.airdrop.feature.delivery

import com.ga.airdrop.R
import com.ga.airdrop.data.model.PackageTimelineEntry

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
     * Server entries → rendered rows, in the order given.
     *
     * ⚠️ THIS USED TO PAPER OVER CORRUPTION, AND #95189's COMMIT MESSAGE SAID
     * IT NO LONGER DID BEFORE THAT WAS TRUE. @Codex-CodexKotlinAudit #95347
     * caught the inconsistency: the repository guards landed, this did not.
     *
     * Three fallbacks are gone:
     *  - a blank label DROPPED the row, so a real recorded event vanished from
     *    the customer's journey with nothing to notice it;
     *  - a missing key was faked as `entry_<index>`, which is not an identity —
     *    it changes when the list reorders, so per-row state attaches to the
     *    wrong row;
     *  - a missing state defaulted to `"done"`, telling the customer a step
     *    COMPLETED that the server never reported.
     *
     * `DeliveryTrackingRepository.packageTimeline` now rejects every one of
     * those before they reach here, so this is a pure mapper. It stays strict
     * anyway: a renderer that silently repairs its input is how the repository
     * guard becomes pointless the day someone feeds this from a new source.
     */
    fun rows(entries: List<PackageTimelineEntry>): List<TrackRow> =
        entries.map { entry ->
            TrackRow(
                key = requireNotNull(entry.key?.trim()?.takeIf(String::isNotEmpty)) {
                    "timeline entry has no key; the repository must reject this"
                },
                label = requireNotNull(entry.label?.trim()?.takeIf(String::isNotEmpty)) {
                    "timeline entry has no label; the repository must reject this"
                },
                state = requireNotNull(entry.state?.takeIf { it in TRACK_ROW_STATES }) {
                    "timeline entry state is not done|current|pending"
                },
                at = entry.at?.trim()?.takeIf(String::isNotEmpty),
                statusId = entry.status,
                iconKey = entry.icon?.trim()?.takeIf(String::isNotEmpty),
                needsHelp = needsHelp(entry.status),
            )
        }

    /** Exact server vocabulary — same literals iOS's DeliveryStageState holds. */
    private val TRACK_ROW_STATES = setOf("done", "current", "pending")

    /**
     * The server's glyph key → a drawable. Keys are `App\Support\StatusIcons`
     * (`KEYS` + `DELIVERY_ICONS`), published by BronzeMountain in #80240 and
     * read back out of the deployed source before this mapping was written.
     *
     * ⚠️ I HAD `in_transit` AND `out_for_delivery` COLLAPSED ONTO ONE ICON.
     * That was wrong, and it was my assumption rather than a server
     * inconsistency. I saw both keys arrive for rows I read as the same event
     * and concluded the vocabulary was still settling. They are two different
     * events and a single package can have both:
     *
     *   in_transit       package status 12, In-Transit to counter — a warehouse
     *                    movement, no driver involved. Truck + clock.
     *   out_for_delivery a DELIVERY LEG from package_deliveries.out_for_delivery_at
     *                    — a driver physically has the package.
     *
     * Collapsing them showed the driver glyph for a warehouse movement and lost
     * information the customer needs. They stay visually distinct.
     *
     * The set is CLOSED. A key outside it means the server added one without
     * telling us: fall back to the status catalogue when there is a status id,
     * a neutral package glyph otherwise, and raise it — never quietly invent a
     * mapping. (This is also why `counter`/`in_transit_counter` are gone: I made
     * those two keys up, and a branch for a key the server cannot send reads
     * like a contract that exists.)
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
        "detained" ->
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
        "dangerous" ->
            if (dark) R.drawable.ic_shipments_status_dangerous_goods_dark else R.drawable.ic_shipments_status_dangerous_goods
        "returned" ->
            if (dark) R.drawable.ic_shipments_status_returned_merchant_dark else R.drawable.ic_shipments_status_returned_merchant
        "auction" ->
            if (dark) R.drawable.ic_shipments_status_auction_dark else R.drawable.ic_shipments_status_auction
        // Status 12 — a warehouse movement to the counter, NOT a driver leg.
        //
        // ⚠️ THE ONE AUTHORISED DUPLICATE GLYPH. DO NOT "FIX" THIS.
        // Kemar, 2026-07-26, verbatim: "In transit to counter can be the same as
        // out for delivery. Why? Because we hardly ever use that update. When
        // the time comes, we will update it, but we hardly ever use it."
        // Figma node 40000692-4169 is assigned to BOTH rows by the owner.
        //
        // The KEY stays distinct — only the artwork is shared. Status 12 is a
        // warehouse movement and out_for_delivery is a driver leg; merging the
        // statuses would be a real defect, and this pair is the one that looks
        // like duplication and is not. Restoring a dedicated glyph later is a
        // one-line change back to ic_shipments_status_in_transit_counter, which
        // is kept in res/drawable for exactly that day.
        "in_transit" ->
            if (dark) R.drawable.ic_shipments_status_out_for_delivery_dark else R.drawable.ic_shipments_status_out_for_delivery
        // Last-mile legs (StatusIcons::DELIVERY_ICONS). These carry no status id.
        "dispatch" -> R.drawable.ic_shipments_status_dispatch
        "out_for_delivery" ->
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
