package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

// List item for GET /packages. Laravel ships both `package_*`-prefixed and
// flat key variants concurrently; mirror Swift's fallback cascade.
@Serializable(with = PackageSerializer::class)
data class Package(
    val id: Int = 0,
    val trackingCode: String? = null,
    val courierNumber: String? = null,
    val shippingMethod: String? = null,
    val description: String? = null,
    val shipper: String? = null,
    val store: String? = null,
    val status: String? = null,
    val statusName: String? = null,
    val amount: String? = null,
    val totalPrice: String? = null,
    val consignee: String? = null,
    val createdAt: String? = null,
    val weight: String? = null,
    val weightKg: String? = null,
    val weightLbs: Double? = null,
    val additionalCharges: Map<String, Double> = emptyMap(),
    val additionalChargesTotal: Double? = null,
    val exchangeRate: Double? = null,
) {
    val displayTitle: String get() = trackingCode ?: courierNumber ?: "Package #$id"

    val displaySubtitle: String
        get() = listOfNotNull(statusName, shipper, shippingMethod)
            .filter { it.isNotEmpty() }
            .joinToString(" • ")
}

object PackageSerializer : KSerializer<Package> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.Package")

    override fun serialize(encoder: Encoder, value: Package) =
        throw UnsupportedOperationException("Package is decode-only")

    override fun deserialize(decoder: Decoder): Package {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return Package()
        return Package(
            id = obj.flexInt("id", "package_id") ?: 0,
            trackingCode = obj.flexString("package_tracking_code", "tracking_code"),
            courierNumber = obj.flexString("package_couirer_number", "courier_number"),
            shippingMethod = obj.flexString("shipping_method"),
            description = obj.flexString("package_description", "description"),
            shipper = obj.flexString("package_shipper", "shipper"),
            store = obj.flexString("package_store", "store"),
            status = obj.flexString("status", "package_status"),
            statusName = obj.flexString("status_name"),
            amount = obj.flexString("package_amount", "amount"),
            totalPrice = obj.flexString("package_total_price", "total_price"),
            consignee = obj.flexString("package_consignee", "consignee"),
            createdAt = obj.flexString("package_creation_date_time", "creation_date"),
            weight = obj.flexString("weight", "package_weight"),
            weightKg = obj.flexString("weight_kg", "package_weight_kg"),
            weightLbs = obj.flexDouble("weight_lbs", "package_weight_lbs"),
            additionalCharges = obj.flexDoubleMap(
                "additional_charges", "package_additional_charges", "charges",
            ) ?: emptyMap(),
            additionalChargesTotal = obj.flexDouble(
                "additional_charges_total", "package_additional_charges_total",
            ),
            exchangeRate = obj.flexDouble("exchange_rate"),
        )
    }
}

@Serializable
data class PackageStatus(
    val id: Int = 0,
    val name: String = "",
    @SerialName("color_code") val colorCode: String = "",
    val order: Int = 0,
)

@Serializable
data class PackageCategory(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String? = null,
    @SerialName("duty_charges")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val dutyCharges: Double? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String? = null,
    @SerialName("is_active")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isActive: Boolean? = null,
)

@Serializable
data class PackageHistoryItem(
    @Serializable(with = FlexibleIntSerializer::class)
    val status: Int? = null,
    @SerialName("status_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val statusName: String? = null,
    @SerialName("changed_date")
    @Serializable(with = FlexibleStringSerializer::class)
    val changedDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val comment: String? = null,
    @SerialName("changed_by_user_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val changedByUserId: Int? = null,
    @SerialName("changed_by")
    @Serializable(with = FlexibleStringSerializer::class)
    val changedBy: String? = null,
)

@Serializable
data class PackageInvoiceDocument(
    @SerialName("doc_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @SerialName("doc_file_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val fileName: String? = null,
    @SerialName("doc_file_path")
    @Serializable(with = FlexibleStringSerializer::class)
    val filePath: String? = null,
    @SerialName("full_url")
    @Serializable(with = FlexibleStringSerializer::class)
    val fullUrl: String? = null,
    @SerialName("doc_type")
    @Serializable(with = FlexibleStringSerializer::class)
    val docType: String? = null,
)

// GET /packages/{id} `data` object; invoices arrive bare or wrapped under
// {documents:[...], html_invoice, total_documents}.
@Serializable(with = PackageDetailSerializer::class)
data class PackageDetail(
    val id: Int = 0,
    val trackingCode: String? = null,
    val invoiceId: Int? = null,
    val description: String? = null,
    val status: String? = null,
    val statusName: String? = null,
    val weight: String? = null,
    val weightKg: String? = null,
    val weightLbs: Double? = null,
    val store: String? = null,
    val userId: Int? = null,
    val accountNumber: String? = null,
    val amount: Double? = null,
    val originalPrice: Double? = null,
    val shippingPrice: Double? = null,
    val totalPrice: Double? = null,
    val additionalCharges: Map<String, Double> = emptyMap(),
    val additionalChargesTotal: Double? = null,
    val customDuty: Double? = null,
    val consignee: String? = null,
    val shipper: String? = null,
    val shipperId: Int? = null,
    val courierNumber: String? = null,
    val shippingMethod: String? = null,
    val adminRemarks: String? = null,
    val boxNumber: String? = null,
    val containerId: String? = null,
    val batchId: Int? = null,
    val pickupLocation: String? = null,
    val deliveryLocation: String? = null,
    val creationDate: String? = null,
    val updatedDate: String? = null,
    val statusUpdatedDate: String? = null,
    val inCart: Boolean? = null,
    val documentId: String? = null,
    val volume: Double? = null,
    val numberOfPieces: Int? = null,
    val exchangeRate: Double? = null,
    val history: List<PackageHistoryItem> = emptyList(),
    val invoices: List<PackageInvoiceDocument> = emptyList(),
)

object PackageDetailSerializer : KSerializer<PackageDetail> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.PackageDetail")

    override fun serialize(encoder: Encoder, value: PackageDetail) =
        throw UnsupportedOperationException("PackageDetail is decode-only")

    override fun deserialize(decoder: Decoder): PackageDetail {
        val input = decoder as JsonDecoder
        val json = input.json
        val obj = input.decodeJsonElement() as? JsonObject ?: return PackageDetail()

        val invoicesElement = obj["invoices"]
        val invoices: List<PackageInvoiceDocument> = when (invoicesElement) {
            is JsonObject -> json.decodeListOrNull(
                PackageInvoiceDocument.serializer(), invoicesElement["documents"],
            ) ?: emptyList()
            is JsonArray -> json.decodeOrNull(
                ListSerializer(PackageInvoiceDocument.serializer()), invoicesElement,
            ) ?: emptyList()
            else -> emptyList()
        }

        return PackageDetail(
            id = obj.flexInt("id", "package_id") ?: 0,
            trackingCode = obj.flexString("tracking_code", "package_tracking_code"),
            invoiceId = obj.flexInt("invoice_id"),
            description = obj.flexString("description", "package_description"),
            status = obj.flexString("status", "package_status"),
            statusName = obj.flexString("status_name"),
            weight = obj.flexString("weight", "package_weight"),
            weightKg = obj.flexString("weight_kg", "package_weight_kg"),
            weightLbs = obj.flexDouble("weight_lbs", "package_weight_lbs"),
            store = obj.flexString("store", "package_store"),
            userId = obj.flexInt("user_id"),
            accountNumber = obj.flexString("account_number"),
            amount = obj.flexDouble("amount", "package_amount"),
            // "package_orignal_price" is misspelled on the wire.
            originalPrice = obj.flexDouble("original_price", "package_orignal_price"),
            shippingPrice = obj.flexDouble("shipping_price", "package_shipping_price"),
            totalPrice = obj.flexDouble("total_price", "package_total_price"),
            additionalCharges = obj.flexDoubleMap(
                "additional_charges", "package_additional_charges", "charges",
            ) ?: emptyMap(),
            additionalChargesTotal = obj.flexDouble(
                "additional_charges_total", "package_additional_charges_total",
            ),
            customDuty = obj.flexDouble("custom_duty"),
            consignee = obj.flexString("consignee", "package_consignee"),
            shipper = obj.flexString("shipper", "package_shipper"),
            shipperId = obj.flexInt("shipper_id"),
            courierNumber = obj.flexString("courier_number", "package_couirer_number"),
            shippingMethod = obj.flexString("shipping_method"),
            adminRemarks = obj.flexString("admin_remarks", "package_admin_remarks"),
            boxNumber = obj.flexString("box_number"),
            containerId = obj.flexString("container_id"),
            batchId = obj.flexInt("batch_id"),
            pickupLocation = obj.flexString("pickup_location"),
            deliveryLocation = obj.flexString("delivery_location"),
            creationDate = obj.flexString("creation_date", "package_creation_date_time"),
            updatedDate = obj.flexString("updated_date"),
            statusUpdatedDate = obj.flexString("status_updated_date"),
            inCart = obj.flexBool("in_cart"),
            documentId = obj.flexString("document_id", "package_document_id"),
            volume = obj.flexDouble("volume"),
            numberOfPieces = obj.flexInt("number_of_pieces"),
            exchangeRate = obj.flexDouble("exchange_rate"),
            history = json.decodeListOrNull(PackageHistoryItem.serializer(), obj["history"])
                ?: emptyList(),
            invoices = invoices,
        )
    }
}

@Serializable
data class PackageInvoicesPayload(
    val documents: List<PackageInvoiceDocument>? = null,
)

@Serializable
data class PackageInvoicesMutationResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: PackageInvoicesPayload? = null,
)

// ── Server cart ──

@Serializable
data class CartPackage(
    @SerialName("package_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @SerialName("tracking_code")
    @Serializable(with = FlexibleStringSerializer::class)
    val trackingCode: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weight: Double? = null,
    @SerialName("weight_kg")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weightKg: Double? = null,
    @SerialName("weight_lbs")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weightLbs: Double? = null,
    @SerialName("number_of_pieces")
    @Serializable(with = FlexibleIntSerializer::class)
    val numberOfPieces: Int? = null,
    @SerialName("declared_value")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val declaredValue: Double? = null,
    @SerialName("shipping_cost")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val shippingCost: Double? = null,
    @SerialName("additional_charges")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val additionalCharges: Double? = null,
    @SerialName("additional_charges_total")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val additionalChargesTotal: Double? = null,
    @SerialName("total_charges")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val totalCharges: Double? = null,
    @SerialName("status_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val statusName: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val status: Int? = null,
)

@Serializable
data class CartBillingInfo(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
)

@Serializable
data class CartSnapshot(
    val packages: List<CartPackage> = emptyList(),
    @SerialName("package_count")
    @Serializable(with = FlexibleIntSerializer::class)
    val packageCount: Int? = null,
    @SerialName("total_amount")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val totalAmount: Double? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val currency: String? = null,
    @SerialName("tracking_codes_string")
    @Serializable(with = FlexibleStringSerializer::class)
    val trackingCodesString: String? = null,
    @SerialName("billing_info")
    val billingInfo: CartBillingInfo? = null,
) {
    val resolvedPackageCount: Int get() = packageCount ?: packages.size
}

@Serializable
data class PackageCartMutation(
    @SerialName("package_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val packageId: Int? = null,
    @SerialName("tracking_code")
    @Serializable(with = FlexibleStringSerializer::class)
    val trackingCode: String? = null,
    @SerialName("in_cart")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val inCart: Boolean? = null,
)

// ── Drop alerts ──

enum class DropAlertShippingMethod(val wireName: String) {
    AIRDROP_STANDARD("AIR"),
    SEADROP_STANDARD("SeaDrop"),
    EXPRESS("Express");

    companion object {
        fun fromDisplayName(displayName: String): DropAlertShippingMethod {
            val normalized = displayName.lowercase()
            return when {
                normalized.contains("sea") -> SEADROP_STANDARD
                normalized.contains("express") -> EXPRESS
                else -> AIRDROP_STANDARD
            }
        }
    }
}

@Serializable(with = DropAlertSerializer::class)
data class DropAlert(
    val id: Int = 0,
    val trackingCode: String? = null,
    val courierNumber: String? = null,
    val shippingMethod: String? = null,
    val shipper: String? = null,
    val store: String? = null,
    val amount: Double? = null,
    val consignee: String? = null,
    val description: String? = null,
    val status: Int? = null,
    val statusName: String? = null,
    val accountNumber: String? = null,
    val userId: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

object DropAlertSerializer : KSerializer<DropAlert> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.DropAlert")

    override fun serialize(encoder: Encoder, value: DropAlert) =
        throw UnsupportedOperationException("DropAlert is decode-only")

    override fun deserialize(decoder: Decoder): DropAlert {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return DropAlert()
        return DropAlert(
            id = obj.flexInt("id") ?: 0,
            trackingCode = obj.flexString("tracking_code"),
            courierNumber = obj.flexString("courier_number", "package_couirer_number"),
            shippingMethod = obj.flexString("shipping_method"),
            shipper = obj.flexString("shipper", "package_shipper"),
            store = obj.flexString("store", "package_store"),
            amount = obj.flexDouble("amount", "package_amount"),
            consignee = obj.flexString("consignee", "package_consignee"),
            description = obj.flexString("description", "package_description"),
            status = obj.flexInt("status"),
            statusName = obj.flexString("status_name"),
            accountNumber = obj.flexString("account_number"),
            userId = obj.flexInt("user_id"),
            createdAt = obj.flexString("created_at"),
            updatedAt = obj.flexString("updated_at"),
        )
    }
}

@Serializable
data class DropAlertResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: DropAlert? = null,
)
