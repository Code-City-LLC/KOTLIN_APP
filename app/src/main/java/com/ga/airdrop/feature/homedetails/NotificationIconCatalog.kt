package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.R
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.feature.shipments.ShipmentStatusCatalog

/**
 * Per-type notification glyphs — Swift FigmaNotificationsListViewController
 * notificationIcon(for:) (:527-583) ported rule-for-rule IN THE SAME ORDER
 * (the chain is first-match, so ordering is behavior). Ledger C5 / Kemar
 * #14729: every notification keeps its own specific icon; shipment-status
 * semantics reuse the duotone [ShipmentStatusCatalog] glyphs (never tint
 * them — a solid tint flattens the duotone, the §261 lesson).
 */
object NotificationIconCatalog {

    fun iconRes(notification: AirdropNotification, dark: Boolean): Int {
        val text = normalizedText(notification)
        return when {
            "invoice" in text ->
                if (dark) R.drawable.ic_notification_mail_dark else R.drawable.ic_notification_mail
            "paid" in text && ("pickup" in text || "ready_for_pickup" in text) ->
                ShipmentStatusCatalog.iconRes(18, dark)
            "ready_for_pickup" in text || "ready for pickup" in text ->
                ShipmentStatusCatalog.iconRes(7, dark)
            "shipment_received" in text || "package_received" in text || "received" in text ->
                ShipmentStatusCatalog.iconRes(2, dark)
            // Swift uses its settings-bell glyph here; Android's themed
            // equivalent is the drop-alerted status glyph (the settings bell
            // has no dark variant, and the dark-icon rule wins).
            "drop_alert" in text || "drop alert" in text ->
                ShipmentStatusCatalog.iconRes(1, dark)
            "departure" in text || "mia" in text -> ShipmentStatusCatalog.iconRes(3, dark)
            "arrived" in text || "jam" in text -> ShipmentStatusCatalog.iconRes(4, dark)
            "delivered" in text || "proof" in text -> ShipmentStatusCatalog.iconRes(8, dark)
            "detained" in text -> ShipmentStatusCatalog.iconRes(10, dark)
            "customs" in text ->
                if ("released" in text) ShipmentStatusCatalog.iconRes(5, dark)
                else ShipmentStatusCatalog.iconRes(9, dark)
            "transit" in text -> ShipmentStatusCatalog.iconRes(12, dark)
            "auction" in text || "sale" in text || "uncollected" in text -> ShipmentStatusCatalog.iconRes(17, dark)
            "dangerous" in text || "restricted" in text -> ShipmentStatusCatalog.iconRes(16, dark)
            "payment" in text || "storage_fee" in text -> R.drawable.ic_payments
            "document" in text ->
                if (dark) R.drawable.ic_more_documents_dark else R.drawable.ic_more_documents
            "promotion" in text || "promotional" in text -> R.drawable.ic_notifications
            else -> R.drawable.ic_packages
        }
    }

    /**
     * Per-type row CTA label — Swift FigmaNotificationsListViewController
     * notificationActionTitle(for:) (:521-527), first-match over the same
     * normalized text as [iconRes]. Rendered as the underlined orange action
     * label + right chevron at the foot of every notification card.
     */
    fun actionTitle(notification: AirdropNotification): String {
        val text = normalizedText(notification)
        return when {
            "invoice" in text -> "Check Mail"
            "payment" in text || "storage_fee" in text -> "View Payment"
            "document" in text -> "View Document"
            "address" in text -> "Update Address"
            else -> "View Details"
        }
    }

    /** Swift normalizedNotificationText: join fields + payload, "-"→"_", "/"→" ", lowercase. */
    internal fun normalizedText(n: AirdropNotification): String =
        (
            listOfNotNull(n.type, n.title, n.body, n.route, n.referenceId) +
                n.payload.flatMap { (key, value) -> listOf(key, value) }
            )
            .joinToString(" ")
            .replace("-", "_")
            .replace("/", " ")
            .lowercase()
}
