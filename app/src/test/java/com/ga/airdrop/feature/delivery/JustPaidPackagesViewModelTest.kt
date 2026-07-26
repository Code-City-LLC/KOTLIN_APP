package com.ga.airdrop.feature.delivery

import com.ga.airdrop.feature.shipments.DamageReportUploadFile
import com.ga.airdrop.feature.shipments.InvoiceUploadFile
import com.ga.airdrop.feature.shipments.Paged
import com.ga.airdrop.feature.shipments.PackageStatusInfo
import com.ga.airdrop.feature.shipments.ShipmentPackage
import com.ga.airdrop.feature.shipments.ShipmentPackageDetail
import com.ga.airdrop.feature.shipments.ShipmentsPackagesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The post-checkout screen's data, which Kemar ruled must be the REAL packages
 * rather than a static rail.
 *
 * The four states here are four genuinely different facts, and the whole point
 * of this file is that they never collapse into each other. Every previous
 * round of this work shipped a version that presented an absence of information
 * as information — a blank card, a failed read shown as an empty journey, an
 * unreadable payload shown as an empty journey. The one below is the same shape
 * again: a package we could not read is NOT a package the customer did not buy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JustPaidPackagesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Resolves the ids it is told to, fails every other lookup. */
    private class FakeRepo(
        private val resolvable: Set<Int>,
        /** Answer 200 with a DIFFERENT package than the one requested. */
        private val answerWithId: Int? = null,
        /** Answer 200 with no usable status label at all. */
        private val blankStatus: Boolean = false,
    ) : ShipmentsPackagesRepository {
        var lookups = 0

        override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> {
            lookups++
            val id = packageId.toIntOrNull() ?: return Result.failure(IllegalArgumentException("bad id"))
            if (id !in resolvable) return Result.failure(java.io.IOException("unreachable"))
            return Result.success(
                ShipmentPackageDetail(
                    id = answerWithId ?: id,
                    status = if (blankStatus) null else "20",
                    statusName = if (blankStatus) null else "Paid and Ready for Delivery",
                    trackingCode = "ARDQA$id",
                    courierNumber = "COURIER-SHOULD-NEVER-SHOW-$id",
                    description = "Package $id",
                ),
            )
        }

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ): Result<Paged<ShipmentPackage>> = Result.failure(UnsupportedOperationException())

        override suspend fun packageStatuses(): Result<List<PackageStatusInfo>> =
            Result.failure(UnsupportedOperationException())

        override suspend fun uploadInvoices(
            packageId: String,
            files: List<InvoiceUploadFile>,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun reportDamage(
            packageId: String,
            description: String,
            photos: List<DamageReportUploadFile>,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    @Test
    fun `every paid package is fetched and reported with its server status`() = runTest(dispatcher) {
        val repo = FakeRepo(setOf(101, 102))
        val vm = JustPaidPackagesViewModel(listOf(101, 102), repo)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertFalse(s.failed)
        assertEquals(0, s.unresolvedCount)
        assertEquals(listOf(101, 102), s.packages.map { it.id })
        assertEquals("Paid and Ready for Delivery", s.packages.first().statusName)
        assertEquals(2, repo.lookups)
    }

    /**
     * ⚠️ THE ONE I NEARLY SHIPPED WRONG.
     *
     * The first cut used `mapNotNull`, so a package whose lookup failed simply
     * vanished. Rendering two packages when the customer paid for three states
     * something false about their purchase — and the customer has no way to
     * know the list is short. The shortfall must be counted and surfaced.
     */
    @Test
    fun `a package that could not be read is reported as missing, never silently dropped`() =
        runTest(dispatcher) {
            val vm = JustPaidPackagesViewModel(listOf(101, 102, 103), FakeRepo(setOf(101, 103)))
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(listOf(101, 103), s.packages.map { it.id })
            assertEquals("the unread package must be counted, not vanish", 1, s.unresolvedCount)
            assertFalse("a partial read is not a total failure", s.failed)
        }

    /** Knowing nothing is its own state — and it must not read as "you bought nothing". */
    @Test
    fun `a total failure is reported as a failure, not as an empty order`() = runTest(dispatcher) {
        val vm = JustPaidPackagesViewModel(listOf(101, 102), FakeRepo(emptySet()))
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.packages.isEmpty())
        assertTrue("no lookup succeeded, so we know nothing", s.failed)
        assertEquals(2, s.unresolvedCount)
    }

    /**
     * An unfulfilled payment legitimately returns `package_ids: []`. That is
     * "nothing to show", NOT a failure — nothing was attempted and nothing
     * broke, so the screen must not claim an error.
     */
    @Test
    fun `no package ids at all is not an error state`() = runTest(dispatcher) {
        val repo = FakeRepo(emptySet())
        val vm = JustPaidPackagesViewModel(emptyList(), repo)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertFalse("an empty verify response is not a failure", s.failed)
        assertEquals(0, s.unresolvedCount)
        assertEquals("nothing to look up means no requests", 0, repo.lookups)
    }

    /**
     * ARD only — Swift CLAUDE.md:31. The fake deliberately carries a courier
     * number that must never reach the model.
     */
    @Test
    fun `the reference is the ARD and never the courier number`() = runTest(dispatcher) {
        val vm = JustPaidPackagesViewModel(listOf(101), FakeRepo(setOf(101)))
        advanceUntilIdle()

        val pkg = vm.state.value.packages.single()
        assertEquals("ARDQA101", pkg.ardNumber)
        assertFalse(
            "an internal courier identifier must never become the customer-facing reference",
            pkg.ardNumber.orEmpty().contains("COURIER"),
        )
    }

    /**
     * ⚠️ A SUCCESS IS NOT PROOF IT IS THE RIGHT PACKAGE.
     *
     * Without an identity check, a mismatched 200 would display some other
     * package on the customer's post-checkout screen AND decrement
     * unresolvedCount as though the one they paid for had been found — hiding
     * the failure behind the wrong data. BrightHarbor #80393.
     */
    @Test
    fun `a response for a different package is not accepted as the one requested`() =
        runTest(dispatcher) {
            val vm = JustPaidPackagesViewModel(
                listOf(101),
                FakeRepo(setOf(101), answerWithId = 999),
            )
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue("the wrong package must never be displayed", s.packages.isEmpty())
            assertEquals("and it must still count as unresolved", 1, s.unresolvedCount)
            assertTrue(s.failed)
        }

    /** A zero id is not a package either. */
    @Test
    fun `a zero id response is treated as unresolved`() = runTest(dispatcher) {
        val vm = JustPaidPackagesViewModel(listOf(101), FakeRepo(setOf(101), answerWithId = 0))
        advanceUntilIdle()

        assertTrue(vm.state.value.packages.isEmpty())
        assertEquals(1, vm.state.value.unresolvedCount)
    }

    /**
     * ⚠️ THIS USED TO SAY "Paid".
     *
     * A successful payment proves the payment succeeded. It does NOT prove the
     * package's current status label. Authoring "Paid" when the server sent
     * nothing is the same invention this whole screen exists to avoid — the
     * fifth instance of that shape in this thread. Null now, and the screen
     * renders "Status unavailable". BrightHarbor #80393.
     */
    @Test
    fun `a missing server status is left null, never authored as Paid`() = runTest(dispatcher) {
        val vm = JustPaidPackagesViewModel(listOf(101), FakeRepo(setOf(101), blankStatus = true))
        advanceUntilIdle()

        val pkg = vm.state.value.packages.single()
        assertEquals("the package itself still resolved", 101, pkg.id)
        assertEquals("no status may be invented from the fact of payment", null, pkg.statusName)
    }
}
