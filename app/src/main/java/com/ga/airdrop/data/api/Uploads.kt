package com.ga.airdrop.data.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

// Android counterpart of Swift's AirdropAPI.UploadFile.
data class UploadFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is UploadFile && fileName == other.fileName &&
            mimeType == other.mimeType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int =
        31 * (31 * fileName.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()
}

fun UploadFile.toPart(fieldName: String): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        fieldName,
        fileName,
        bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
    )

// Text fields carry no per-part Content-Type header, matching Swift's
// multipart builder.
fun textPart(value: String): RequestBody = value.toRequestBody(null)
