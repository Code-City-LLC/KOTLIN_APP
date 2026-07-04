package com.ga.airdrop.data

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.Package
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Payment
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Laravel returns numbers-as-strings and several envelope shapes; these tests
 * pin the tolerant decoding the Swift app relies on in production.
 */
class FlexibleDecodingTest {

    @Test
    fun `payment decodes string amounts and ids`() {
        val json = """
            {"id":"123","invoice_id":"INV-9","payment_type":"package",
             "total_amount":"1,550.50","tracking_code":"ARD00000000042",
             "package_id":"77","order_id":"12","exchange_rate":"156.5"}
        """.trimIndent()
        val payment = AirdropJson.decodeFromString(Payment.serializer(), json)
        assertEquals(123, payment.id)
        assertEquals(1550.50, payment.totalAmount!!, 0.001)
        assertEquals(77, payment.packageId)
        assertEquals(12, payment.orderId)
        assertEquals(156.5, payment.exchangeRate!!, 0.001)
    }

    @Test
    fun `login response unwraps data envelope`() {
        val wrapped = """{"message":"ok","data":{"token":"abc123","token_type":"Bearer"}}"""
        val flat = """{"token":"xyz789"}"""
        assertEquals("abc123", AirdropJson.decodeFromString(LoginResponse.serializer(), wrapped).token)
        assertEquals("xyz789", AirdropJson.decodeFromString(LoginResponse.serializer(), flat).token)
    }

    @Test
    fun `user decodes legacy flat keys and nested tier`() {
        val json = """
            {"user_first_name":"Kemar","last_name":"C","email":"t@x.com",
             "customer_tier":{"name":"Gold Priority"}}
        """.trimIndent()
        val user = AirdropJson.decodeFromString(AirdropUser.serializer(), json)
        assertEquals("Kemar", user.firstName)
        assertEquals("Gold Priority", user.customerTierName)
    }

    @Test
    fun `user tier accepts plain string`() {
        val user = AirdropJson.decodeFromString(
            AirdropUser.serializer(),
            """{"first_name":"A","customer_tier":"Bronze Saver"}""",
        )
        assertEquals("Bronze Saver", user.customerTierName)
    }

    @Test
    fun `paginated accepts bare array`() {
        val result = AirdropJson.decodeFromString(
            Paginated.serializer(Int.serializer()),
            "[1,2,3]",
        )
        assertEquals(listOf(1, 2, 3), result.items)
    }

    @Test
    fun `paginated accepts data-wrapped array`() {
        val result = AirdropJson.decodeFromString(
            Paginated.serializer(Int.serializer()),
            """{"data":[4,5]}""",
        )
        assertEquals(listOf(4, 5), result.items)
    }

    @Test
    fun `aircoins status unwraps data and mirrors available into balance`() {
        val status = AirdropJson.decodeFromString(
            AirCoinsStatus.serializer(),
            """{"data":{"accumulated":"120","redeemed":"20","available":"100"}}""",
        )
        assertEquals(100, status.available)
        assertEquals(100, status.balance)
        assertEquals(120, status.accumulated)
    }

    @Test
    fun `package decodes flexible fields without crashing on unknowns`() {
        val pkg = AirdropJson.decodeFromString(
            Package.serializer(),
            """
            {"id":"9","tracking_code":"ARD1","shipping_method":"Express",
             "status":"7","status_name":"Ready for Pickup",
             "weight_lbs":"12.5","unknown_future_field":{"x":1}}
            """.trimIndent(),
        )
        assertEquals(9, pkg.id)
        assertEquals("Express", pkg.shippingMethod)
        assertEquals(12.5, pkg.weightLbs!!, 0.001)
        assertEquals("Ready for Pickup", pkg.statusName)
    }

    @Test
    fun `malformed optional fields decode to null not crash`() {
        val payment = AirdropJson.decodeFromString(
            Payment.serializer(),
            """{"id":1,"total_amount":null,"payment_date":null}""",
        )
        assertNotNull(payment.id)
        assertNull(payment.totalAmount)
        assertTrue(payment.displayTitle.isNotEmpty())
    }
}
