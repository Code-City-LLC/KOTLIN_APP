package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.TierChangeRequest
import com.ga.airdrop.data.model.TierChangeResult
import com.ga.airdrop.data.model.ServiceTier

interface CustomerTierReader {
    suspend fun serviceTiers(): Result<List<ServiceTier>>
    suspend fun customerTier(): Result<CustomerTier>

    /** PATCH /customers/me/tier — provisional; confirm with [customerTier]. */
    suspend fun changeTier(code: String): Result<TierChangeResult>
}

class TierRepository(private val service: AirdropApiService) : CustomerTierReader {
    override suspend fun serviceTiers(): Result<List<ServiceTier>> =
        apiResult { service.serviceTiers().items }

    override suspend fun customerTier(): Result<CustomerTier> = apiResult {
        service.customerTier().data ?: error("No customer tier returned.")
    }

    override suspend fun changeTier(code: String): Result<TierChangeResult> = apiResult {
        service.changeCustomerTier(TierChangeRequest(requestedTierCode = code)).data
            ?: error("No change result returned.")
    }
}
