package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

// GET /orders list item + /orders/{id} detail. Product data arrives either
// as flat order keys or nested under `product`; mirror Swift's cascade.
@Serializable(with = OrderSerializer::class)
data class Order(
    val id: Int = 0,
    val orderNumber: String? = null,
    val title: String? = null,
    val status: String? = null,
    val total: String? = null,
    val createdAt: String? = null,
    val productImage: String? = null,
    val customerName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val amazonLink: String? = null,
    val weightLbs: Double? = null,
    val weightKg: Double? = null,
    val invoiceAmountUsd: Double? = null,
    val orderStatus: String? = null,
    val paymentStatus: String? = null,
    val paymentMethod: String? = null,
    val invoiceId: String? = null,
    val productName: String? = null,
    val regularPriceUsd: Double? = null,
    val salePriceUsd: Double? = null,
    val purchasedAt: String? = null,
    val productStatus: String? = null,
    val exchangeRate: Double? = null,
) {
    val displayTitle: String get() = title ?: orderNumber ?: "Order #$id"
    val displaySubtitle: String get() = orderStatus ?: status ?: createdAt ?: "Order"
}

object OrderSerializer : KSerializer<Order> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.Order")

    override fun serialize(encoder: Encoder, value: Order) =
        throw UnsupportedOperationException("Order is decode-only")

    override fun deserialize(decoder: Decoder): Order {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return Order()

        val product = obj["product"] as? JsonObject
        val nestedName = product?.flexString("name")
        val nestedImage = product?.flexString("image_url")
            ?: (product?.get("images") as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.let { images ->
                    (images.firstOrNull { it.flexBool("is_primary") == true } ?: images.firstOrNull())
                        ?.flexString("image_url")
                }

        val orderStatus = obj.flexString("order_status")
        val createdAt = obj.flexString("created_at")
        val invoiceAmountUsd = obj.flexDouble("invoice_amount_usd")

        return Order(
            id = obj.flexInt("id") ?: 0,
            orderNumber = obj.flexString("order_number"),
            title = obj.flexString("title", "product_title") ?: nestedName,
            status = obj.flexString("status") ?: orderStatus,
            total = obj.flexString("total", "grand_total"),
            createdAt = createdAt,
            productImage = obj.flexString("product_image") ?: nestedImage,
            customerName = obj.flexString("customer_name"),
            email = obj.flexString("email"),
            phone = obj.flexString("phone"),
            amazonLink = obj.flexString("amazon_link"),
            weightLbs = obj.flexDouble("weight_lbs"),
            weightKg = obj.flexDouble("weight_kg"),
            invoiceAmountUsd = invoiceAmountUsd,
            orderStatus = orderStatus,
            paymentStatus = obj.flexString("payment_status"),
            paymentMethod = obj.flexString("payment_method"),
            invoiceId = obj.flexString("invoice_id"),
            productName = nestedName ?: obj.flexString("product_name"),
            regularPriceUsd = product?.flexDouble("regular_price")
                ?: obj.flexDouble("regular_price"),
            salePriceUsd = (product?.flexDouble("sale_price") ?: obj.flexDouble("sale_price"))
                ?: invoiceAmountUsd,
            purchasedAt = (product?.flexString("purchased_at") ?: obj.flexString("purchased_at"))
                ?: createdAt,
            productStatus = (product?.flexString("status") ?: obj.flexString("product_status"))
                ?: orderStatus,
            exchangeRate = obj.flexDouble("exchange_rate"),
        )
    }
}

// GET /orders/{id}: {data:{order}}, {data:<order>}, {order:<order>} or bare.
@Serializable(with = OrderDetailEnvelopeSerializer::class)
data class OrderDetailEnvelope(
    val order: Order? = null,
)

object OrderDetailEnvelopeSerializer : KSerializer<OrderDetailEnvelope> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.OrderDetailEnvelope")

    override fun serialize(encoder: Encoder, value: OrderDetailEnvelope) =
        throw UnsupportedOperationException("OrderDetailEnvelope is decode-only")

    override fun deserialize(decoder: Decoder): OrderDetailEnvelope {
        val input = decoder as JsonDecoder
        val json = input.json
        val obj = input.decodeJsonElement() as? JsonObject ?: return OrderDetailEnvelope()
        val data = obj["data"]
        val order = when (data) {
            is JsonObject -> json.decodeOrNull(Order.serializer(), data["order"])
                ?: json.decodeOrNull(Order.serializer(), data)
            else -> null
        }
            ?: json.decodeOrNull(Order.serializer(), obj["order"])
            ?: if (!obj.containsKey("data") && !obj.containsKey("order")) {
                json.decodeOrNull(Order.serializer(), obj)
            } else {
                null
            }
        return OrderDetailEnvelope(order)
    }
}
