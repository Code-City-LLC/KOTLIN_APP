package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.push.PackageDeepLinkReference

internal const val MIN_PACKAGE_ALIAS_LENGTH = 3
internal const val PACKAGE_REFERENCE_LOOKUP_PER_PAGE = 50
internal const val MAX_PACKAGE_REFERENCE_LOOKUP_PAGES = 10

/**
 * Returns one unambiguous, positive package match. Tracking and courier
 * references compare exactly (case-insensitive); arbitrary digits embedded in
 * an alias never become a package ID.
 */
internal fun exactPackageReferenceMatch(
    packages: List<ShipmentPackage>,
    rawReference: String,
): ShipmentPackage? {
    val reference = PackageDeepLinkReference.routeReference(rawReference) ?: return null
    val normalized = PackageDeepLinkReference.normalized(reference) ?: return null
    val directId = PackageDeepLinkReference.positiveId(reference)
    val paddedAirdropId = normalized
        .takeIf { it.startsWith("ARD") }
        ?.removePrefix("ARD")
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

    return packages.asSequence()
        .filter { it.id > 0 }
        .filter { pkg ->
            pkg.id == directId ||
                pkg.id == paddedAirdropId ||
                PackageDeepLinkReference.normalized(pkg.trackingCode) == normalized ||
                PackageDeepLinkReference.normalized(pkg.courierNumber) == normalized
        }
        .distinctBy(ShipmentPackage::id)
        .toList()
        .singleOrNull()
}

internal fun packageAliasSearchTerm(rawReference: String): String? =
    PackageDeepLinkReference.routeReference(rawReference)
        ?.takeIf { PackageDeepLinkReference.positiveId(it) == null }
        ?.takeIf { it.length >= MIN_PACKAGE_ALIAS_LENGTH }

/**
 * Swift's package-reference lookup scans up to 10 pages at 50 rows. Preserve
 * the server pagination verdict when present and use batch size only when the
 * response had no pagination metadata.
 */
internal suspend fun packageReferenceSearchRows(
    repository: ShipmentsPackagesRepository,
    alias: String,
    isRequestCurrent: () -> Boolean = { true },
): Result<List<ShipmentPackage>> {
    val rows = mutableListOf<ShipmentPackage>()
    for (pageNumber in 1..MAX_PACKAGE_REFERENCE_LOOKUP_PAGES) {
        if (!isRequestCurrent()) return Result.success(emptyList())
        val pageResult = repository.packages(
            page = pageNumber,
            perPage = PACKAGE_REFERENCE_LOOKUP_PER_PAGE,
            status = null,
            search = alias,
            shippingMethod = null,
        )
        val page = pageResult.getOrElse { return Result.failure(it) }
        if (!isRequestCurrent()) return Result.success(emptyList())
        rows += page.items
        val reachedLastPage = page.isLastPage
            ?: (page.items.size < PACKAGE_REFERENCE_LOOKUP_PER_PAGE)
        if (reachedLastPage) break
    }
    return Result.success(rows)
}
