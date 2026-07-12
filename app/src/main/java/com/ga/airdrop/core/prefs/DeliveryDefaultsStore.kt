package com.ga.airdrop.core.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Default delivery-method preference — Swift `DeliveryDefaultsStore`
 * (UserDefaults key "Airdrop.delivery.defaultMethod"). Device-local, no backend
 * call, cleared on logout via [clearAll] (mirrors Swift UserStateCache.clearAll
 * so a shared device doesn't carry the previous user's default). Forwards-
 * compatible: the checkout Delivery Method flow reads this once it lands — Swift
 * ships it as a stored preference with no consumer yet either.
 *
 * Writes are guarded on init() having bound prefs (the C7/C8 lesson): a call
 * before Application.onCreate no-ops instead of throwing.
 */
object DeliveryDefaultsStore {

    enum class Method(val raw: String, val displayName: String, val subtitle: String) {
        PICKUP(
            "pickup",
            "Counter pickup",
            "Pick up at the AirDrop counter when your package arrives. No delivery fee.",
        ),
        HOME(
            "homeDelivery",
            "Home delivery",
            "Deliver to your saved Jamaica address. A delivery fee applies based on distance.",
        );

        companion object {
            fun fromRaw(raw: String?): Method? = entries.firstOrNull { it.raw == raw }
        }
    }

    private const val PREFS = "airdrop_delivery"
    private const val KEY = "Airdrop.delivery.defaultMethod"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** null when the user has not chosen a default. */
    var preferredMethod: Method?
        get() = if (::prefs.isInitialized) Method.fromRaw(prefs.getString(KEY, null)) else null
        set(value) {
            if (!::prefs.isInitialized) return
            prefs.edit().apply {
                if (value == null) remove(KEY) else putString(KEY, value.raw)
            }.apply()
        }

    fun clearAll() {
        if (::prefs.isInitialized) prefs.edit().remove(KEY).apply()
    }
}
