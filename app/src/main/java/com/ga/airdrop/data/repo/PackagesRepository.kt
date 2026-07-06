package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.UploadFile
import com.ga.airdrop.data.api.textPart
import com.ga.airdrop.data.api.toPart
import com.ga.airdrop.data.model.CartSnapshot
import com.ga.airdrop.data.model.DropAlertResponse
import com.ga.airdrop.data.model.DropAlertShippingMethod
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.Package
import com.ga.airdrop.data.model.PackageCartMutation
import com.ga.airdrop.data.model.PackageCategory
import com.ga.airdrop.data.model.PackageDetail
import com.ga.airdrop.data.model.PackageInvoiceDocument
import com.ga.airdrop.data.model.PackageStatus
import com.ga.airdrop.data.model.ShipmentSummary

class PackagesRepository(private val service: AirdropApiService) {

    // Session cache, Swift PackageStatusCatalog equivalent.
    @Volatile
    private var cachedStatuses: List<PackageStatus>? = null

    suspend fun packages(
        page: Int = 1,
        perPage: Int = 15,
        status: Int? = null,
        search: String? = null,
    ): Result<List<Package>> = apiResult {
        service.packages(
            page = page,
            perPage = perPage,
            sortBy = "creation_date",
            sortOrder = "desc",
            status = status?.takeIf { it > 0 },
            search = normalizedSearch(search),
        ).items
    }

    suspend fun packagesShortlist(): Result<List<Package>> = packages(page = 1, perPage = 6)

    suspend fun packageDetails(packageId: String): Result<PackageDetail> = apiResult {
        service.packageDetails(packageId).data ?: error("Package not found")
    }

    suspend fun invoices(packageId: String): Result<List<PackageInvoiceDocument>> =
        packageDetails(packageId).map { it.invoices }

    suspend fun uploadPackageInvoices(
        packageId: String,
        files: List<UploadFile>,
    ): Result<List<PackageInvoiceDocument>> = apiResult {
        val parts = files.map { it.toPart("invoices[]") }
        service.uploadPackageInvoices(packageId, parts).data?.documents ?: emptyList()
    }

    suspend fun deletePackageInvoice(packageId: String, invoiceId: Int): Result<MutationResponse> =
        apiResult { service.deletePackageInvoice(packageId, invoiceId) }

    suspend fun reportPackageDamage(
        packageId: String,
        description: String,
        photos: List<UploadFile>,
    ): Result<MutationResponse> = apiResult {
        service.reportPackageDamage(
            packageId = packageId,
            fields = mapOf("description" to textPart(description.trim())),
            photos = photos.take(5).map { it.toPart("photos[]") },
        )
    }

    suspend fun packageStatuses(refresh: Boolean = false): Result<List<PackageStatus>> = apiResult {
        if (!refresh) {
            cachedStatuses?.let { return@apiResult it }
        }
        val statuses = service.packageStatuses().items.sortedBy { it.order }
        cachedStatuses = statuses
        statuses
    }

    suspend fun packageCategories(): Result<List<PackageCategory>> =
        apiResult { service.packageCategories().items }

    suspend fun shipmentsSummary(): Result<ShipmentSummary> =
        apiResult { service.shipmentsSummary() }

    // ── Server cart ──

    suspend fun cart(): Result<CartSnapshot> = apiResult {
        val envelope = service.cart()
        envelope.data ?: error(envelope.message ?: "Failed to load cart")
    }

    suspend fun addPackageToCart(packageId: Int): Result<PackageCartMutation> = apiResult {
        val envelope = service.addPackageToCart(packageId, EmptyRequest())
        if (envelope.success == false || envelope.data == null) {
            error(envelope.message ?: "Failed to add package to cart")
        }
        envelope.data
    }

    suspend fun removePackageFromCart(packageId: Int): Result<PackageCartMutation> = apiResult {
        val envelope = service.removePackageFromCart(packageId)
        if (envelope.success == false || envelope.data == null) {
            error(envelope.message ?: "Failed to remove package from cart")
        }
        envelope.data
    }

    // ── Drop alerts ──

    suspend fun createDropAlert(
        courierNumber: String,
        shipper: String,
        shippingMethod: DropAlertShippingMethod,
        store: String,
        packageAmount: String,
        consignee: String,
        description: String? = null,
        invoiceFiles: List<UploadFile> = emptyList(),
    ): Result<DropAlertResponse> = apiResult {
        val rawFields = mapOf(
            "package_couirer_number" to courierNumber,
            "shipping_method" to shippingMethod.wireName,
            "package_shipper" to shipper,
            "package_store" to store,
            "package_amount" to packageAmount,
            "package_consignee" to consignee,
            "package_description" to description.orEmpty(),
            // Always sent, always empty — the wire contract (misspelled).
            "pckaage_invoice" to "",
        )
        val fields = rawFields
            .filter { (key, value) -> value.isNotBlank() || key == "pckaage_invoice" }
            .mapValues { (_, value) -> textPart(value) }
        val files = invoiceFiles.mapIndexed { index, file ->
            file.toPart("preorder_invoice[$index]")
        }
        service.createDropAlert(fields, files)
    }
}
