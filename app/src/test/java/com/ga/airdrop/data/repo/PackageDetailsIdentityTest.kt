package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.PackageDetail
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /packages/{id}` must return the package that was asked for.
 *
 * ⚠️ THE WRONG PACKAGE COULD RENDER, AND NOTHING ANYWHERE REPORTED IT.
 *
 * `PackagesRepository.packageDetails` validated `data != null` and stopped
 * there. A successful but MISMATCHED payload was therefore rendered as the
 * requested package — description, invoices, courier number, the lot.
 *
 * The reachable path is the notification deep link: `PushDeepLink` maps a push
 * `"package"` route to `PackageDetailsView` carrying an id taken straight off
 * the push payload, so a stale or wrong reference showed a customer somebody
 * else's package with no error on any screen and nothing in a crash report.
 *
 * Swift carried the identical hole in `packageDetailsByID` and it was caught
 * there first (@Codex-SwiftSentinel, ORC #99022). Kotlin already failed closed
 * on an id mismatch in `packageTimeline` and in `addToCart`; package details
 * was the single read that did not, with the correct guard sitting ~100 lines
 * below it in the same file.
 *
 * The `missing id` case matters as much as the mismatch: `PackageDetail.id`
 * defaults to 0, so a payload that omits `id` used to decode into a confident
 * package object whose identity nobody could check. This codebase's recurring
 * defect is absence rendered as fact, and an unverifiable response is not the
 * package we asked for.
 */
class PackageDetailsIdentityTest {

    private fun repo(payload: String?) = PackagesRepository(
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "packageDetails" -> DataEnvelope(
                    success = true,
                    data = payload?.let {
                        PackageDetail(id = it.toInt(), description = "Package $it")
                    },
                )
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService,
    )

    private fun load(returnedId: String?, requested: String) =
        runBlocking { repo(returnedId).packageDetails(requested) }

    @Test
    fun `the requested package loads`() {
        val r = load(returnedId = "41", requested = "41")
        assertTrue("an exact id match must succeed. Got: $r", r.isSuccess)
        assertEquals(41, r.getOrNull()!!.id)
    }

    @Test
    fun `a DIFFERENT package is rejected, not rendered as the requested one`() {
        // The defect in one assertion. Before the guard this returned package
        // 999 to a customer who tapped a notification for package 41.
        val r = load(returnedId = "999", requested = "41")
        assertTrue("a mismatched package id must fail closed. Got: $r", r.isFailure)
    }

    @Test
    fun `an off-by-one id is rejected — the guard is exact, not fuzzy`() {
        assertTrue(load(returnedId = "42", requested = "41").isFailure)
        assertTrue(load(returnedId = "4", requested = "41").isFailure)
        assertTrue(load(returnedId = "411", requested = "41").isFailure)
    }

    @Test
    fun `a payload with no id at all is rejected, not defaulted to zero and trusted`() {
        // PackageDetail.id defaults to 0. Absence must not read as a valid
        // identity — it reads as "this response cannot be verified".
        val r = load(returnedId = "0", requested = "41")
        assertTrue("an unverifiable response is not the package we asked for. Got: $r", r.isFailure)
    }

    @Test
    fun `a null data envelope still fails, as it always did`() {
        val r = load(returnedId = null, requested = "41")
        assertTrue("got: $r", r.isFailure)
    }

    @Test
    fun `the failure does not disclose that another package came back`() {
        // "Package not found" is what actually happened from the customer's
        // side, and it deliberately does not leak the other package's id.
        val message = load(returnedId = "999", requested = "41").exceptionOrNull()?.message.orEmpty()
        assertEquals("Package not found", message)
        assertTrue("the other package's id must not leak", !message.contains("999"))
    }
}
