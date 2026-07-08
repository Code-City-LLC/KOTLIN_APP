package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.AirCoinTransaction
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.data.model.CustomDutyRate
import com.ga.airdrop.data.model.DeviceToken
import com.ga.airdrop.data.model.ExchangeRate
import com.ga.airdrop.data.model.MarkNotificationReadRequest
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.data.model.RegisterDeviceTokenRequest
import com.ga.airdrop.data.model.SendTestNotificationRequest
import com.ga.airdrop.data.model.ShipmentCalculation
import com.ga.airdrop.data.model.ShipmentCalculationRequest
import com.ga.airdrop.data.model.ShippingRates
import com.ga.airdrop.data.model.Warehouse

class MiscRepository(private val service: AirdropApiService) {

    // ── Warehouses / rates / calculator ──

    suspend fun warehouses(): Result<List<Warehouse>> =
        apiResult { service.warehouses().items }

    suspend fun exchangeRate(): Result<ExchangeRate> =
        apiResult { service.exchangeRates() }

    suspend fun shippingRates(): Result<ShippingRates> = apiResult {
        service.shippingRates().data ?: error("Failed to load shipping rates")
    }

    suspend fun calculateShipment(
        shippingMethod: String,
        invoiceAmount: Double,
        weightLbs: Double?,
        lengthInches: Double? = null,
        widthInches: Double? = null,
        heightInches: Double? = null,
        numberOfPackages: Int = 1,
        incorrectShippingInfo: Boolean = false,
    ): Result<ShipmentCalculation> = apiResult {
        service.calculateShipment(
            ShipmentCalculationRequest(
                shippingMethod = shippingMethod,
                numberOfPackages = maxOf(1, numberOfPackages),
                invoiceAmount = invoiceAmount,
                weightUnit = "lbs",
                dimensionUnit = "inch",
                customDutyPercentage = 45.0,
                incorrectShippingInfo = incorrectShippingInfo,
                weightLbs = weightLbs,
                packageLength = lengthInches,
                packageWidth = widthInches,
                packageHeight = heightInches,
            ),
        )
    }

    suspend fun customDutyRates(
        page: Int = 1,
        perPage: Int = 15,
        search: String? = null,
        activeOnly: Boolean = true,
    ): Result<List<CustomDutyRate>> = apiResult {
        service.customDutyRates(
            page = page,
            perPage = perPage,
            activeOnly = if (activeOnly) "true" else "false",
            search = search?.trim()?.takeIf { it.isNotEmpty() },
        ).items
    }

    suspend fun customDutyRate(id: Int): Result<CustomDutyRate> = apiResult {
        service.customDutyRate(id).data ?: error("Custom duty rate not found")
    }

    // ── Promotional banners ──

    suspend fun promotionalBanners(activeOnly: Boolean = true): Result<List<PromotionalBanner>> =
        apiResult {
            val banners = service.promotionalBanners().items
            if (activeOnly) banners.filter { it.active == true } else banners
        }

    // ── AirCoins ──

    suspend fun airCoinsStatus(): Result<AirCoinsStatus> =
        apiResult { service.airCoinsStatus() }

    suspend fun airCoinHistory(page: Int = 1, limit: Int = 20): Result<List<AirCoinTransaction>> =
        apiResult { service.airCoinsHistory(page = page, perPage = limit).items }

    // ── Notifications / device tokens ──

    suspend fun notifications(page: Int = 1, limit: Int = 20): Result<List<AirdropNotification>> =
        apiResult { service.notifications(page = page, perPage = limit).items }

    suspend fun markNotificationRead(id: String): Result<MutationResponse> =
        apiResult { service.markNotificationRead(MarkNotificationReadRequest(id)) }

    suspend fun registerFcmToken(
        deviceToken: String,
        deviceType: String = "android",
        deviceInfo: String? = null,
    ): Result<MutationResponse> = apiResult {
        service.registerDeviceToken(
            RegisterDeviceTokenRequest(
                deviceToken = deviceToken,
                deviceType = deviceType,
                deviceInfo = deviceInfo,
            ),
        )
    }

    suspend fun deviceTokens(): Result<List<DeviceToken>> =
        apiResult { service.deviceTokens().items }

    suspend fun sendTestNotification(
        deviceToken: String,
        title: String = "Test Notification",
        body: String = "This is a test push from the app",
        screen: String = "HomeScreen",
        notificationType: String = "system_announcement",
        type: String = "general",
        deepLink: String = "",
        trackingCode: String? = null,
    ): Result<MutationResponse> = apiResult {
        service.sendTestNotification(
            SendTestNotificationRequest(
                deviceId = deviceToken,
                title = title,
                body = body,
                screen = screen,
                notificationType = notificationType,
                type = type,
                deepLink = deepLink,
                trackingCode = trackingCode,
            ),
        )
    }
}
