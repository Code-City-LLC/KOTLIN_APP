package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.data.model.Paginated
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscRepositoryNotificationsTest {
    @Test
    fun `unread header query reaches the existing Laravel filter`() = runBlocking {
        var captured: List<Any?> = emptyList()
        val service = Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "notifications" -> {
                    captured = args.orEmpty().toList()
                    Paginated(items = listOf(AirdropNotification(id = "unread")))
                }
                "toString" -> "NotificationsService"
                "hashCode" -> 1
                "equals" -> false
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService

        val result = MiscRepository(service).notifications(
            page = 1,
            limit = 100,
            unreadOnly = true,
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("unread"), result.getOrThrow().map { it.id })
        assertEquals(1, captured[0])
        assertEquals(100, captured[1])
        assertEquals(true, captured[2])
    }
}
