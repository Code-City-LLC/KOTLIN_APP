package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Laravel owns qualification and the single open inquiry per customer. */
@Serializable
data class BusinessRateInquiryRequest(
    @SerialName("business_name") val businessName: String,
    @SerialName("client_request_id") val clientRequestId: String,
    @SerialName("airdrop_monthly_lb") val airDropMonthlyPounds: Double? = null,
    @SerialName("seadrop_monthly_cubic_feet") val seaDropMonthlyCubicFeet: Double? = null,
)

@Serializable
data class BusinessRateInquiryResult(
    @SerialName("message_id") val messageId: Int,
    val action: String,
)
