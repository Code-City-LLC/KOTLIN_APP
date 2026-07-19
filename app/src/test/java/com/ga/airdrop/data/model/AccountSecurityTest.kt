package com.ga.airdrop.data.model

import com.ga.airdrop.feature.more2.sessionSubtitle
import com.ga.airdrop.feature.more2.sessionTitle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Account-security wire pins: GET /user/sessions tolerates both `data`
 * and `sessions` keys (Swift parity), export response carries either a
 * download link or an email-when-ready message, and session rows label
 * themselves sensibly with partial data.
 */
class AccountSecurityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `sessions parse from the data key`() {
        val parsed = json.decodeFromString<ActiveSessionsResponse>(
            """{"data":[{"id":"s1","device_name":"Pixel 7","platform":"Android",
                "last_seen_ip":"1.2.3.4","last_seen_at":"2026-07-19","is_current":true}]}""",
        )
        assertEquals(1, parsed.all.size)
        assertEquals("Pixel 7", parsed.all.first().deviceName)
        assertEquals(true, parsed.all.first().isCurrent)
    }

    @Test
    fun `sessions parse from the legacy sessions key`() {
        val parsed = json.decodeFromString<ActiveSessionsResponse>(
            """{"sessions":[{"id":"s2"}]}""",
        )
        assertEquals(listOf("s2"), parsed.all.map { it.id })
    }

    @Test
    fun `empty body yields an empty list, not a crash`() {
        assertEquals(0, json.decodeFromString<ActiveSessionsResponse>("{}").all.size)
    }

    @Test
    fun `export response models link and message forms`() {
        val link = json.decodeFromString<ExportPersonalDataResponse>(
            """{"download_url":"https://x/export.zip"}""",
        )
        assertEquals("https://x/export.zip", link.downloadUrl)
        assertNull(link.message)

        val queued = json.decodeFromString<ExportPersonalDataResponse>(
            """{"message":"We'll email you when it's ready."}""",
        )
        assertEquals("We'll email you when it's ready.", queued.message)
    }

    @Test
    fun `session rows label sensibly with partial data`() {
        val full = ActiveSession(
            id = "a",
            deviceName = "Pixel 7",
            platform = "Android",
            lastSeenAt = "2026-07-19",
            lastSeenIp = "1.2.3.4",
        )
        assertEquals("Pixel 7", sessionTitle(full))
        assertEquals("Android · Last seen 2026-07-19 · 1.2.3.4", sessionSubtitle(full))

        val bare = ActiveSession(id = "b")
        assertEquals("Session b", sessionTitle(bare))
        assertEquals("", sessionSubtitle(bare))

        val platformOnly = ActiveSession(id = "c", platform = "iOS")
        assertEquals("iOS", sessionTitle(platformOnly))
    }
}
