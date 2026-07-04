package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

// ── Warehouses (GET /warehouse) ──

@Serializable(with = WarehouseSerializer::class)
data class Warehouse(
    val id: Int = 0,
    val name: String? = null,
    val country: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipCode: String? = null,
    val phoneNumber: String? = null,
)

object WarehouseSerializer : KSerializer<Warehouse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.Warehouse")

    override fun serialize(encoder: Encoder, value: Warehouse) =
        throw UnsupportedOperationException("Warehouse is decode-only")

    override fun deserialize(decoder: Decoder): Warehouse {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return Warehouse()
        return Warehouse(
            id = obj.flexInt("id") ?: 0,
            name = obj.flexString("name"),
            country = obj.flexString("country"),
            address = obj.flexString("address", "address_line_1", "line_1"),
            city = obj.flexString("city"),
            state = obj.flexString("state"),
            zipCode = obj.flexString("zip_code"),
            phoneNumber = obj.flexString("phone_number"),
        )
    }
}

// ── Exchange rate (GET /exchange-rates) ──

@Serializable
data class ExchangeRate(
    @SerialName("usd_to_jmd")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val usdToJmd: Double? = null,
)

// ── Shipping rates (GET /shipping-rates, wrapped in {data:{...}}) ──

@Serializable
data class ShippingRate(
    @SerialName("weight_lbs") val weightLbs: Double? = null,
    @SerialName("rate_usd") val rateUsd: Double? = null,
)

@Serializable
data class AirdropStandardRates(
    val rates: List<ShippingRate>? = null,
    @SerialName("over_twenty_lb_rate") val overTwentyLbRate: Double? = null,
)

@Serializable
data class AirdropExpressRates(
    @SerialName("base_rate_per_lb") val baseRatePerLb: Double? = null,
    val note: String? = null,
)

@Serializable
data class SeaDropStandardRates(
    @SerialName("rate_per_cubic_foot") val ratePerCubicFoot: Double? = null,
    @SerialName("tariff_percentage") val tariffPercentage: Double? = null,
    @SerialName("insurance_percentage") val insurancePercentage: Double? = null,
)

@Serializable
data class AdditionalFees(
    @SerialName("fuel_surcharge") val fuelSurcharge: Double? = null,
    @SerialName("insurance_rate") val insuranceRate: Double? = null,
    @SerialName("incorrect_shipping_info") val incorrectShippingInfo: Double? = null,
    @SerialName("document_letter_rate") val documentLetterRate: Double? = null,
    @SerialName("customs_threshold") val customsThreshold: Double? = null,
)

@Serializable
data class ShippingRates(
    @SerialName("airdrop_standard") val airdropStandard: AirdropStandardRates? = null,
    @SerialName("airdrop_express") val airdropExpress: AirdropExpressRates? = null,
    @SerialName("seadrop_standard") val seadropStandard: SeaDropStandardRates? = null,
    @SerialName("additional_fees") val additionalFees: AdditionalFees? = null,
)

// ── Custom duty rates (GET /custom-duty-rates) ──

@Serializable
data class CustomDutyRate(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @SerialName("item_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val itemName: String? = null,
    @SerialName("duty_percentage")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val dutyPercentage: Double? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String? = null,
    @SerialName("is_active")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isActive: Boolean? = null,
    @SerialName("created_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val createdAt: String? = null,
    @SerialName("updated_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val updatedAt: String? = null,
)

// ── Shipments summary (GET /shipments/summary) ──

@Serializable(with = ShipmentSummarySerializer::class)
data class ShipmentSummary(
    val totalShipments: Int? = null,
    val totalPackages: Int? = null,
    val totalPayments: Int? = null,
    val totalOrders: Int? = null,
)

object ShipmentSummarySerializer : KSerializer<ShipmentSummary> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.ShipmentSummary")

    override fun serialize(encoder: Encoder, value: ShipmentSummary) =
        throw UnsupportedOperationException("ShipmentSummary is decode-only")

    override fun deserialize(decoder: Decoder): ShipmentSummary {
        val input = decoder as JsonDecoder
        val root = input.decodeJsonElement() as? JsonObject ?: return ShipmentSummary()
        val obj = root["data"] as? JsonObject ?: root
        return ShipmentSummary(
            totalShipments = obj.flexInt("total_shipments", "active_packages"),
            totalPackages = obj.flexInt("total_packages", "packages"),
            totalPayments = obj.flexInt("total_payments", "payments"),
            totalOrders = obj.flexInt("total_orders", "orders"),
        )
    }
}

// ── Shipping calculator (POST /shipping/calculate) ──

@Serializable
data class ShipmentCalculationRequest(
    @SerialName("shipping_method") val shippingMethod: String,
    @SerialName("number_of_packages") val numberOfPackages: Int,
    @SerialName("invoice_amount") val invoiceAmount: Double,
    @SerialName("weight_unit") val weightUnit: String,
    @SerialName("dimension_unit") val dimensionUnit: String,
    @SerialName("custom_duty_percentage") val customDutyPercentage: Double,
    @SerialName("incorrect_shipping_info") val incorrectShippingInfo: Boolean,
    @SerialName("weight_lbs") val weightLbs: Double? = null,
    @SerialName("package_length") val packageLength: Double? = null,
    @SerialName("package_width") val packageWidth: Double? = null,
    @SerialName("package_height") val packageHeight: Double? = null,
)

// Response payload: optional {data:{...}} wrap, then nested `breakdown`
// ({freight, insurance, fuel_surcharge, airdrop_charges, customs_duty,
// total_with_duty}) and `calculations` ({cif_value, total_weight_lbs}).
@Serializable(with = ShipmentCalculationSerializer::class)
data class ShipmentCalculation(
    val shippingMethod: String? = null,
    val freight: Double = 0.0,
    val insurance: Double = 0.0,
    val fuelSurcharge: Double = 0.0,
    val airdropCharges: Double = 0.0,
    val customsDuty: Double = 0.0,
    val totalWithDuty: Double = 0.0,
    val cifValue: Double = 0.0,
    val totalWeightLbs: Double? = null,
)

object ShipmentCalculationSerializer : KSerializer<ShipmentCalculation> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.ShipmentCalculation")

    override fun serialize(encoder: Encoder, value: ShipmentCalculation) =
        throw UnsupportedOperationException("ShipmentCalculation is decode-only")

    override fun deserialize(decoder: Decoder): ShipmentCalculation {
        val input = decoder as JsonDecoder
        val root = input.decodeJsonElement() as? JsonObject ?: return ShipmentCalculation()
        val payload = root["data"] as? JsonObject ?: root
        val breakdown = payload["breakdown"] as? JsonObject
        val calculations = payload["calculations"] as? JsonObject
        return ShipmentCalculation(
            shippingMethod = payload.flexString("shipping_method"),
            freight = breakdown?.flexDouble("freight") ?: 0.0,
            insurance = breakdown?.flexDouble("insurance") ?: 0.0,
            fuelSurcharge = breakdown?.flexDouble("fuel_surcharge") ?: 0.0,
            airdropCharges = breakdown?.flexDouble("airdrop_charges") ?: 0.0,
            customsDuty = breakdown?.flexDouble("customs_duty") ?: 0.0,
            totalWithDuty = breakdown?.flexDouble("total_with_duty") ?: 0.0,
            cifValue = calculations?.flexDouble("cif_value") ?: 0.0,
            totalWeightLbs = calculations?.flexDouble("total_weight_lbs"),
        )
    }
}
