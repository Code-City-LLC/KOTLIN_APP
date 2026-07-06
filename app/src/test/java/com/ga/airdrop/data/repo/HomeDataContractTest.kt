package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.model.CurrentUserResponse
import com.ga.airdrop.data.model.Paginated
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDataContractTest {

    @Test
    fun homeRepositoriesUseSwiftAuthenticatedDataContracts() = runBlocking {
        val capture = CapturedHomeCalls()
        val service = homeService(capture)

        val user = UserRepository(service).currentUser().getOrThrow()
        val status = MiscRepository(service).airCoinsStatus().getOrThrow()
        val auctions = ProductsRepository(service).auctionProductsShortlist().getOrThrow()

        assertEquals("Kemar", user.firstName)
        assertEquals("Gold Standard", user.customerTierName)
        assertTrue(capture.currentUserCalled)

        assertEquals(42, status.available)
        assertTrue(capture.airCoinsStatusCalled)

        assertEquals(listOf("swift-product"), auctions.map { it.slug })
        assertEquals(
            mapOf(
                "page" to "1",
                "per_page" to "4",
                "order" to "created_at",
                "direction" to "desc",
                "in_stock" to "1",
            ),
            capture.productsParams,
        )
    }

    private class CapturedHomeCalls {
        var currentUserCalled = false
        var airCoinsStatusCalled = false
        var productsParams: Map<String, String>? = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun homeService(capture: CapturedHomeCalls): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "currentUser" -> {
                    capture.currentUserCalled = true
                    CurrentUserResponse(
                        AirdropUser(
                            firstName = "Kemar",
                            customerTierName = "Gold Standard",
                        ),
                    )
                }
                "airCoinsStatus" -> {
                    capture.airCoinsStatusCalled = true
                    AirCoinsStatus(available = 42, balance = 7)
                }
                "products" -> {
                    capture.productsParams = args?.getOrNull(0) as? Map<String, String>
                    Paginated(
                        listOf(
                            AuctionProduct(
                                id = 17,
                                name = "Swift Product",
                                slug = "swift-product",
                                currentPrice = "12.00",
                            ),
                        ),
                    )
                }
                else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService
}
