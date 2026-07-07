package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.R
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.feature.shipments.ShipmentStatusCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ledger C5 / Kemar #14729 regression guard: per-type notification icons,
 * ported rule-for-rule from Swift FigmaNotificationsListViewController
 * notificationIcon(for:) (:527-583). The chain is first-match, so these pin
 * the ORDER as well as the mappings (e.g. "invoice" beats "received";
 * "paid + pickup" beats plain "ready for pickup").
 */
class NotificationIconCatalogTest {

    private fun n(
        type: String? = null,
        title: String = "",
        body: String = "",
        payload: Map<String, String> = emptyMap(),
    ) = AirdropNotification(id = "1", title = title, body = body, type = type, payload = payload)

    @Test
    fun `invoice wins over other status words - Swift order`() {
        val res = NotificationIconCatalog.iconRes(
            n(type = "invoice_required", body = "Package received, invoice required"),
            dark = false,
        )
        assertEquals(R.drawable.ic_notification_mail, res)
    }

    @Test
    fun `paid and ready for pickup beats plain ready for pickup`() {
        val res = NotificationIconCatalog.iconRes(
            n(type = "package_paid_ready_for_pickup"),
            dark = false,
        )
        assertEquals(ShipmentStatusCatalog.iconRes(18, dark = false), res)
    }

    @Test
    fun `ready for pickup maps to status 7 glyph`() {
        val res = NotificationIconCatalog.iconRes(n(title = "Ready for Pickup"), dark = true)
        assertEquals(ShipmentStatusCatalog.iconRes(7, dark = true), res)
    }

    @Test
    fun `payload-only ready for pickup type maps to status 7 glyph`() {
        val res = NotificationIconCatalog.iconRes(
            n(payload = mapOf("notification_type" to "package_ready_for_pickup")),
            dark = false,
        )
        assertEquals(ShipmentStatusCatalog.iconRes(7, dark = false), res)
    }

    @Test
    fun `delivered maps to delivery check glyph distinct from ready for pickup`() {
        val delivered = NotificationIconCatalog.iconRes(n(type = "package_delivered"), false)
        val ready = NotificationIconCatalog.iconRes(n(type = "package_ready_for_pickup"), false)
        assertEquals(ShipmentStatusCatalog.iconRes(8, false), delivered)
        assertEquals(ShipmentStatusCatalog.iconRes(7, false), ready)
    }

    @Test
    fun `customs released vs processing are distinct`() {
        val released = NotificationIconCatalog.iconRes(n(body = "Released from customs"), false)
        val processing = NotificationIconCatalog.iconRes(n(body = "Processing at customs"), false)
        assertEquals(ShipmentStatusCatalog.iconRes(5, false), released)
        assertEquals(ShipmentStatusCatalog.iconRes(9, false), processing)
    }

    @Test
    fun `hyphenated and slashed types normalize like Swift`() {
        // Swift normalizes "-"→"_" and "/"→" " before matching.
        val res = NotificationIconCatalog.iconRes(n(type = "drop-alert"), dark = false)
        assertEquals(ShipmentStatusCatalog.iconRes(1, dark = false), res)
    }

    @Test
    fun `payment and document and promotion map to their glyphs`() {
        assertEquals(
            R.drawable.ic_payments,
            NotificationIconCatalog.iconRes(n(type = "payment_required"), false),
        )
        assertEquals(
            R.drawable.ic_more_documents,
            NotificationIconCatalog.iconRes(n(body = "New document uploaded"), false),
        )
        assertEquals(
            R.drawable.ic_notifications,
            NotificationIconCatalog.iconRes(n(title = "New promotion!"), false),
        )
        assertEquals(
            R.drawable.ic_notifications,
            NotificationIconCatalog.iconRes(n(type = "promotional"), false),
        )
    }

    @Test
    fun `unknown types fall back to the packages glyph like Swift`() {
        assertEquals(
            R.drawable.ic_packages,
            NotificationIconCatalog.iconRes(n(title = "Welcome to Airdrop"), false),
        )
    }
}
