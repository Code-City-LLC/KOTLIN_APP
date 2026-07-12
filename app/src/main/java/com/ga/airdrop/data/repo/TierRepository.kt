package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier

interface CustomerTierReader {
    suspend fun serviceTiers(): Result<List<ServiceTier>>
    suspend fun customerTier(): Result<CustomerTier>
}

class TierRepository(private val service: AirdropApiService) : CustomerTierReader {
    override suspend fun serviceTiers(): Result<List<ServiceTier>> =
        apiResult { service.serviceTiers().items }

    override suspend fun customerTier(): Result<CustomerTier> = apiResult {
        service.customerTier().data ?: error("No customer tier returned.")
    }
}
