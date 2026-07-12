package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AirdropNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationsRouteResolverTest {

    @Test
    fun `ready for pickup type without explicit route opens package details`() {
        val route = resolveNotificationRoute(
            AirdropNotification(
                id = "ready",
                title = "Package ready for pickup",
                type = "package_ready_for_pickup",
                referenceId = "ADX-240524",
                payload = mapOf("notification_type" to "package_ready_for_pickup"),
            )
        )

        assertEquals(Routes.packageDetails("ADX-240524"), route)
    }

    @Test
    fun `Swift notification type map handles package payment and profile destinations`() {
        assertEquals(
            Routes.packageDetails("PKG-1001"),
            resolveNotificationRoute(
                AirdropNotification(
                    id = "storage",
                    type = "storage_fee_reminder",
                    referenceId = "PKG-1001",
                )
            ),
        )
        assertEquals(
            Routes.PAYMENTS,
            resolveNotificationRoute(AirdropNotification(id = "payment", type = "payment_failed")),
        )
        assertEquals(
            Routes.PROFILE,
            resolveNotificationRoute(AirdropNotification(id = "profile", type = "address_required")),
        )
    }

    @Test
    fun `Swift screen aliases preserve existing route resolver behavior`() {
        assertEquals(
            Routes.packageDetails("ADX-240524"),
            resolveNotificationRoute("PackageDetailScreen", "ADX-240524"),
        )
        assertEquals(
            Routes.paymentPackageDetails("42"),
            resolveNotificationRoute("PaymentPackageDetailsView", "42"),
        )
        assertEquals(
            Routes.productPaymentDetails("43"),
            resolveNotificationRoute("ProductPaymentDetailsScreen", "43"),
        )
        assertEquals(
            Routes.orderDetails("44"),
            resolveNotificationRoute("orderDetails", "44"),
        )
        assertEquals(Routes.PAYMENTS, resolveNotificationRoute("PaymentScreen", null))
        assertEquals(Routes.PROFILE, resolveNotificationRoute("KYCScreen", null))
    }

    @Test
    fun `blank or unknown notifications still do not invent a destination`() {
        assertNull(resolveNotificationRoute(AirdropNotification(id = "unknown", type = "unknown")))
        assertNull(resolveNotificationRoute(route = "", referenceId = "ADX-240524"))
    }
}
