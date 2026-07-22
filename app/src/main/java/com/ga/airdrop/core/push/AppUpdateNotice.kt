package com.ga.airdrop.core.push

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.data.model.AirdropNotification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One fail-closed contract for update notices from both FCM and the Laravel inbox.
 * Payload-provided destinations are deliberately ignored: every accepted Android
 * update opens this one allowlisted Play listing.
 */
internal const val GOOGLE_PLAY_UPDATE_URL =
    "https://play.google.com/store/apps/details?id=com.ga.airdrop.app"

internal data class InstalledAppVersion(
    val versionName: String,
    val buildNumber: Long,
) {
    val registrationIdentity: String = "$versionName|$buildNumber"
}

internal object InstalledAppVersionProvider {
    @Volatile
    private var installed = InstalledAppVersion(
        versionName = BuildConfig.VERSION_NAME,
        buildNumber = BuildConfig.VERSION_CODE.toLong(),
    )

    fun initialize(context: Context) {
        installed = context.installedAppVersion()
    }

    fun current(): InstalledAppVersion = installed
}

internal sealed interface AppUpdateEvaluation {
    data object NotAnUpdate : AppUpdateEvaluation
    data object Suppressed : AppUpdateEvaluation
    data class Eligible(
        val latestVersion: String,
        val minimumSupportedVersion: String?,
    ) : AppUpdateEvaluation
}

private val updatePayloadJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val updateTypes = setOf(
    "app_update",
    "app_update_available",
    "update_available",
    "force_update",
)

internal fun evaluateAppUpdateNotification(
    notification: AirdropNotification,
    installedVersion: String = InstalledAppVersionProvider.current().versionName,
): AppUpdateEvaluation = evaluateAppUpdate(
    fields = notification.payload,
    explicitType = notification.type,
    installedVersion = installedVersion,
)

internal fun evaluateAppUpdatePush(
    data: Map<String, String>,
    installedVersion: String = InstalledAppVersionProvider.current().versionName,
): AppUpdateEvaluation {
    val fields = flattenPushPayload(data)
    return evaluateAppUpdate(
        fields = fields,
        explicitType = fields.firstValue("type", "notification_type"),
        installedVersion = installedVersion,
    )
}

internal fun AirdropNotification.isVisibleForInstalledApp(
    installedVersion: String = InstalledAppVersionProvider.current().versionName,
): Boolean = evaluateAppUpdateNotification(this, installedVersion) != AppUpdateEvaluation.Suppressed

internal fun isRecognizedAppUpdateType(raw: String?): Boolean =
    raw?.trim()?.lowercase()?.replace('-', '_') in updateTypes

internal fun compareNumericVersions(left: String, right: String): Int? {
    val leftParts = left.numericVersionParts() ?: return null
    val rightParts = right.numericVersionParts() ?: return null
    val width = maxOf(leftParts.size, rightParts.size)
    repeat(width) { index ->
        val leftPart = leftParts.getOrElse(index) { 0L }
        val rightPart = rightParts.getOrElse(index) { 0L }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    return 0
}

internal fun googlePlayUpdateIntent(): Intent = Intent(
    Intent.ACTION_VIEW,
    Uri.parse(GOOGLE_PLAY_UPDATE_URL),
).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun launchGooglePlayUpdate(context: Context): Boolean =
    runCatching { context.startActivity(googlePlayUpdateIntent()) }.isSuccess

private fun evaluateAppUpdate(
    fields: Map<String, String>,
    explicitType: String?,
    installedVersion: String,
): AppUpdateEvaluation {
    val type = explicitType ?: fields.firstValue("type", "notification_type")
    if (!isRecognizedAppUpdateType(type)) return AppUpdateEvaluation.NotAnUpdate

    val platform = fields.firstValue("platform")?.trim()?.lowercase()
    val latestVersion = fields.firstValue("latest_version", "latestVersion")?.trim()
    val minimumVersion = fields.firstValue(
        "minimum_supported_version",
        "minimumSupportedVersion",
        "minimum_version",
    )?.trim()?.takeIf(String::isNotEmpty)

    if (platform != "android" || latestVersion.isNullOrEmpty()) {
        return AppUpdateEvaluation.Suppressed
    }
    if (minimumVersion != null && minimumVersion.numericVersionParts() == null) {
        return AppUpdateEvaluation.Suppressed
    }
    val comparison = compareNumericVersions(latestVersion, installedVersion)
        ?: return AppUpdateEvaluation.Suppressed
    if (comparison <= 0) return AppUpdateEvaluation.Suppressed

    return AppUpdateEvaluation.Eligible(latestVersion, minimumVersion)
}

internal fun flattenPushPayload(data: Map<String, String>): Map<String, String> = buildMap {
    for (containerKey in listOf("data_payload", "data")) {
        val raw = data.entries.firstOrNull { it.key.equals(containerKey, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: continue
        val nested = runCatching { updatePayloadJson.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?: continue
        for ((key, value) in nested) {
            val primitive = value as? JsonPrimitive ?: continue
            put(key, primitive.content)
        }
    }
    // Direct FCM keys are authoritative when a sender includes both a nested
    // Laravel payload and top-level delivery metadata.
    putAll(data)
}

private fun Map<String, String>.firstValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { expected ->
        entries.firstOrNull { (key, value) ->
            key.equals(expected, ignoreCase = true) && value.isNotBlank()
        }?.value
    }

private fun String.numericVersionParts(): List<Long>? {
    val normalized = trim()
    if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]+)*"))) return null
    return normalized.split('.').map { it.toLongOrNull() ?: return null }
}

@Suppress("DEPRECATION")
private fun Context.installedAppVersion(): InstalledAppVersion {
    val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
            packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
    val versionName = packageInfo.versionName?.trim()?.takeIf(String::isNotEmpty)
        ?: BuildConfig.VERSION_NAME
    val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }
    return InstalledAppVersion(versionName, buildNumber)
}
