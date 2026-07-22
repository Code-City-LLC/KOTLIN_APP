package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterDeviceTokenRequestTest {
    @Test
    fun `registration carries installed app version and build number`() {
        val encoded = AirdropJson.encodeToString(
            RegisterDeviceTokenRequest(
                deviceToken = "fcm-token",
                deviceType = "android",
                deviceInfo = "Pixel",
                appVersion = "8.1",
                buildNumber = "22",
            )
        )
        val obj = AirdropJson.parseToJsonElement(encoded).jsonObject
        assertEquals("8.1", obj.getValue("app_version").jsonPrimitive.content)
        assertEquals("22", obj.getValue("build_number").jsonPrimitive.content)
        assertEquals("android", obj.getValue("device_type").jsonPrimitive.content)
    }
}
