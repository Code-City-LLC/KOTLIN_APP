package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import org.junit.Rule
import org.junit.Test

/**
 * A FAILED packages load must not be reported as "you have no packages".
 *
 * ⚠️ THE BUG THIS PINS. `PackagesScreen` fell straight from the loading branch
 * to `ShipmentsEmptyLabel("No packages found")`, so any failure — offline, 500,
 * expired token — told the customer their shipments did not exist. That is the
 * strongest false statement this app can make about someone's account.
 *
 * The failure was not even unknown. `PackagesViewModel.load()` already did
 * `error = e.message` on the failure branch; `PackagesScreen` simply never read
 * `state.error`. The information existed and was thrown away at the last step,
 * which is why nothing in CI or the ViewModel tests caught it: the state was
 * correct and only the render was wrong.
 *
 * Found by an adversarial three-platform audit, then confirmed by reading
 * PackagesViewModel.kt:208 against PackagesScreen.kt:100-103.
 */
class PackagesLoadFailureParityTest {

    @get:Rule
    val compose = createComposeRule()

    /** Fails every page fetch, the way a dropped connection does. */
    private class FailingPackagesRepository : ShipmentsPackagesRepository {
        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ): Result<Paged<ShipmentPackage>> = Result.failure(java.io.IOException("unreachable"))

        override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> =
            Result.failure(UnsupportedOperationException())

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

    /** The hub is irrelevant here; it just must not be the thing that fails. */
    private class QuietHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)
        override suspend fun summary() = Result.success(ShipmentsSummary())
        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())
        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())
        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    @Test
    fun aFailedLoadSaysItCouldNotLoad_neverThatTheCustomerHasNoPackages() {
        val viewModel = PackagesViewModel(
            repo = FailingPackagesRepository(),
            hubRepo = QuietHubRepository(),
        )

        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    PackagesScreen(onBack = {}, onNavigate = {}, viewModel = viewModel)
                }
            }
        }

        compose.waitUntil(5_000) { !viewModel.state.value.loading }
        compose.waitForIdle()

        // The failure is stated, and there is a way out of it.
        compose.onNodeWithTag("packages-load-error").assertIsDisplayed()
        compose.onNodeWithText("Tap to retry").assertIsDisplayed()

        // ⚠️ The assertion that actually pins the defect: the app must NOT
        // claim the customer has no packages when it simply could not read them.
        compose.onNodeWithText("No packages found").assertDoesNotExist()
    }
}
