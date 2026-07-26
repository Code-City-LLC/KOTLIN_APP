package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.AirCoinTransaction
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.feature.homedetails.AIRCOIN_HISTORY_PER_PAGE
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscRepositoryAirCoinsTest {

    @Test
    fun airCoinsUsesSwiftStatusAndHistoryContracts() = runBlocking {
        val capture = CapturedAirCoinsCalls()
        val repository = MiscRepository(airCoinsService(capture))

        val status = repository.airCoinsStatus().getOrThrow()
        assertEquals(234.0, status.accumulated)
        assertEquals(23.0, status.redeemed)
        assertEquals(50.0, status.available)
        assertEquals(50.0, status.balance)
        assertTrue(capture.statusCalled)

        val history = repository.airCoinHistory(page = 1, limit = AIRCOIN_HISTORY_PER_PAGE).getOrThrow()
        assertEquals(listOf("#24242433"), history.map { it.referenceId })
        assertEquals(1, capture.historyPage)
        assertEquals(50, capture.historyPerPage)
    }

    private class CapturedAirCoinsCalls {
        var statusCalled = false
        var historyPage: Int? = null
        var historyPerPage: Int? = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun airCoinsService(capture: CapturedAirCoinsCalls): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "airCoinsStatus" -> {
                    capture.statusCalled = true
                    AirCoinsStatus(
                        accumulated = 234.0,
                        redeemed = 23.0,
                        available = 50.0,
                        balance = 50.0,
                    )
                }
                "airCoinsHistory" -> {
                    capture.historyPage = args?.getOrNull(0) as? Int
                    capture.historyPerPage = args?.getOrNull(1) as? Int
                    Paginated(
                        listOf(
                            AirCoinTransaction(
                                id = 1,
                                amount = 25.0,
                                referenceId = "#24242433",
                                createdAt = "2025-10-25T00:00:00Z",
                            ),
                        ),
                    )
                }
                else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService
}
