package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.model.AirdropNotification
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [NotificationIconCatalog.actionTitle] parity with Swift
 * FigmaNotificationsListViewController.notificationActionTitle(for:) (:521-527):
 * first-match over the normalized type+title+body text.
 */
class NotificationActionTitleTest {

    private fun notif(type: String? = null, title: String = "", body: String = "") =
        AirdropNotification(id = "1", title = title, body = body, type = type)

    private fun title(n: AirdropNotification) = NotificationIconCatalog.actionTitle(n)

    @Test
    fun `invoice maps to Check Mail`() {
        assertEquals("Check Mail", title(notif(type = "invoice_required")))
        assertEquals("Check Mail", title(notif(title = "New Invoice Available")))
    }

    @Test
    fun `payment and storage fee map to View Payment`() {
        assertEquals("View Payment", title(notif(type = "payment_due")))
        assertEquals("View Payment", title(notif(type = "storage_fee")))
    }

    @Test
    fun `document maps to View Document`() {
        assertEquals("View Document", title(notif(type = "document_uploaded")))
    }

    @Test
    fun `address maps to Update Address`() {
        assertEquals("Update Address", title(notif(body = "Please update your address")))
    }

    @Test
    fun `unmatched falls back to View Details`() {
        assertEquals("View Details", title(notif(type = "ready_for_pickup")))
        assertEquals("View Details", title(notif(title = "Your package arrived")))
    }

    @Test
    fun `first-match ordering — invoice wins over payment`() {
        // Swift chain checks invoice before payment.
        assertEquals("Check Mail", title(notif(type = "invoice", body = "payment required")))
    }

    @Test
    fun `app update uses explicit store action`() {
        assertEquals("Update App", title(notif(type = "app_update")))
        assertEquals("Update App", title(notif(type = "force-update")))
    }
}
