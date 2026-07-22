package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropNotification
import kotlinx.serialization.decodeFromString
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
    fun `positive package id wins over tracking preview reference`() {
        val route = resolveNotificationRoute(
            AirdropNotification(
                id = "package-id",
                type = "package_status_update",
                referenceId = "TRACK-PREVIEW-99",
                payload = mapOf(
                    "package_id" to "0042",
                    "tracking_code" to "TRACK-PREVIEW-99",
                ),
            ),
        )

        assertEquals(Routes.packageDetails("42"), route)
    }

    @Test
    fun `decoded top level package id wins over nested tracking and notification row id`() {
        val notification = AirdropJson.decodeFromString<AirdropNotification>(
            """
            {
              "id": "999999",
              "title": "Package update",
              "type": "package_status_update",
              "package_id": "0042",
              "data": {
                "screen": "PackageDetailsView",
                "tracking_code": "TRACK-PREVIEW-999999"
              }
            }
            """.trimIndent(),
        )

        assertEquals("999999", notification.id)
        assertEquals("42", notification.referenceId?.toIntOrNull()?.toString())
        assertEquals("0042", notification.payload["package_id"])
        assertEquals(
            Routes.packageDetails("42"),
            resolveNotificationRoute(notification),
        )
    }

    @Test
    fun `invalid package id falls back to exact encoded tracking alias`() {
        val route = resolveNotificationRoute(
            AirdropNotification(
                id = "tracking-alias",
                type = "package_status_update",
                referenceId = "0",
                payload = mapOf(
                    "package_id" to "0",
                    "tracking_code" to "TRACK/42",
                ),
            ),
        )

        assertEquals(Routes.packageDetails("TRACK%2F42"), route)
    }

    @Test
    fun `invalid package id without alias opens packages and never fabricates detail id`() {
        assertEquals(
            Routes.PACKAGES,
            resolveNotificationRoute(
                AirdropNotification(
                    id = "invalid-id",
                    type = "package_ready_for_pickup",
                    referenceId = "-7",
                    payload = mapOf("package_id" to "0"),
                ),
            ),
        )
        assertEquals(Routes.PACKAGES, resolveNotificationRoute("PackageDetailsView", "0"))
        assertEquals(Routes.PACKAGES, resolveNotificationRoute("PackageDetailsView", "-7"))
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
