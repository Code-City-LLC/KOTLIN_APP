package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two silent-failure bugs salvaged from the stale PR #167 branch, both verified
 * against `main` before porting rather than taken on the branch's word.
 */
class NotificationSalvageTest {

    /**
     * ⚠️ `data` arrives as an OBJECT over the REST inbox and as a JSON STRING
     * over FCM, whose data payload is `Map<String, String>` so every value is
     * stringified in transit.
     *
     * The old `obj["data"] as? JsonObject` returned null for the string form and
     * silently dropped the whole payload — route, package reference, everything.
     * The notification still rendered, just inert with no deep link, so nothing
     * ever looked broken enough to investigate.
     */
    @Test
    fun `a stringified data payload decodes instead of vanishing`() {
        val json = """
            {"id":"1","title":"Package update","description":"Arrived",
             "data":"{\"screen\":\"PackageDetails\",\"package_id\":\"9987\"}"}
        """.trimIndent()

        val n = AirdropJson.decodeFromString(AirdropNotificationSerializer, json)

        assertEquals("the deep link must survive a stringified payload", "PackageDetails", n.route)
        assertEquals("9987", n.referenceId)
        assertTrue("payload keys must be readable too", n.payload.containsKey("package_id"))
    }

    /** The object form must keep working exactly as before. */
    @Test
    fun `an object data payload still decodes`() {
        val json = """
            {"id":"2","title":"Package update","description":"Arrived",
             "data":{"screen":"PackageDetails","package_id":"1234"}}
        """.trimIndent()

        val n = AirdropJson.decodeFromString(AirdropNotificationSerializer, json)

        assertEquals("PackageDetails", n.route)
        assertEquals("1234", n.referenceId)
    }

    /** Malformed embedded JSON must degrade, never crash the inbox. */
    @Test
    fun `malformed embedded json degrades to no payload`() {
        val json = """{"id":"3","title":"T","description":"B","data":"{not valid json"}"""

        val n = AirdropJson.decodeFromString(AirdropNotificationSerializer, json)

        assertEquals("T", n.title)
        assertTrue("a bad payload must not take the notification down", n.payload.isEmpty())
    }

    /**
     * ⚠️ THE TRAP THIS PINS: `AirdropJson` leaves `encodeDefaults` FALSE, so any
     * property carrying a default value is OMITTED from the serialized body.
     *
     * If someone "tidies" `appVersion`/`buildNumber` to `= null`, this compiles,
     * reads fine, and silently stops sending them — the server goes back to not
     * knowing which devices run a stale build, with no error anywhere. This test
     * fails the moment that happens.
     */
    @Test
    fun `device token registration actually puts app version on the wire`() {
        val body = AirdropJson.encodeToString(
            RegisterDeviceTokenRequest.serializer(),
            RegisterDeviceTokenRequest(
                deviceToken = "tok",
                deviceType = "android",
                appVersion = "3.1.2",
                buildNumber = 24,
                deviceInfo = null,
            ),
        )

        assertTrue("app_version missing from the request body: $body", body.contains("\"app_version\""))
        assertTrue("build_number missing from the request body: $body", body.contains("\"build_number\""))
        assertTrue(body.contains("3.1.2"))
        assertTrue(body.contains("24"))
    }
}
