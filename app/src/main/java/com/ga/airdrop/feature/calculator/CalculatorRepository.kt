package com.ga.airdrop.feature.calculator

import com.ga.airdrop.feature.shipments.ShipmentsFormat
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.prefs.ExchangeRateStore
import com.ga.airdrop.data.model.flexDouble
import com.ga.airdrop.data.model.flexBool
import com.ga.airdrop.data.model.flexInt
import com.ga.airdrop.data.model.flexString
import com.ga.airdrop.data.model.objectAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    /** POST /shipments/quote — final Airdrop pricing from Laravel's tier engine. */
    suspend fun quoteShipment(request: TierQuoteRequest): TierQuote =
        throw UnsupportedOperationException("Tier quote is not implemented by this repository.")

    /** POST /shipping/calculate — Swift `AirdropAPI.calculateShipment`. */
    suspend fun calculateShipment(
        shippingMethod: String,
        invoiceAmount: Double,
        weightLbs: Double?,
        numberOfPackages: Int = 1,
        lengthInches: Double? = null,
        widthInches: Double? = null,
        heightInches: Double? = null,
        /**
         * The customs classification the customer picked, resolved SERVER-side.
         * Same principle as the deliberately-absent `custom_duty_percentage`
         * below: the client names the item, the server owns the rate.
         */
        customDutyRateId: Int? = null,
    ): ShipmentCalculation

    /** Search the screen's customs catalogue, ranked as in Swift. No prices. */
    suspend fun searchDutyRates(query: String, limit: Int = 1000): List<CalcDutyRate>

    /** GET /exchange-rates → the live USD→JMD rate; last known rate if unreachable. */
    suspend fun usdToJmdRate(): Double
}

/** Preserves Laravel's machine-readable quote error code for UI decisions. */
class TierQuoteException(
    val errorCode: String?,
    message: String,
) : IOException(message)

/** Swift `ShipmentCalculationRequest` — field names/values verbatim. */
@Serializable
private data class ShipmentCalculationRequest(
    val shipping_method: String,
    val number_of_packages: Int,
    val invoice_amount: Double,
    val weight_unit: String = "lbs",
    val dimension_unit: String = "inch",
    // custom_duty_percentage is deliberately ABSENT. BrightHarbor #79936:
    // "raw custom_duty_percentage is accepted for legacy compatibility, but
    // mobile must NOT author it. Omit it so server defaults apply." Kotlin was
    // hardcoding 45.0 — a duty rate the client has no business choosing.
    val incorrect_shipping_info: Boolean = false,
    // The customs classification id. The server validates it is active and
    // resolves the percentage itself (CalculateShippingRequest.php:33-37), so
    // this is the sanctioned way to influence duty — unlike the raw percentage
    // above, which mobile must never author.
    val custom_duty_rate_id: Int? = null,
    val weight_lbs: Double? = null,
    val package_length: Double? = null,
    val package_width: Double? = null,
    val package_height: Double? = null,
)

@Serializable
private data class TierQuotePayload(
    val weight: Double,
    val method: String? = null,
    val destination: String? = null,
    val declared_value: Double? = null,
    val insured_value: Double? = null,
    val item_name: String? = null,
    val insurance_declined: Boolean? = null,
    val return_weight: Double? = null,
    val storage_charge: Double? = null,
    val delivery_charge: Double? = null,
)

class RemoteCalculatorRepository(
    private val client: OkHttpClient = ApiClient.okHttp,
    private val json: Json = ApiClient.json,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) : CalculatorRepository {

    private val dutyCatalogMutex = Mutex()
    private var dutyCatalog: List<CalcDutyRate>? = null

    // USD→JMD fallback comes from the shared ExchangeRateStore (last-known live
    // rate, server-seeded 160.625) instead of a private 156.0 constant — the
    // calculator was the one screen quoting a different offline JMD total than
    // Cart/Shop/Shipments (Swift carries the same APIConfig drift; the shared
    // store is the app-wide SSOT per the 6f8f8af rate-store work).

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun quoteShipment(request: TierQuoteRequest): TierQuote = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            TierQuotePayload.serializer(),
            TierQuotePayload(
                weight = request.weightLbs,
                method = request.method,
                destination = request.destination,
                declared_value = request.declaredValue,
                insured_value = request.insuredValue,
                item_name = request.itemName,
                insurance_declined = request.insuranceDeclined,
                return_weight = request.returnWeight,
                storage_charge = request.storageCharge,
                delivery_charge = request.deliveryCharge,
            ),
        )
        val httpRequest = Request.Builder()
            .url(url("/shipments/quote"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (!response.isSuccessful) {
                throw TierQuoteException(
                    errorCode = root?.flexString("error_code"),
                    message = root?.flexString("message")
                        ?: "Quote request failed (${response.code}).",
                )
            }
            root ?: throw IOException("Empty shipment quote response.")
            if (root.flexBool("success") == false) {
                throw TierQuoteException(
                    errorCode = root.flexString("error_code"),
                    message = root.flexString("message") ?: "Shipment quote request failed.",
                )
            }
            val payload = root.objectAt("data") ?: root
            payload.decodeTierQuote()
        }
    }

    override suspend fun calculateShipment(
        shippingMethod: String,
        invoiceAmount: Double,
        weightLbs: Double?,
        numberOfPackages: Int,
        lengthInches: Double?,
        widthInches: Double?,
        heightInches: Double?,
        customDutyRateId: Int?,
    ): ShipmentCalculation = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            ShipmentCalculationRequest.serializer(),
            ShipmentCalculationRequest(
                shipping_method = shippingMethod,
                number_of_packages = maxOf(1, numberOfPackages),
                invoice_amount = invoiceAmount,
                custom_duty_rate_id = customDutyRateId,
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
                // ⚠️ `total_with_duty` and `total_charges` are NOT totals.
                // Measured live: on airdrop_standard `total_with_duty` == 90.90
                // == `customs_duty`; on airdrop_express `total_charges` == 87.30
                // == `customs_duty`; on seadrop `total_charges` == 202.50 and
                // folds in the merchant invoice. One key, three meanings.
                //
                // `grand_total` is composed server-side, identically for every
                // method, as round(airdrop_charges + customs_duty, 2) — see
                // ShippingCalculatorService::withCanonicalKeys(). It is not an
                // alias of any legacy key; aliasing was rejected because the
                // legacy keys disagree. Three-of-three consensus, BronzeMountain
                // #80146. The legacy keys are read only as a fallback for a
                // server that predates grand_total.
                totalWithDuty = breakdown.flexDouble("grand_total")
                    ?: breakdown.flexDouble("total_with_duty")
                    ?: breakdown.flexDouble("total_charges")
                    ?: 0.0,
                // Conditional — it applies only when the address is bad, so it
                // is deliberately NOT inside grand_total. Folding it in would
                // OVER-quote every normal customer, the mirror of the
                // under-quote just fixed. Rendered as its own line when non-zero.
                badAddressFee = breakdown.flexDouble("bad_address_fee"),
                tariff = breakdown.flexDouble("tariff"),
                billOfLadingProcessing = breakdown.flexDouble("bill_of_lading_processing"),
                cifValue = calculations?.flexDouble("cif_value") ?: 0.0,
                totalWeightLbs = calculations?.flexDouble("total_weight_lbs"),
            )
        }
    }

    private fun JsonObject.decodeTierQuote(): TierQuote {
        val rawItems = this["line_items"] as? JsonArray
            ?: throw IOException(flexString("message") ?: "Malformed shipment quote response.")
        val lineItems = rawItems.mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            TierLineItem(
                code = item.flexString("code") ?: "",
                label = item.flexString("label"),
                amount = item.flexDouble("amount") ?: 0.0,
            )
        }
        val insuranceOptions = objectAt("insurance_options")?.let { options ->
            TierInsuranceOptions(
                insuredValue = options.flexDouble("insured_value") ?: 0.0,
                ratePer100 = options.flexDouble("rate_per_100") ?: 0.0,
                blockSize = options.flexInt("block_size") ?: 100,
                blocks = options.flexInt("blocks") ?: 0,
                premium = options.flexDouble("premium") ?: 0.0,
                maxCoverage = options.flexDouble("max_coverage"),
                coveredValue = options.flexDouble("covered_value") ?: 0.0,
                canDecline = options.flexBool("can_decline") ?: false,
                mandatory = options.flexBool("mandatory") ?: true,
                explicitRequired = options.flexBool("explicit_required") ?: false,
            )
        }
        return TierQuote(
            quoteReference = flexString("quote_reference"),
            customerTier = flexString("customer_tier"),
            method = flexString("method"),
            destination = flexString("destination"),
            currency = flexString("currency"),
            lineItems = lineItems,
            subtotal = flexDouble("subtotal") ?: 0.0,
            totalDue = flexDouble("total_due") ?: 0.0,
            status = flexString("status"),
            isExpired = flexBool("is_expired") ?: false,
            expiresAt = flexString("expires_at"),
            insuranceOptions = insuranceOptions,
            insuranceChoiceRequired = flexBool("insurance_choice_required") ?: false,
            aircoinsEarned = flexDouble("aircoins_earned") ?: 0.0,
        )
    }

    /**
     * The customs catalogue, NOT the shop.
     *
     * This used to call `GET /products?in_stock=1` and map rows to a title and
     * a `displayPrice` — the auction listing. A customer classifying a laptop
     * for duty was shown laptops for sale with dollar amounts beside them, and
     * the id they picked was an auction product id that `/shipping/calculate`
     * had no field to receive. Wrong object, wrong endpoint, dead selection.
     *
     * `/custom-duty-rates` returns `id, item_name, duty_percentage` and no
     * money at all, which is why `CalcDutyRate` has no price to render.
     */
    override suspend fun searchDutyRates(query: String, limit: Int): List<CalcDutyRate> =
        withContext(Dispatchers.IO) {
            val normalized = query.trim().lowercase(Locale.ROOT)
            if (normalized.length < 3) return@withContext emptyList()
            // One successful catalogue per repository/screen. A failed fetch
            // leaves no cache, and concurrent keystrokes share the same load.
            val catalog = dutyCatalogMutex.withLock {
                dutyCatalog ?: fetchDutyCatalog().also { dutyCatalog = it }
            }
            catalog.filter { it.itemName.lowercase(Locale.ROOT).contains(normalized) }
                .sortedWith(compareBy<CalcDutyRate> {
                    val name = it.itemName.lowercase(Locale.ROOT)
                    when {
                        name == normalized -> 0
                        name.startsWith(normalized) -> 1
                        else -> 2
                    }
                }.thenBy { it.itemName.lowercase(Locale.ROOT) }.thenBy { it.id })
                .take(limit.coerceAtLeast(0))
        }

    private fun fetchDutyCatalog(): List<CalcDutyRate> {
        val httpUrl = url("/custom-duty-rates").toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("per_page", "1000")
            // Laravel accepts "1", not the string "true".
            .addQueryParameter("active_only", "1")
            .build()
        val request = Request.Builder().url(httpUrl).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Duty rate search failed (${response.code}).")
            val element = try {
                json.parseToJsonElement(response.body?.string().orEmpty())
            } catch (error: IllegalArgumentException) {
                throw IOException("Invalid customs catalogue response.", error)
            }
            val array = when (element) {
                is JsonArray -> element
                is JsonObject -> element.paginatedArray()
                else -> null
            } ?: throw IOException("Missing customs catalogue in response.")
            array.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val id = obj.flexInt("id")?.takeIf { it > 0 } ?: return@mapNotNull null
                val name = obj.flexString("item_name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CalcDutyRate(id, name, obj.flexDouble("duty_percentage"))
            }
        }
    }

    /**
     * GET /exchange-rates.
     *
     * ⚠️ This used to read `usd_to_jmd` — a key the server does not send. The
     * live body is `{"data":{"id":1,"exchange_rate":"162.00","formatted_rate":162.0}}`,
     * with the rate as a STRING. `flexDouble("usd_to_jmd")` therefore returned
     * null on every call, `getOrNull()` collapsed to the stored value, and the
     * function silently "succeeded" with a stale rate — while
     * CalculatorViewModel then wrote that stale value BACK into
     * ExchangeRateStore as if it were live. The shared data layer was fixed for
     * exactly this (Shipping.kt ExchangeRate); the calculator's own hand-rolled
     * call was missed.
     *
     * Reads the real keys now, still preferring the last known rate over
     * nothing when the network is down — but a decode mismatch can no longer
     * masquerade as a successful fetch.
     */
    override suspend fun usdToJmdRate(): Double = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/exchange-rates")).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val root = json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                val data = root?.objectAt("data") ?: root
                // `exchange_rate` is a string; `formatted_rate` is a number.
                // `usd_to_jmd` is kept only for older deployments.
                data?.flexDouble("exchange_rate")
                    ?: data?.flexDouble("formatted_rate")
                    ?: data?.flexDouble("usd_to_jmd")
            }
        }.getOrNull()?.takeIf { it > 0 } ?: ExchangeRateStore.current
    }
}

/**
 * ⚠️ `arrayAt` DOES NOT WALK A PATH — it checks each key for a TOP-LEVEL array
 * and returns the first hit. I used `arrayAt("data", "items", ...)` in
 * `215903c3` as if it descended, so against Laravel's real
 * `{"data":{"items":[...]}}` every key missed, the parse fell through to null,
 * and a VALID duty-rate search rendered empty. Caught by QC before release
 * (ORC #99841/#99843).
 *
 * This mirrors `PaginatedSerializer.deserialize` (Envelopes.kt:67-103), which
 * is what every Retrofit-backed call in the app already uses. Raw OkHttp here
 * has to reproduce that order deliberately, in this exact sequence:
 *
 *   1. `data` holding an array          -> {"data":[...]}
 *   2. `data` holding an object         -> {"data":{"items":[...]}}   <- Laravel
 *   3. a list key at the top level      -> {"items":[...]}
 *
 * Keep it in step with LIST_KEYS if that list grows.
 */
private fun JsonObject.paginatedArray(): kotlinx.serialization.json.JsonArray? {
    val listKeys = listOf("items", "data", "custom_duty_rates", "results")
    (this["data"] as? kotlinx.serialization.json.JsonArray)?.let { return it }
    (this["data"] as? JsonObject)?.let { data ->
        for (key in listKeys) {
            (data[key] as? kotlinx.serialization.json.JsonArray)?.let { return it }
        }
    }
    for (key in listKeys) {
        (this[key] as? kotlinx.serialization.json.JsonArray)?.let { return it }
    }
    return null
}
