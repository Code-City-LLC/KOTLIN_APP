package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.util.Collections
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShipmentsInfiniteScrollParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun packagesKeepsLoadingWhileFilteredTailStaysVisible() {
        val repo = SparseExpressPackagesRepository()
        lateinit var viewModel: PackagesViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                viewModel = PackagesViewModel(repo = repo, hubRepo = FakeHubRepository())
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

        compose.waitUntil(timeoutMillis = 5_000) {
            repo.pages.contains(1) && !viewModel.state.value.loading
        }
        compose.runOnIdle {
            viewModel.selectMethod(ShipmentTypeFilter.Express)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repo.pages.containsAll(listOf(1, 2, 3)) &&
                !viewModel.state.value.loadingMore &&
                !viewModel.state.value.hasMorePages
        }
    }

    private class SparseExpressPackagesRepository : ShipmentsPackagesRepository {
        private val recordedPages = Collections.synchronizedList(mutableListOf<Int>())

        val pages: List<Int>
            get() = synchronized(recordedPages) { recordedPages.toList() }

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
        ): Result<List<ShipmentPackage>> {
            recordedPages += page
            val count = if (page < 3) perPage else 1
            return Result.success(
                (1..count).map { index ->
                    val express = index == 1
                    ShipmentPackage(
                        id = page * 100 + index,
                        description = if (express) "Express visible $page" else "Standard hidden $page-$index",
                        weightLbs = 1.0,
                        statusName = "Ready for Pickup",
                        shippingMethod = if (express) "Express" else "Standard",
                        additionalChargesTotal = 50.0,
                    )
                },
            )
        }

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException())

        override suspend fun packageStatuses() = Result.success(ShipmentStatusCatalog.defaults)

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(UnsupportedOperationException())

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(UnsupportedOperationException())
    }

    private class FakeHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(160.625)

        override suspend fun summary() = Result.success(ShipmentsSummary())

        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())

        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())

        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }
}
