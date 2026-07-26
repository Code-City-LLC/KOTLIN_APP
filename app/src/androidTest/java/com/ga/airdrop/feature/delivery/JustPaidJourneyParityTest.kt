package com.ga.airdrop.feature.delivery

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.feature.shipments.DamageReportUploadFile
import com.ga.airdrop.feature.shipments.InvoiceUploadFile
import com.ga.airdrop.feature.shipments.PackageStatusInfo
import com.ga.airdrop.feature.shipments.Paged
import com.ga.airdrop.feature.shipments.ShipmentPackage
import com.ga.airdrop.feature.shipments.ShipmentPackageDetail
import com.ga.airdrop.feature.shipments.ShipmentsPackagesRepository
import com.ga.airdrop.feature.shipments.ShipmentsRepoProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The post-checkout screen, rendered on a device.
 *
 * Kemar ruled this screen must show the REAL packages a payment settled rather
 * than a static rail. The view-model tests pin the data; this pins what a
 * customer actually SEES, because the two can diverge — the state can be right
 * while the screen renders the wrong branch of it.
 *
 * Five cases, deliberately: ARD-only (and no Stripe id anywhere), a blank
 * status, a partial read, a total failure, and no ids at all. Each is a
 * different fact, and none may be rendered as another. BrightHarbor #179
 * block 3/3.
 */
@RunWith(AndroidJUnit4::class)
class JustPaidJourneyParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val realPackages = ShipmentsRepoProvider.packages

    @After
    fun tearDown() {
        ShipmentsRepoProvider.packages = realPackages
    }

    /**
     * Physical proof. Semantics assertions prove the right NODES exist; an
     * image proves a human can see the result and that nothing unexpected — a
     * Stripe id, a courier number — is sitting on screen next to them. The
     * path is printed so the artifact is findable from the test log rather
     * than only from a device pull. BrightHarbor #179 physical-proof hold.
     */
    private fun capture(filename: String) {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/just_paid",
        ).apply { mkdirs() }
        val output = File(dir, filename)
        FileOutputStream(output).use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        println("JUST_PAID_ARTIFACT ${output.absolutePath}")
    }

    /** Resolves only the ids it is given; every other lookup fails. */
    private class FakePackages(
        private val resolvable: Set<Int>,
        private val blankStatus: Boolean = false,
    ) : ShipmentsPackagesRepository {
        override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> {
            val id = packageId.toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("bad id"))
            if (id !in resolvable) return Result.failure(java.io.IOException("unreachable"))
            return Result.success(
                ShipmentPackageDetail(
                    id = id,
                    status = if (blankStatus) null else "20",
                    statusName = if (blankStatus) null else "Paid and Ready for Delivery",
                    trackingCode = "ARDQA$id",
                    // Must never surface: internal identifiers are not
                    // customer-facing (Swift CLAUDE.md:31).
                    courierNumber = "COURIER-$id",
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

    private fun render(ids: List<Int>, repo: ShipmentsPackagesRepository) {
        ShipmentsRepoProvider.packages = repo
        compose.setContent {
            AirdropThemeProvider {
                DeliveryCenterScreen(
                    // A Stripe checkout id, exactly as verifySession supplies it.
                    orderReference = "cs_test_a1b2c3d4",
                    initialPackageId = null,
                    justPaidPackageIds = ids,
                    onBack = {},
                    onContactUs = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * ⚠️ THE STRIPE ID MUST NOT BE ON SCREEN.
     *
     * `orderReference` IS the `cs_...` checkout-session id — an internal
     * payment-processor identifier. It was rendered to customers on two
     * surfaces until BrightHarbor caught it. Only the ARD may appear.
     */
    @Test
    fun theArdIsShownAndTheStripeCheckoutIdIsNowhereOnScreen() {
        render(listOf(101, 102), FakePackages(setOf(101, 102)))

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("just-paid-package-101").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("just-paid-package-101").performScrollTo().assertIsDisplayed()
        assertTrue(
            "the ARD must be the customer-facing reference",
            compose.onAllNodesWithText("ARDQA101").fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(
            "the Stripe checkout id must never reach the customer",
            0,
            compose.onAllNodesWithText("cs_test_a1b2c3d4").fetchSemanticsNodes().size,
        )
        assertEquals(
            "an internal courier identifier must never reach the customer",
            0,
            compose.onAllNodesWithText("COURIER-101").fetchSemanticsNodes().size,
        )

        capture("just_paid_ard_only.png")
    }

    /** A status the server did not send is shown as unavailable, never authored. */
    @Test
    fun aBlankServerStatusRendersAsUnavailableRatherThanAnInventedOne() {
        render(listOf(101), FakePackages(setOf(101), blankStatus = true))

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("just-paid-status-101").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            compose.onAllNodesWithText("Status unavailable").fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(
            "no status may be invented from the fact of payment",
            0,
            compose.onAllNodesWithText("Paid").fetchSemanticsNodes().size,
        )

        capture("just_paid_blank_status.png")
    }

    /**
     * Two of three readable: show the two, and SAY the third is missing.
     * Rendering two silently would tell the customer they bought two.
     */
    @Test
    fun aPartialReadShowsWhatLoadedAndDeclaresWhatDidNot() {
        render(listOf(101, 102, 103), FakePackages(setOf(101, 103)))

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("just-paid-partial").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("just-paid-package-101").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("just-paid-package-103").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("just-paid-partial").performScrollTo().assertIsDisplayed()
        assertEquals(
            "the unreadable package must not be rendered as if it loaded",
            0,
            compose.onAllNodesWithTag("just-paid-package-102").fetchSemanticsNodes().size,
        )

        capture("just_paid_partial.png")
    }

    /** Nothing readable: say so, and confirm the payment went through. */
    @Test
    fun aTotalFailureSaysSoAndDoesNotImplyAnEmptyOrder() {
        render(listOf(101, 102), FakePackages(emptySet()))

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("just-paid-unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("just-paid-unavailable").performScrollTo().assertIsDisplayed()
        assertEquals(
            "a failed read must not render the none-yet copy",
            0,
            compose.onAllNodesWithTag("just-paid-none").fetchSemanticsNodes().size,
        )

        capture("just_paid_total_failure.png")
    }

    /**
     * An unfulfilled payment returns `package_ids: []`. That is "nothing yet",
     * NOT an error — and it must not claim one.
     */
    @Test
    fun noPackageIdsRendersTheNoneYetCopyAndNotAnError() {
        render(emptyList(), FakePackages(emptySet()))

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("just-paid-none").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("just-paid-none").performScrollTo().assertIsDisplayed()
        assertEquals(
            "no ids is not a failure",
            0,
            compose.onAllNodesWithTag("just-paid-unavailable").fetchSemanticsNodes().size,
        )

        capture("just_paid_no_ids.png")
    }
}
