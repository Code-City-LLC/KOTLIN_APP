package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.PackageInvoicesMutationResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * "Your invoice uploaded" must not be printed when the server rejected it.
 *
 * `uploadPackageInvoices` was `.data?.documents ?: emptyList()` with no
 * `success` guard. A null-check cannot save that: when `data` is null,
 * `DataEnvelopeSerializer` re-decodes the WHOLE envelope as the payload
 * (Envelopes.kt:131-135), and with `ignoreUnknownKeys = true` plus defaulted
 * fields that fallback CANNOT fail. So a soft-fail body produced a non-null
 * payload with no documents and the call returned `Result.success(emptyList())`.
 *
 * The customer is told the upload worked. It did not. **Their package sits at
 * customs waiting on a document nobody has**, and the one screen that would
 * tell them shows the upload as done — so they have no reason to retry.
 *
 * ⚠️ This test exists because my own revert-check caught that I shipped the fix
 * with NO guard at all. Reverting the repository to the buggy one-liner left the
 * suite entirely green, which is the same false-coverage defect this codebase
 * keeps producing.
 */
class InvoiceUploadFalseSuccessTest {

    private fun repo(json: String) = PackagesRepository(
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "uploadPackageInvoices" -> AirdropJson.decodeFromString(
                    PackageInvoicesMutationResponse.serializer(), json,
                )
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService,
    )

    @Test
    fun `a rejected upload is a FAILURE, not an empty document list`() {
        val result = runBlocking {
            repo("""{"success":false,"message":"Invoice must be a PDF or image.","data":null}""")
                .uploadPackageInvoices("41", emptyList())
        }

        assertTrue(
            "a rejected upload must NOT report success — the customer would " +
                "stop retrying and their package stays stuck at customs. Got: $result",
            result.isFailure,
        )
        assertEquals(
            "and the server's own reason must reach them, not a generic message",
            "Invoice must be a PDF or image.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `an absent data key is a failure too`() {
        val result = runBlocking {
            repo("""{"success":false,"message":"Unauthorized"}""")
                .uploadPackageInvoices("41", emptyList())
        }
        assertTrue("got: $result", result.isFailure)
    }

    @Test
    fun `a success envelope with no documents list is a contract violation, not an empty upload`() {
        val result = runBlocking {
            repo("""{"success":true,"data":{}}""").uploadPackageInvoices("41", emptyList())
        }
        assertTrue(
            "success with no documents means we cannot confirm what was stored. " +
                "Got: $result",
            result.isFailure,
        )
    }

    @Test
    fun `a real upload still succeeds and returns its documents`() {
        // The guard must not break the working path — otherwise it has traded a
        // silent failure for a loud one on every customer.
        val result = runBlocking {
            repo(
                """{"success":true,"data":{"documents":[
                     {"id":9,"file_name":"invoice.pdf"}
                   ]}}""",
            ).uploadPackageInvoices("41", emptyList())
        }

        assertTrue("a genuine upload must come through. Got: $result", result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }

    @Test
    fun `a genuine EMPTY documents list is allowed through`() {
        // Distinct from the missing-key case above: the server explicitly said
        // the list is empty, which is an answer rather than a gap.
        val result = runBlocking {
            repo("""{"success":true,"data":{"documents":[]}}""")
                .uploadPackageInvoices("41", emptyList())
        }
        assertTrue("got: $result", result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }
}
