package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.Package
import com.ga.airdrop.data.model.Paginated
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PackagesRepositoryQueryTest {

    @Test
    fun packagesSendsSwiftShippingMethodQueryWhenSelected() = runBlocking {
        val capture = CapturedPackagesQuery()
        val repository = PackagesRepository(packagesService(capture))

        repository.packages(
            page = 2,
            perPage = 15,
            status = 7,
            search = " ARD000 ",
            shippingMethod = " Express ",
        ).getOrThrow()

        assertEquals(2, capture.page)
        assertEquals(15, capture.perPage)
        assertEquals("creation_date", capture.sortBy)
        assertEquals("desc", capture.sortOrder)
        assertEquals(7, capture.status)
        assertEquals("ARD000", capture.search)
        assertEquals("Express", capture.shippingMethod)
    }

    @Test
    fun packagesOmitsAllShippingMethodLikeSwift() = runBlocking {
        val capture = CapturedPackagesQuery()
        val repository = PackagesRepository(packagesService(capture))

        repository.packages(shippingMethod = "All").getOrThrow()

        assertEquals(null, capture.shippingMethod)
    }

    private class CapturedPackagesQuery {
        var page: Int? = null
        var perPage: Int? = null
        var sortBy: String? = null
        var sortOrder: String? = null
        var status: Int? = null
        var search: String? = null
        var shippingMethod: String? = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun packagesService(capture: CapturedPackagesQuery): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            if (method.name != "packages") {
                throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
            capture.page = args?.getOrNull(0) as? Int
            capture.perPage = args?.getOrNull(1) as? Int
            capture.sortBy = args?.getOrNull(2) as? String
            capture.sortOrder = args?.getOrNull(3) as? String
            capture.status = args?.getOrNull(4) as? Int
            capture.search = args?.getOrNull(5) as? String
            capture.shippingMethod = args?.getOrNull(6) as? String
            Paginated(listOf(Package(id = 42, shippingMethod = capture.shippingMethod)))
        } as AirdropApiService
}
