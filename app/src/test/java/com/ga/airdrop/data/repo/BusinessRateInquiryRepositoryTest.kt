package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiFactory
import com.ga.airdrop.data.model.BusinessRateInquiryRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class BusinessRateInquiryRepositoryTest {
    private val owner = AuthTokenStore.RequestProvenance(42L, "session-a", 1)
    private val requestId = "9717246e-2049-4f67-a740-c9b6f64a851d"

    private class Transport(var status: Int = 201, var payload: String =
        """{"success":true,"data":{"message_id":123,"action":"created"}}""",
    ) {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        val repository = BusinessRateInquiryRepository(
            AirdropApiFactory.create(
                "https://example.test/api/v1/",
                OkHttpClient.Builder().addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    bodies += Buffer().also { request.body?.writeTo(it) }.readUtf8()
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(status).message("Fixture")
                        .body(payload.toResponseBody("application/json".toMediaType())).build()
                }.build(),
            ),
        )
    }

    @Test fun airDropSubmissionPreservesWireFieldsAndAccountGeneration() = runTest {
        val transport = Transport()
        val result = transport.repository.submit(
            BusinessRateInquiryRequest("Jordan Imports", requestId, airDropMonthlyPounds = 400.0), owner,
        ).getOrThrow()
        val wire = transport.requests.single()
        val body = Json.parseToJsonElement(transport.bodies.single()).jsonObject
        assertEquals("POST", wire.method)
        assertEquals("/api/v1/business-rate-inquiries", wire.url.encodedPath)
        assertEquals("42", wire.header(AuthTokenStore.REQUEST_REVISION_HEADER))
        assertEquals("session-a", wire.header(AuthTokenStore.REQUEST_SESSION_ID_HEADER))
        assertEquals("Jordan Imports", body.getValue("business_name").jsonPrimitive.content)
        assertEquals(requestId, body.getValue("client_request_id").jsonPrimitive.content)
        assertEquals("400.0", body.getValue("airdrop_monthly_lb").jsonPrimitive.content)
        assertFalse(body.containsKey("seadrop_monthly_cubic_feet"))
        assertEquals(123, result.messageId)
        assertEquals("created", result.action)
    }

    @Test fun seaDropOnlyAndUpdatedResponseAreSupported() = runTest {
        val transport = Transport(200, """{"success":true,"data":{"message_id":123,"action":"updated"}}""")
        val result = transport.repository.submit(
            BusinessRateInquiryRequest("Jordan Imports", requestId, seaDropMonthlyCubicFeet = 106.7), owner,
        ).getOrThrow()
        val body = Json.parseToJsonElement(transport.bodies.single()).jsonObject
        assertFalse(body.containsKey("airdrop_monthly_lb"))
        assertEquals("106.7", body.getValue("seadrop_monthly_cubic_feet").jsonPrimitive.content)
        assertEquals("updated", result.action)
    }

    @Test fun retryKeepsTheCallersRequestIdAndDoesNotRetryAutomatically() = runTest {
        val transport = Transport(503, """{"message":"Temporarily unavailable"}""")
        val request = BusinessRateInquiryRequest("Jordan Imports", requestId, airDropMonthlyPounds = 400.0)
        assertTrue(transport.repository.submit(request, owner).isFailure)
        assertEquals(1, transport.requests.size)
        transport.status = 201
        transport.payload = """{"success":true,"data":{"message_id":123,"action":"created"}}"""
        assertTrue(transport.repository.submit(request, owner).isSuccess)
        assertEquals(transport.bodies[0], transport.bodies[1])
    }

    @Test fun serverValidationIsReportedAsFailure() = runTest {
        val transport = Transport(422, """{"message":"Enter a qualifying monthly volume."}""")
        val result = transport.repository.submit(
            BusinessRateInquiryRequest("Jordan Imports", requestId, airDropMonthlyPounds = 399.9), owner,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("qualifying monthly volume"))
    }

    @Test fun unconfirmedOrMalformedResponsesNeverClaimSuccess() = runTest {
        val payloads = listOf(
            """{"success":false,"message":"Not stored","data":{"message_id":123,"action":"created"}}""",
            """{"success":true,"data":null}""",
            """{"success":true,"data":{"message_id":0,"action":"created"}}""",
            """{"success":true,"data":{"message_id":123,"action":"pending"}}""",
            """{"data":{"message_id":123,"action":"created"}}""",
        )
        for (payload in payloads) {
            val result = Transport(200, payload).repository.submit(
                BusinessRateInquiryRequest("Jordan Imports", requestId, airDropMonthlyPounds = 400.0), owner,
            )
            assertTrue(payload, result.isFailure)
        }
    }
}
