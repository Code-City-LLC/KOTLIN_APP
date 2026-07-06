package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.UploadFile
import com.ga.airdrop.data.model.MutationResponse
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagesRepositoryReportDamageTest {

    @Test
    fun reportPackageDamageBuildsSwiftMultipartFieldsAndPhotoParts() = runBlocking {
        val capture = CapturedDamageReportRequest()
        val service = damageReportService(capture)
        val repository = PackagesRepository(service)

        val result = repository.reportPackageDamage(
            packageId = "42",
            description = "  Cracked corner  ",
            photos = (1..6).map { index ->
                UploadFile(
                    fileName = "damage-$index.png",
                    mimeType = "image/png",
                    bytes = "PHOTO-$index".encodeToByteArray(),
                )
            },
        )

        assertTrue(result.isSuccess)
        assertEquals("42", capture.packageId)
        val fields = capture.fields ?: error("No multipart fields captured")
        assertEquals("Cracked corner", fields.value("description"))

        val photos = capture.photos ?: error("No multipart photos captured")
        assertEquals("Laravel accepts at most five damage photos", 5, photos.size)
        photos[0].assertFilePart(
            fieldName = "photos[]",
            fileName = "damage-1.png",
            contentType = "image/png",
            body = "PHOTO-1",
        )
        photos[4].assertFilePart(
            fieldName = "photos[]",
            fileName = "damage-5.png",
            contentType = "image/png",
            body = "PHOTO-5",
        )
    }

    private class CapturedDamageReportRequest {
        var packageId: String? = null
        var fields: Map<String, RequestBody>? = null
        var photos: List<MultipartBody.Part>? = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun damageReportService(capture: CapturedDamageReportRequest): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            if (method.name != "reportPackageDamage") {
                throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
            capture.packageId = args?.getOrNull(0) as? String
            capture.fields = args?.getOrNull(1) as? Map<String, RequestBody>
            capture.photos = args?.getOrNull(2) as? List<MultipartBody.Part>
            MutationResponse(success = true, message = "Damage report submitted successfully")
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
