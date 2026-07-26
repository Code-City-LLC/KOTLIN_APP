package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.model.Warehouse

/**
 * Address Line 2 as the customer must copy it onto a US merchant checkout.
 *
 * **Kemar's format (2026-07-26), consistent across Laravel, Kotlin and Swift:**
 * ```
 * Unit G36 - AIR – 14823
 * Unit G36 - SEA – 14823
 * Unit G36 - EXPRESS – 14823
 * ```
 * i.e. `<warehouse unit><separator><method token><en-dash><account number>`.
 *
 * Two things this fixes:
 *  1. The **unit was missing entirely** — the app rendered only `AIR – 14823`,
 *     so a customer copying Address Line 2 lost the suite and the parcel could
 *     not be routed to our bay.
 *  2. SeaDrop rendered as **`SEADROP`**. It must be **`SEA`**.
 *
 * The token is SERVER-OWNED (`address_line_2_tokens` on the warehouse payload)
 * precisely so the three platforms cannot drift; [FALLBACK_TOKENS] is used only
 * when the field is absent, and matches what Laravel currently sends.
 *
 * This applies to addresses being **displayed**. It is not an identifier to
 * parse or store.
 */
object WarehouseAddressLine2 {

    /** Mirrors Laravel's `address_line_2_tokens` for older payloads. */
    val FALLBACK_TOKENS = mapOf(
        "standard" to "AIR",
        "express" to "EXPRESS",
        "seadrop" to "SEA",
    )

    /** Laravel's `address_line_2_separator`. */
    const val FALLBACK_SEPARATOR = " - "

    /** En dash, exactly as Kemar specified between token and account number. */
    private const val ACCOUNT_DASH = " – "

    /** The method token for [typeKey] ("standard" / "express" / "seadrop"). */
    fun token(warehouse: Warehouse?, typeKey: String): String {
        val key = typeKey.trim().lowercase()
        return warehouse?.addressLine2Tokens?.get(key)?.takeIf { it.isNotBlank() }
            ?: FALLBACK_TOKENS[key]
            ?: key.uppercase()
    }

    /**
     * The full displayed line, or null when there is no account number to show
     * (rendering a bare unit would invite the customer to ship without their
     * account, which is how parcels become unclaimable).
     */
    fun format(warehouse: Warehouse?, typeKey: String, accountNumber: String?): String? {
        val account = accountNumber?.trim().orEmpty()
        if (account.isEmpty()) return null
        val token = token(warehouse, typeKey)
        val unit = warehouse?.unit?.trim().orEmpty()
        val separator = warehouse?.addressLine2Separator?.takeIf { it.isNotBlank() }
            ?: FALLBACK_SEPARATOR
        return if (unit.isEmpty()) {
            "$token$ACCOUNT_DASH$account"
        } else {
            "$unit$separator$token$ACCOUNT_DASH$account"
        }
    }
}
