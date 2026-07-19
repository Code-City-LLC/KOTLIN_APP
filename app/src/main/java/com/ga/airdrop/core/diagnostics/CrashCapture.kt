package com.ga.airdrop.core.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Crash capture → our own backend — port of Swift CrashCapture.swift
 * (deliberately NOT Crashlytics/Sentry; project isolation rule). Uncaught
 * exceptions serialize to `<filesDir>/crashes/<timestamp>-<uuid8>.json`
 * synchronously (the process is dying — best effort, no retries), then the
 * NEXT launch posts every file to `POST /api/v1/diagnostics/crashes`
 * (anonymous-friendly; server storm-caps at 100/hr per device+build+IP)
 * and deletes on acceptance.
 *
 * Field names follow the Laravel validator (DiagnosticsController):
 * `ios_version` is the server's OS-version slot — Android sends
 * "Android <release>" there rather than forking the schema.
 */
object CrashCapture {

    private const val DIR_NAME = "crashes"
    internal const val TYPE_UNCAUGHT = "uncaught_exception"
    /** Keep payloads well under body limits; stacks beyond this are cut. */
    internal const val MAX_STACK_LINES = 200
    internal const val MAX_REASON_CHARS = 4000

    private val json = Json { prettyPrint = false }
    @Volatile private var crashesDir: File? = null
    @Volatile private var installed = false

    /** Install the handler chain. Call once from Application.onCreate. */
    fun install(context: Context) {
        val dir = File(context.filesDir, DIR_NAME)
        crashesDir = dir
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { persist(dir, thread, throwable) }
            // Always hand back to the platform handler — the crash dialog /
            // process teardown must still happen.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Pending crash files, oldest first (empty when the dir is absent). */
    fun pendingCrashFiles(): List<File> =
        crashesDir?.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy(File::getName)
            .orEmpty()

    /**
     * Send every pending crash via [send]; delete a file when the server
     * ACCEPTED it (2xx) or judged it permanently malformed (422). Network
     * failures keep the file for the next launch.
     */
    suspend fun flush(send: suspend (JsonObject) -> Int) {
        for (file in pendingCrashFiles()) {
            val payload = runCatching {
                json.parseToJsonElement(file.readText()) as JsonObject
            }.getOrNull()
            if (payload == null) {
                file.delete() // unreadable forever — drop it
                continue
            }
            val status = runCatching { send(payload) }.getOrDefault(0)
            if (status in 200..299 || status == 422) file.delete()
        }
    }

    private fun persist(dir: File, thread: Thread, throwable: Throwable) {
        if (!dir.exists() && !dir.mkdirs()) return
        val timestamp = iso8601Utc(Date())
        val payload = buildCrashPayload(
            throwable = throwable,
            threadName = thread.name,
            occurredAtIso = timestamp,
            appVersion = com.ga.airdrop.BuildConfig.VERSION_NAME,
            buildNumber = com.ga.airdrop.BuildConfig.VERSION_CODE.toString(),
            osVersion = "Android " + Build.VERSION.RELEASE,
            deviceModel = Build.MODEL.orEmpty(),
            deviceName = (Build.MANUFACTURER.orEmpty() + " " + Build.MODEL.orEmpty()).trim(),
            bundleId = com.ga.airdrop.BuildConfig.APPLICATION_ID,
        )
        val safeTs = timestamp.replace(":", "-")
        val name = "$safeTs-${UUID.randomUUID().toString().take(8)}.json"
        File(dir, name).writeText(json.encodeToString(JsonObject.serializer(), payload))
    }

    internal fun iso8601Utc(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(date)
}

/** Pure payload builder — Laravel DiagnosticsController field names. */
internal fun buildCrashPayload(
    throwable: Throwable,
    threadName: String,
    occurredAtIso: String,
    appVersion: String,
    buildNumber: String,
    osVersion: String,
    deviceModel: String,
    deviceName: String,
    bundleId: String,
): JsonObject {
    val stack = throwable.stackTraceToString()
        .lineSequence()
        .take(CrashCapture.MAX_STACK_LINES)
        .toList()
    return buildJsonObject {
        put("type", CrashCapture.TYPE_UNCAUGHT)
        put("exception_name", throwable.javaClass.name)
        put(
            "exception_reason",
            throwable.message.orEmpty().take(CrashCapture.MAX_REASON_CHARS),
        )
        put(
            "call_stack_symbols",
            buildJsonArray { stack.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
        )
        put("user_info", "thread:$threadName")
        put("occurred_at", occurredAtIso)
        put("app_version", appVersion)
        put("build_number", buildNumber)
        put("ios_version", osVersion)
        put("device_model", deviceModel.take(64))
        put("device_name", deviceName.take(64))
        put("bundle_id", bundleId)
    }
}
