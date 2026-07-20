package com.ga.airdrop.core.analytics

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.network.AuthInterceptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Checkout funnel diagnostics — Swift quality pass 89fbb11 AirdropFunnel:
 * fire-and-forget events buffered in memory and shipped in batches to
 * `POST /diagnostics/funnel` as `{"events":[{event, properties?, occurred_at}]}`.
 *
 * Contract (Swift):
 * - buffer flushes when it reaches [FLUSH_THRESHOLD] events or the app goes
 *   to background (AirdropApp counts started activities);
 * - each upload takes at most [BATCH_LIMIT] events, removed from the buffer
 *   BEFORE the request — upload failures are dropped silently (no retry, no
 *   disk queue);
 * - properties are scalar-only (String/Boolean/Int/Long/Double; Boolean is
 *   checked BEFORE Number when bridging Any) and must be PII-free;
 * - a 401 must never invalidate the session: the request opts out of the
 *   AuthInterceptor's refresh/force-logout path and attaches the bearer
 *   itself, so an auth failure is just a dropped batch.
 *
 * [log] is a no-op until [install] runs (AirdropApp.onCreate) so JVM unit
 * tests of ViewModels never buffer or upload anything.
 */
object AirdropFunnel {

    data class FunnelEvent(
        val event: String,
        val properties: Map<String, Any>?,
        val occurredAt: String,
    )

    internal const val FLUSH_THRESHOLD = 5
    internal const val BATCH_LIMIT = 20

    private val lock = Any()
    private val buffer = ArrayList<FunnelEvent>()

    @Volatile
    private var installed = false

    /** Injectable for tests — the default posts to /diagnostics/funnel. */
    internal var uploader: suspend (List<FunnelEvent>) -> Unit = ::postFunnelBatch

    /** Injectable for tests — flush work runs here (never the caller thread). */
    internal var flushScope: CoroutineScope = defaultScope()

    /** Arm the funnel (app process only — unit tests leave it disarmed). */
    fun install() {
        installed = true
    }

    /**
     * Record one event. Properties are bridged to scalars ([bridgeFunnelProperties]);
     * anything non-scalar is silently dropped. Never throws, never blocks on I/O.
     */
    fun log(event: String, properties: Map<String, Any?>? = null) {
        if (!installed) return
        val entry = FunnelEvent(
            event = event,
            properties = bridgeFunnelProperties(properties),
            occurredAt = iso8601Utc(Date()),
        )
        val shouldFlush = synchronized(lock) {
            buffer.add(entry)
            buffer.size >= FLUSH_THRESHOLD
        }
        if (shouldFlush) flush()
    }

    /**
     * Drain the buffer in batches of ≤[BATCH_LIMIT]. Each batch is removed
     * before its upload; a failed upload is dropped silently.
     */
    fun flush() {
        flushScope.launch {
            while (true) {
                val batch = nextBatch() ?: break
                runCatching { uploader(batch) }
            }
        }
    }

    private fun nextBatch(): List<FunnelEvent>? = synchronized(lock) {
        if (buffer.isEmpty()) return null
        val batch = ArrayList<FunnelEvent>(minOf(buffer.size, BATCH_LIMIT))
        repeat(minOf(buffer.size, BATCH_LIMIT)) { batch.add(buffer.removeAt(0)) }
        batch
    }

    internal fun pendingCount(): Int = synchronized(lock) { buffer.size }

    internal fun resetForTest(armed: Boolean = false) {
        synchronized(lock) { buffer.clear() }
        installed = armed
        uploader = ::postFunnelBatch
        flushScope = defaultScope()
    }

    private fun defaultScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun iso8601Utc(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(date)

    /**
     * POST {API base}/diagnostics/funnel. Opts out of the shared auth
     * interceptor (NO_AUTH header) and attaches the current bearer directly,
     * so a 401 can never trigger the refresh/force-logout path — Swift's
     * "401 must not kill session" rule for diagnostics.
     */
    private suspend fun postFunnelBatch(events: List<FunnelEvent>) {
        val payload = buildJsonObject {
            putJsonArray("events") {
                events.forEach { e ->
                    addJsonObject {
                        put("event", e.event)
                        e.properties?.takeIf { it.isNotEmpty() }?.let { props ->
                            putJsonObject("properties") {
                                props.forEach { (key, value) ->
                                    when (value) {
                                        is Boolean -> put(key, value)
                                        is String -> put(key, value)
                                        is Int -> put(key, value)
                                        is Long -> put(key, value)
                                        is Double -> put(key, value)
                                        is Number -> put(key, value.toDouble())
                                    }
                                }
                            }
                        }
                        put("occurred_at", e.occurredAt)
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/diagnostics/funnel")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header(AuthInterceptor.NO_AUTH_HEADER, "true")
            .apply {
                AuthTokenStore.snapshot().token?.let { header("Authorization", "Bearer $it") }
            }
            .build()
        withContext(Dispatchers.IO) {
            ApiClient.okHttp.newCall(request).execute().use { /* result ignored */ }
        }
    }
}

/**
 * Bridge caller properties to the scalar-only wire set. Boolean is matched
 * BEFORE any Number check (Swift NSNumber pitfall); Float/other numbers are
 * widened to Double; nulls and non-scalars are dropped. Returns null when
 * nothing survives so `properties` is omitted from the payload.
 */
internal fun bridgeFunnelProperties(properties: Map<String, Any?>?): Map<String, Any>? {
    if (properties.isNullOrEmpty()) return null
    val bridged = LinkedHashMap<String, Any>()
    for ((key, value) in properties) {
        when (value) {
            null -> Unit
            is Boolean -> bridged[key] = value
            is String -> bridged[key] = value
            is Int -> bridged[key] = value
            is Long -> bridged[key] = value
            is Double -> bridged[key] = value
            is Number -> bridged[key] = value.toDouble()
            else -> Unit // non-scalar (list/map/object) — dropped, PII-safe
        }
    }
    return bridged.takeIf { it.isNotEmpty() }
}
