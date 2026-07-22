package com.ga.airdrop.core.push

import java.util.Locale

/**
 * Canonical package reference selection shared by tray pushes and the in-app
 * notification inbox.
 *
 * A real positive package ID always wins. Tracking/courier references remain
 * references only; they must be resolved against the authenticated package
 * list before Package Details calls GET /packages/{id}.
 */
internal object PackageDeepLinkReference {

    private val signedInteger = Regex("^[+-]?\\d+$")

    fun select(
        packageIdCandidates: Iterable<String?>,
        aliasCandidates: Iterable<String?>,
    ): String? {
        packageIdCandidates.firstNotNullOfOrNull(::positiveId)?.let {
            return it.toString()
        }
        return aliasCandidates.firstNotNullOfOrNull(::routeReference)
    }

    fun positiveId(value: String?): Int? {
        val trimmed = value?.trim().orEmpty()
        if (!signedInteger.matches(trimmed)) return null
        val number = trimmed.toLongOrNull() ?: return null
        return number.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }

    /**
     * Keeps an exact tracking/courier alias, canonicalizes positive numeric
     * IDs, and rejects zero/negative integer references.
     */
    fun routeReference(value: String?): String? {
        val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        positiveId(trimmed)?.let { return it.toString() }
        // All-digit references are package IDs, never tracking aliases. Fail
        // closed for zero, negatives, Int overflow, and values too large for
        // Long instead of routing them as preview/courier text.
        if (signedInteger.matches(trimmed)) return null
        return trimmed
    }

    fun normalized(value: String?): String? =
        routeReference(value)?.uppercase(Locale.US)
}
