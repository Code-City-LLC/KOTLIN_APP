package com.ga.airdrop.core.push

import com.ga.airdrop.data.model.AirdropNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateNoticeTest {
    @Test
    fun `all agreed update types accept a newer Android version`() {
        listOf("app_update", "app_update_available", "update_available", "force_update")
            .forEach { type ->
                val result = evaluateAppUpdateNotification(
                    updateNotification(type = type, latest = "8.1"),
                    installedVersion = "8.0",
                )
                assertTrue("$type should be eligible", result is AppUpdateEvaluation.Eligible)
            }
    }

    @Test
    fun `equal older wrong-platform and malformed updates fail closed`() {
        val suppressed = listOf(
            updateNotification(latest = "8.0"),
            updateNotification(latest = "7.9"),
            updateNotification(latest = "8.1", platform = "ios"),
            updateNotification(latest = "version-eight"),
            updateNotification(latest = "8.1", minimum = "not-a-version"),
            updateNotification(latest = ""),
        )
        suppressed.forEach { notification ->
            assertEquals(
                AppUpdateEvaluation.Suppressed,
                evaluateAppUpdateNotification(notification, installedVersion = "8.0"),
            )
            assertFalse(notification.isVisibleForInstalledApp(installedVersion = "8.0"))
        }
    }

    @Test
    fun `ordinary notifications remain visible`() {
        val notification = AirdropNotification(id = "normal", type = "package_delivered")
        assertEquals(
            AppUpdateEvaluation.NotAnUpdate,
            evaluateAppUpdateNotification(notification, installedVersion = "8.0"),
        )
        assertTrue(notification.isVisibleForInstalledApp(installedVersion = "8.0"))
    }

    @Test
    fun `nested data-payload push is accepted and direct keys win conflicts`() {
        val nested = evaluateAppUpdatePush(
            data = mapOf(
                "data_payload" to
                    """{"type":"app_update","platform":"android","latest_version":"8.1"}""",
            ),
            installedVersion = "8.0",
        )
        assertTrue(nested is AppUpdateEvaluation.Eligible)

        val transitionalMinimum = evaluateAppUpdatePush(
            data = mapOf(
                "type" to "app_update_available",
                "platform" to "android",
                "latest_version" to "8.1",
                "minimum_version" to "8.0",
            ),
            installedVersion = "8.0",
        )
        assertEquals("8.0", (transitionalMinimum as AppUpdateEvaluation.Eligible).minimumSupportedVersion)

        val directConflict = evaluateAppUpdatePush(
            data = mapOf(
                "data_payload" to
                    """{"type":"app_update","platform":"android","latest_version":"8.1"}""",
                "latest_version" to "8.0",
            ),
            installedVersion = "8.0",
        )
        assertEquals(AppUpdateEvaluation.Suppressed, directConflict)
    }

    @Test
    fun `numeric comparison handles unequal widths and rejects labels`() {
        assertEquals(1, compareNumericVersions("8.10", "8.9"))
        assertEquals(0, compareNumericVersions("8", "8.0.0"))
        assertEquals(-1, compareNumericVersions("7.9.9", "8"))
        assertEquals(null, compareNumericVersions("8.0-beta", "8.0"))
    }

    @Test
    fun `only the fixed production Play listing is allowlisted`() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.ga.airdrop.app",
            GOOGLE_PLAY_UPDATE_URL,
        )
    }

    private fun updateNotification(
        type: String = "app_update",
        latest: String,
        platform: String = "android",
        minimum: String? = null,
    ) = AirdropNotification(
        id = "update-$latest-$platform",
        type = type,
        payload = buildMap {
            put("platform", platform)
            put("latest_version", latest)
            minimum?.let { put("minimum_supported_version", it) }
            put("url", "https://attacker.invalid/not-used")
        },
    )
}
