package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceTier(
    val code: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("processing_copy") val processingCopy: String? = null,
    @SerialName("benefits_summary") val benefitsSummary: List<String> = emptyList(),
)

@Serializable
data class CustomerTier(
    @SerialName("current_tier") val currentTier: String = "",
)
