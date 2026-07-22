package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirdropNotificationDecodingTest {

    @Test
    fun `decodes Swift notification type and tracking aliases from payload`() {
        val notification = AirdropJson.decodeFromString<AirdropNotification>(
            """
            {
              "title": "Package ready for pickup",
              "description": "Your package ADX-240524 is ready.",
              "notification_type": "package_ready_for_pickup",
              "is_read": 0,
              "created_at": "2026-07-07T10:30:00Z",
              "data": {
                "tracking_code": "ADX-240524",
                "screen": "PackageDetailScreen",
                "notification_type": "package_ready_for_pickup"
              }
            }
            """.trimIndent()
        )

        assertEquals("package_ready_for_pickup", notification.type)
        assertEquals("PackageDetailScreen", notification.route)
        assertEquals("ADX-240524", notification.referenceId)
        assertEquals("package_ready_for_pickup", notification.payload["notification_type"])
        assertEquals("Package ready for pickup", notification.title)
        assertTrue(notification.id.startsWith("synthetic."))
    }

    @Test
    fun `payload package id wins before tracking aliases like Swift`() {
        val notification = AirdropJson.decodeFromString<AirdropNotification>(
            """
            {
              "id": "n-2",
              "title": "Package delivered",
              "type": "package_delivered",
              "tracking_code": "TOP-TRACKING",
              "data": {
                "packageId": "12345",
                "package_couirer_number": "TYPO-TRACKING"
              }
            }
            """.trimIndent()
        )

        assertEquals("12345", notification.referenceId)
        assertEquals("12345", notification.payload["packageId"])
        assertEquals("TYPO-TRACKING", notification.payload["package_couirer_number"])
    }

    @Test
    fun `decodes update contract from data-payload and top-level aliases`() {
        val nested = AirdropJson.decodeFromString<AirdropNotification>(
            """
            {
              "id": "update-1",
              "data_payload": {
                "type": "app_update_available",
                "platform": "android",
                "latest_version": "8.1",
                "minimum_supported_version": "8.0"
              }
            }
            """.trimIndent()
        )
        assertEquals("app_update_available", nested.type)
        assertEquals("android", nested.payload["platform"])
        assertEquals("8.1", nested.payload["latest_version"])

        val topLevel = AirdropJson.decodeFromString<AirdropNotification>(
            """
            {
              "id": "update-2",
              "type": "app_update",
              "platform": "android",
              "latestVersion": 9.0
            }
            """.trimIndent()
        )
        assertEquals("android", topLevel.payload["platform"])
        assertEquals("9.0", topLevel.payload["latestVersion"])

        val encodedWrapper = AirdropJson.decodeFromString<AirdropNotification>(
            """
            {
              "id": "update-3",
              "data_payload": "{\"type\":\"app_update\",\"platform\":\"android\",\"latest_version\":\"9.1\"}"
            }
            """.trimIndent()
        )
        assertEquals("app_update", encodedWrapper.type)
        assertEquals("9.1", encodedWrapper.payload["latest_version"])
    }
}
