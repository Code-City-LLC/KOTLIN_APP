package com.ga.airdrop.feature.shipments

import androidx.compose.runtime.Composable
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.data.model.PackageStorage

/**
 * Storage fees on a package — renders Laravel's `storage` block
 * (`App\Services\StorageFeeService`, pre_staging 7123a3aa).
 *
 * ⚠️ RULE: **report, never recompute.** The warehouse writes the applied
 * charge into `package_additional_charges` and the customer is invoiced from
 * it. Laravel deliberately refuses to derive a second figure because it would
 * contradict the ledger and the invoice, and the client must hold the same
 * line — there is no rate x days arithmetic anywhere in this file. (The
 * `STORAGE_FEE_RATE = 0.50` constant that used to live on the server was dead
 * code; real effective rates measured $0.2968–$0.3859/day.)
 */
@Composable
internal fun StorageFeeCard(storage: PackageStorage, exchangeRate: Double) {
    val colors = AirdropTheme.colors
    val fee = storage.fee ?: 0.0
    val hasFee = fee > 0.0

    ShipmentsSectionCard(title = "Storage") {
        ShipmentsListRow(
            label = "Storage Fees",
            value = when {
                storage.writtenOff == true -> "Waived"
                hasFee -> ShipmentsFormat.usdJmd(fee, exchangeRate)
                else -> "None yet"
            },
            // Money owed reads as an alert; "Waived"/"None yet" must not.
            valueColor = if (hasFee && storage.writtenOff != true) {
                AlertPalette.Pending
            } else {
                colors.textDarkTitle
            },
        )
        storage.daysInStorage?.let { days ->
            ShipmentsListRow(
                label = "Days in Storage",
                value = if (days == 1) "1 day" else "$days days",
            )
        }
        ShipmentsListRow(
            label = "Status",
            value = storageStatusLine(storage),
            showDivider = false,
        )
    }
}

/**
 * The one line that tells the customer what happens next.
 *
 * Deliberately does NOT say "free" whenever a fee already exists: a real
 * pre-staging package (153906) is `in_free_period: true` AND carries an
 * applied `fee: "8.00"`, because the warehouse charge and the tier's free
 * window are independent. Telling that customer their storage is free while
 * they owe $8.00 would be a lie the invoice then contradicts.
 */
internal fun storageStatusLine(storage: PackageStorage): String {
    val hasFee = (storage.fee ?: 0.0) > 0.0
    val remaining = storage.daysUntilFees
    return when {
        storage.writtenOff == true -> "Waived — nothing to pay"
        storage.inFreePeriod == true && remaining != null && !hasFee ->
            if (remaining == 1) "1 free day left" else "$remaining free days left"
        // In the free window but already carrying a warehouse charge.
        storage.inFreePeriod == true && remaining != null ->
            if (remaining == 1) "Charge applied · 1 free day left" else "Charge applied · $remaining free days left"
        storage.accruing == true -> "Accruing daily"
        hasFee -> "Charge applied"
        else -> "Not accruing"
    }
}
