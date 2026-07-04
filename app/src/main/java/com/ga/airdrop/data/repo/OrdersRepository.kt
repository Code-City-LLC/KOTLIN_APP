package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.Order

class OrdersRepository(private val service: AirdropApiService) {

    suspend fun orders(
        page: Int = 1,
        perPage: Int = 15,
        search: String? = null,
    ): Result<List<Order>> = apiResult {
        service.orders(page = page, perPage = perPage, search = normalizedSearch(search)).items
    }

    suspend fun ordersShortlist(): Result<List<Order>> = orders(page = 1, perPage = 10)

    suspend fun orderDetail(orderId: Int): Result<Order> = apiResult {
        service.orderDetails(orderId).order ?: error("Order not found")
    }
}
