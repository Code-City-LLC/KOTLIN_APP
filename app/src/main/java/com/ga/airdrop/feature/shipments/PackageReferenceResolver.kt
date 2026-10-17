package com.ga.airdrop.feature.shipments

import java.util.Locale

/**
 * One canonical exact-reference lookup shared by Quick Track and notification
 * Package Details handoffs.
 *
 * Notification routes may carry a numeric package id, a tracking code, or a
 * courier number. Numeric references are searched first because courier values
 * can also be all digits; only when no exact tracking/courier match exists may
 * an explicitly canonical numeric reference fall back to `/packages/{id}`.
 * Broad server search results can therefore never open the wrong package.
 */
internal data class ResolvedPackageReference(
    val packageId: Int,
    val matchedPackage: ShipmentPackage?,
)

internal class PackageReferenceNotFoundException(reference: String) :
    NoSuchElementException("No package found for $reference. Check the code and try again.")

internal suspend fun resolvePackageReference(
    reference: String,
    packagesRepo: ShipmentsPackagesRepository,
    shortlist: suspend () -> Result<List<ShipmentPackage>>,
    numericIdIsCanonical: Boolean,
): Result<ResolvedPackageReference> = runCatching {
    val code = reference.trim().uppercase(Locale.US)
    require(code.isNotEmpty()) { "Enter a tracking number to continue." }

    val numericId = code.toIntOrNull()?.takeIf { it > 0 }
    val searchResults = packagesRepo
        .packages(
            page = 1,
            perPage = 20,
            status = null,
            search = code,
            shippingMethod = null,
        )
        .getOrThrow()
        .items
    searchResults.exactPackageReferenceMatch(code, includePackageId = false)?.let { match ->
        return@runCatching ResolvedPackageReference(match.id, matchedPackage = match)
    }

    val shortlistResults = shortlist().getOrDefault(emptyList())
    shortlistResults.exactPackageReferenceMatch(code, includePackageId = false)?.let { match ->
        return@runCatching ResolvedPackageReference(match.id, matchedPackage = match)
    }

    if (numericIdIsCanonical && numericId != null) {
        return@runCatching ResolvedPackageReference(numericId, matchedPackage = null)
    }

    val match = searchResults.exactPackageReferenceMatch(code)
        ?: shortlistResults.exactPackageReferenceMatch(code)
        ?: throw PackageReferenceNotFoundException(code)

    ResolvedPackageReference(match.id, matchedPackage = match)
}

internal fun Iterable<ShipmentPackage>.exactPackageReferenceMatch(
    reference: String,
    includePackageId: Boolean = true,
): ShipmentPackage? {
    val code = reference.trim().uppercase(Locale.US)
    // The drop number as the app displays it ("ARD 153 998") must find the
    // stored code ("ARD00000153998") too. Courier numbers stay as typed.
    val storedCode = ShipmentsFormat.normalizeTrackingCode(code)
    val referenceMatch = firstOrNull { pkg ->
        pkg.trackingCode.matchesPackageReference(code) ||
            pkg.trackingCode.matchesPackageReference(storedCode) ||
            pkg.courierNumber.matchesPackageReference(code)
    }
    if (referenceMatch != null || !includePackageId) return referenceMatch
    return firstOrNull { pkg -> pkg.id.toString() == code }
}

private fun String?.matchesPackageReference(reference: String): Boolean =
    this?.trim()?.uppercase(Locale.US) == reference
