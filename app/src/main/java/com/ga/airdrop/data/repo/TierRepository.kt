package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.TierChangeRequest

interface CustomerTierReader {
    suspend fun serviceTiers(): Result<List<ServiceTier>>
    suspend fun customerTier(): Result<CustomerTier>
}

/**
 * Separate seam from [CustomerTierReader] so existing reader fakes in tests
 * stay source-compatible. PATCH → the caller re-GETs for confirmation
 * (Swift changeCustomerTier; the backend applies its own change rules).
 */
fun interface TierChanger {
    suspend fun changeTier(requestedTierCode: String): Result<CustomerTier>
}

class TierRepository(private val service: AirdropApiService) : CustomerTierReader, TierChanger {
    override suspend fun serviceTiers(): Result<List<ServiceTier>> =
        apiResult { service.serviceTiers().items }

    override suspend fun customerTier(): Result<CustomerTier> = apiResult {
        service.customerTier().data ?: error("No customer tier returned.")
    }

    override suspend fun changeTier(requestedTierCode: String): Result<CustomerTier> = apiResult {
        service.changeCustomerTier(TierChangeRequest(requestedTierCode)).data
            ?: error("No customer tier returned.")
    }
}
