package com.ga.airdrop.feature.calculator

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.prefs.ExchangeRateStore
import com.ga.airdrop.data.model.arrayAt
import com.ga.airdrop.data.model.flexDouble
import com.ga.airdrop.data.model.flexInt
import com.ga.airdrop.data.model.flexString
import com.ga.airdrop.data.model.objectAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale

// RECONCILE: data/api/AirdropApiService + data/repo/* are being landed by the
// data-layer agent. When they exist, move these endpoints into the shared
// service (POST /shipping/calculate, GET /products, GET /exchange-rates) and
// delete RemoteCalculatorRepository — the interface below is the seam.

interface CalculatorRepository {
    /** POST /shipping/calculate — Swift `AirdropAPI.calculateShipment`. */
    suspend fun calculateShipment(
        shippingMethod: String,
        invoiceAmount: Double,
        weightLbs: Double?,
        numberOfPackages: Int = 1,
        lengthInches: Double? = null,
        widthInches: Double? = null,
        heightInches: Double? = null,
    ): ShipmentCalculation

    /** GET /products?search=… — Swift `AirdropAPI.searchProducts` (RN parity). */
    suspend fun searchProducts(query: String, limit: Int = 20): List<CalcProduct>

    /** GET /exchange-rates → usd_to_jmd; fallback 156.0 (Swift APIConfig.usdToJmdFallback). */
    suspend fun usdToJmdRate(): Double
}

/** Swift `ShipmentCalculationRequest` — field names/values verbatim. */
@Serializable
private data class ShipmentCalculationRequest(
    val shipping_method: String,
    val number_of_packages: Int,
    val invoice_amount: Double,
    val weight_unit: String = "lbs",
    val dimension_unit: String = "inch",
    // Do NOT invent duty rates: the Swift app always sends 45 and displays
    // whatever `breakdown.customs_duty` the server answers with.
    val custom_duty_percentage: Double = 45.0,
    val incorrect_shipping_info: Boolean = false,
    val weight_lbs: Double? = null,
    val package_length: Double? = null,
    val package_width: Double? = null,
    val package_height: Double? = null,
)

class RemoteCalculatorRepository(
    private val client: OkHttpClient = ApiClient.okHttp,
    private val json: Json = ApiClient.json,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) : CalculatorRepository {

    // USD→JMD fallback comes from the shared ExchangeRateStore (last-known live
    // rate, server-seeded 160.625) instead of a private 156.0 constant — the
    // calculator was the one screen quoting a different offline JMD total than
    // Cart/Shop/Shipments (Swift carries the same APIConfig drift; the shared
    // store is the app-wide SSOT per the 6f8f8af rate-store work).

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun calculateShipment(
        shippingMethod: String,
        invoiceAmount: Double,
        weightLbs: Double?,
        numberOfPackages: Int,
        lengthInches: Double?,
        widthInches: Double?,
        heightInches: Double?,
    ): ShipmentCalculation = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            ShipmentCalculationRequest.serializer(),
            ShipmentCalculationRequest(
                shipping_method = shippingMethod,
                number_of_packages = maxOf(1, numberOfPackages),
                invoice_amount = invoiceAmount,
                weight_lbs = weightLbs,
                package_length = lengthInches,
                package_width = widthInches,
                package_height = heightInches,
            ),
        )
        val request = Request.Builder()
            .url(url("/shipping/calculate"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (!response.isSuccessful) {
                throw IOException(root?.flexString("message") ?: "Calculation failed (${response.code}).")
            }
            root ?: throw IOException("Empty calculation response.")
            // Payload lives under `data` when present — Swift ShipmentCalculation decoder.
            val payload = root.objectAt("data")?.takeIf { it.objectAt("breakdown") != null } ?: root
            val breakdown = payload.objectAt("breakdown")
                ?: throw IOException(root.flexString("message") ?: "Malformed calculation response.")
            val calculations = payload.objectAt("calculations")
            ShipmentCalculation(
                shippingMethod = payload.flexString("shipping_method"),
                freight = breakdown.flexDouble("freight") ?: 0.0,
                insurance = breakdown.flexDouble("insurance") ?: 0.0,
                fuelSurcharge = breakdown.flexDouble("fuel_surcharge") ?: 0.0,
                airdropCharges = breakdown.flexDouble("airdrop_charges") ?: 0.0,
                customsDuty = breakdown.flexDouble("customs_duty") ?: 0.0,
                totalWithDuty = breakdown.flexDouble("total_with_duty") ?: 0.0,
                cifValue = calculations?.flexDouble("cif_value") ?: 0.0,
                totalWeightLbs = calculations?.flexDouble("total_weight_lbs"),
            )
        }
    }

    override suspend fun searchProducts(query: String, limit: Int): List<CalcProduct> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 3) return@withContext emptyList()
            // Swift searchProducts → auctionProducts(filters: search, order:
            // created_at desc, in_stock).
            val httpUrl = url("/products").toHttpUrl().newBuilder()
                .addQueryParameter("page", "1")
                .addQueryParameter("per_page", limit.toString())
                .addQueryParameter("search", trimmed)
                .addQueryParameter("order", "created_at")
                .addQueryParameter("direction", "desc")
                .addQueryParameter("in_stock", "1")
                .build()
            val request = Request.Builder().url(httpUrl).get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("Product search failed (${response.code}).")
                val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
                val array = when (element) {
                    is kotlinx.serialization.json.JsonArray -> element
                    is JsonObject -> element.arrayAt("data", "products", "items")
                    else -> null
                } ?: return@use emptyList()
                array.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    CalcProduct(
                        id = obj.flexInt("id") ?: 0,
                        title = obj.flexString("name") ?: obj.flexString("title") ?: "Product",
                        displayPrice = obj.displayPrice(),
                    )
                }
            }
        }

    /** Swift AuctionProduct.displayPrice — first parseable of the price fields. */
    private fun JsonObject.displayPrice(): String {
        val value = flexDouble("current_price")
            ?: flexDouble("sale_price")
            ?: flexDouble("price")
            ?: flexDouble("regular_price")
            ?: flexDouble("price_usd")
            ?: 0.0
        return String.format(Locale.US, "$%,.2f", value)
    }

    override suspend fun usdToJmdRate(): Double = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/exchange-rates")).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(text) as? JsonObject
                root?.flexDouble("usd_to_jmd")
                    ?: root?.objectAt("data")?.flexDouble("usd_to_jmd")
            }
        }.getOrNull() ?: ExchangeRateStore.current
    }
}
