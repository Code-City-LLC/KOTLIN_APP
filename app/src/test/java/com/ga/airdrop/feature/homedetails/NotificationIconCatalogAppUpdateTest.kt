package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.R
import com.ga.airdrop.data.model.AirdropNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * An "App update available" row used to render with the PACKAGE BOX glyph and a
 * "View Details" button.
 *
 * Neither was harmless. The icon told the customer a new app version was a
 * parcel, and the label misdescribed the tap: that CTA leaves the app for the
 * Play Store (Routes.EXTERNAL_APP_UPDATE), it does not open a detail screen.
 * Both fell out of the same cause — the catalog had no case for this type at
 * all, so it dropped through to `else`.
 *
 * The server has used three spellings for the type, so all three are pinned.
 */
class NotificationIconCatalogAppUpdateTest {

    private fun notif(type: String) = AirdropNotification(
        id = "1",
        title = "App update available",
        body = "A new version of AirDrop is ready.",
        type = type,
    )

    @Test
    fun `every app-update type spelling gets the update icon, not the package box`() {
        listOf("AppUpdateView", "AppUpdateScreen", "app_update").forEach { type ->
            assertEquals(
                "$type must not render as a package",
                R.drawable.ic_notifications,
                NotificationIconCatalog.iconRes(notif(type), dark = false),
            )
            assertNotEquals(
                "$type fell through to the package-box default",
                R.drawable.ic_packages,
                NotificationIconCatalog.iconRes(notif(type), dark = false),
            )
        }
    }

    @Test
    fun `the CTA says Update App, because the tap leaves for the Play Store`() {
        listOf("AppUpdateView", "AppUpdateScreen", "app_update").forEach { type ->
            assertEquals("Update App", NotificationIconCatalog.actionTitle(notif(type)))
        }
    }

    /** The new first-position case must not capture ordinary shipment rows. */
    @Test
    fun `shipment notifications are unaffected`() {
        val delivered = AirdropNotification(
            id = "2",
            title = "Package delivered",
            body = "Your package was delivered.",
            type = "ShipmentView",
        )
        assertNotEquals(
            "a delivery must not be labelled as an app update",
            "Update App",
            NotificationIconCatalog.actionTitle(delivered),
        )
    }
}
