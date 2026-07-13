package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.TierChangeRequest
import com.ga.airdrop.data.model.TierChangeResult

interface CustomerTierReader {
    suspend fun serviceTiers(): Result<List<ServiceTier>>
    suspend fun customerTier(): Result<CustomerTier>
}

/**
 * Separate seam from [CustomerTierReader] so existing reader fakes in tests
 * stay source-compatible. PATCH returns the backend's change RESULT; the
 * caller must await the authoritative GET before claiming success
 * (Swift changeCustomerTier; the backend applies its own change rules).
 */
fun interface TierChanger {
    suspend fun changeTier(
        requestedTierCode: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<TierChangeResult>
}

class TierRepository(private val service: AirdropApiService) : CustomerTierReader, TierChanger {
    override suspend fun serviceTiers(): Result<List<ServiceTier>> =
        apiResult { service.serviceTiers().items }

    override suspend fun customerTier(): Result<CustomerTier> = apiResult {
        service.customerTier().data ?: error("No customer tier returned.")
    }

    override suspend fun changeTier(
        requestedTierCode: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<TierChangeResult> = apiResult {
        service.changeCustomerTier(
            authRevision = expectedSession.revision.toString(),
            sessionId = expectedSession.sessionId,
            body = TierChangeRequest(requestedTierCode),
        ).data
            ?: error("No change result returned.")
    }
}
