package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.BusinessRateInquiryRequest
import com.ga.airdrop.data.model.BusinessRateInquiryResult

fun interface BusinessRateInquirySender {
    suspend fun submit(
        request: BusinessRateInquiryRequest,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<BusinessRateInquiryResult>
}

class BusinessRateInquiryRepository(private val service: AirdropApiService) : BusinessRateInquirySender {
    override suspend fun submit(
        request: BusinessRateInquiryRequest,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<BusinessRateInquiryResult> = apiResult {
        // The caller owns the request ID across retries. The existing interceptor
        // enforces this account/session generation at dispatch, as for tier changes.
        val response = service.submitBusinessRateInquiry(
            authRevision = expectedSession.revision.toString(),
            sessionId = expectedSession.sessionId,
            body = request,
        )
        check(response.success == true) {
            response.message ?: "Your Business request was not confirmed. Please try again."
        }
        val result = response.data ?: error("No Business request confirmation returned.")
        check(result.messageId > 0 && result.action in setOf("created", "updated")) {
            "Your Business request was not confirmed. Please try again."
        }
        result
    }
}
