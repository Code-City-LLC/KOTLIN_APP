package com.ga.airdrop.feature.shipments

import android.content.Context
import android.content.SharedPreferences

/**
 * User-selectable Packages sort — Swift FigmaPackagesViewController §B.4
 * ("FigmaPackages.sortOrder.v1"): applied client-side after the search+filter
 * pass and persisted so the choice survives launches.
 */
enum class PackagesSort(val title: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    STATUS_AZ("Status (A-Z)"),
    TRACKING_AZ("Tracking # (A-Z)"),
}

/**
 * Pure comparator port of Swift applySortedOrder: creation date with an id
 * tiebreak for Newest/Oldest; case-insensitive status / tracking A-Z with a
 * newest-id tiebreak.
 */
fun sortPackages(packages: List<ShipmentPackage>, sort: PackagesSort): List<ShipmentPackage> =
    when (sort) {
        PackagesSort.NEWEST_FIRST -> packages.sortedWith(
            compareByDescending<ShipmentPackage> { it.createdAt ?: "" }.thenByDescending { it.id },
        )
        PackagesSort.OLDEST_FIRST -> packages.sortedWith(
            compareBy<ShipmentPackage> { it.createdAt ?: "" }.thenBy { it.id },
        )
        PackagesSort.STATUS_AZ -> packages.sortedWith(
            compareBy<ShipmentPackage> { (it.statusName ?: it.status ?: "").lowercase() }
                .thenByDescending { it.id },
        )
        PackagesSort.TRACKING_AZ -> packages.sortedWith(
            compareBy<ShipmentPackage> { (it.trackingCode ?: "").lowercase() }
                .thenByDescending { it.id },
        )
    }

/** SharedPreferences persistence for the chosen sort (Swift sortOrderKey). */
object PackagesSortStore {
    private const val PREFS = "packages_sort"
    private const val KEY = "sortOrder"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** The saved sort; NEWEST_FIRST until a first save (Swift default). */
    fun read(): PackagesSort = sortFor(prefs?.getString(KEY, null))

    fun save(sort: PackagesSort) {
        prefs?.edit()?.putString(KEY, sort.name)?.apply()
    }

    /** Pure name→sort resolution with the Swift .newestFirst fallback. */
    internal fun sortFor(name: String?): PackagesSort =
        PackagesSort.entries.firstOrNull { it.name == name } ?: PackagesSort.NEWEST_FIRST
}
