package com.ga.airdrop.feature.shop

import retrofit2.HttpException

internal fun Throwable.isUnauthenticatedCheckoutFailure(): Boolean {
    if (this is HttpException && code() == 401) return true
    val text = listOfNotNull(message, cause?.message).joinToString(" ")
    return text.contains("401") ||
        text.contains("unauthenticated", ignoreCase = true) ||
        text.contains("unauthorized", ignoreCase = true)
}
