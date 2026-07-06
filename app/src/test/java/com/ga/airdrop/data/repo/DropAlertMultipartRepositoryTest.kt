package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.UploadFile
import com.ga.airdrop.data.model.DropAlertResponse
import com.ga.airdrop.data.model.DropAlertShippingMethod
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DropAlertMultipartRepositoryTest {

    @Test
    fun createDropAlertBuildsSwiftMultipartFieldsAndInvoiceParts() = runBlocking {
        val capture = CapturedDropAlertRequest()
        val service = dropAlertService(capture)
        val repository = PackagesRepository(service)

        val result = repository.createDropAlert(
            courierNumber = "1Z999",
            shipper = "Amazon",
            shippingMethod = DropAlertShippingMethod.SEADROP_STANDARD,
            store = "UPS",
            packageAmount = "84.50",
            consignee = "Kerry Smith",
            description = null,
            invoiceFiles = listOf(
                UploadFile(
                    fileName = "invoice-a.pdf",
                    mimeType = "application/pdf",
                    bytes = "PDF-A".encodeToByteArray(),
                ),
                UploadFile(
                    fileName = "invoice-b.png",
                    mimeType = "image/png",
                    bytes = "PNG-B".encodeToByteArray(),
                ),
            ),
        )

        assertTrue(result.isSuccess)
        val fields = capture.fields ?: error("No multipart fields captured")
        assertEquals("1Z999", fields.value("package_couirer_number"))
        assertEquals("SeaDrop", fields.value("shipping_method"))
        assertEquals("Amazon", fields.value("package_shipper"))
        assertEquals("UPS", fields.value("package_store"))
        assertEquals("84.50", fields.value("package_amount"))
        assertEquals("Kerry Smith", fields.value("package_consignee"))
        assertEquals("", fields.value("pckaage_invoice"))
        assertFalse(
            "Swift filters blank package_description while preserving pckaage_invoice",
            fields.containsKey("package_description"),
        )

        val files = capture.files ?: error("No multipart files captured")
        assertEquals(2, files.size)
        files[0].assertFilePart(
            fieldName = "preorder_invoice[0]",
            fileName = "invoice-a.pdf",
            contentType = "application/pdf",
            body = "PDF-A",
        )
        files[1].assertFilePart(
            fieldName = "preorder_invoice[1]",
            fileName = "invoice-b.png",
            contentType = "image/png",
            body = "PNG-B",
        )
    }

    private class CapturedDropAlertRequest {
        var fields: Map<String, RequestBody>? = null
        var files: List<MultipartBody.Part>? = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun dropAlertService(capture: CapturedDropAlertRequest): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            if (method.name != "createDropAlert") {
                throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
            capture.fields = args?.getOrNull(0) as? Map<String, RequestBody>
            capture.files = args?.getOrNull(1) as? List<MultipartBody.Part>
            DropAlertResponse(success = true, message = "Created")
        } as AirdropApiService

    private fun Map<String, RequestBody>.value(name: String): String =
        get(name)?.readUtf8() ?: error("Missing multipart field $name")

    private fun RequestBody.readUtf8(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun MultipartBody.Part.assertFilePart(
        fieldName: String,
        fileName: String,
        contentType: String,
        body: String,
    ) {
        val disposition = headers?.get("Content-Disposition").orEmpty()
        assertTrue(disposition.contains("name=\"$fieldName\""))
        assertTrue(disposition.contains("filename=\"$fileName\""))
        assertEquals(contentType, this.body.contentType().toString())
        assertEquals(body, this.body.readUtf8())
    }
}
