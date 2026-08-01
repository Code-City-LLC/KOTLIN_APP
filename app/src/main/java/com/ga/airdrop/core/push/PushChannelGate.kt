package com.ga.airdrop.core.push

import com.ga.airdrop.core.config.AirdropFeatureFlags
import com.ga.airdrop.core.prefs.NotificationAccountPreferences
import com.ga.airdrop.core.prefs.NotificationPreferenceMatrix
import java.util.Locale

/**
 * Makes the Notification Settings **Push** switches actually do something.
 *
 * ## What was wrong
 *
 * Notification Settings renders nine controls. Exactly ONE of them reached the
 * server: the master "Notification (On/Off)", which calls
 * `PushRegistrar.setDevicePushEnabled` and genuinely registers or deletes the
 * FCM device token. **That one always worked.**
 *
 * The other eight — the two section switches and the six Email/SMS/Push
 * sub-switches — were written to [NotificationAccountPreferences] and read back
 * to paint the UI, and **nothing ever consulted them again**. `pushWanted()` is
 * literally `master`, so turning "Push" off under Promotions left the device
 * token registered and the promos arriving. The switch moved, persisted, and
 * survived a restart, which made it look *more* trustworthy than a switch that
 * did nothing at all.
 *
 * ## What this fixes, and what it CANNOT
 *
 * ✅ **Push** (both sections) is now enforced here, at delivery. A push whose
 * type belongs to a section whose Push switch is off is dropped.
 *
 * ❌ **Email and SMS cannot be enforced by any client.** Those are sent by the
 * server to an address and a phone number; the app is not in that path and
 * cannot intercept them. They need the API to accept the fields — see
 * [NotificationChannelSupport].
 *
 * ## ⚠️ The limit that must not be overstated
 *
 * This runs in `onMessageReceived`, which Android **does not call** for
 * background/killed-state pushes that carry a `notification` block — the system
 * posts those to the tray itself (documented in `PushDeepLink.capture`). So this
 * gate covers data-only pushes and foreground delivery. **Making it airtight
 * requires the SERVER to stop sending a suppressed category**, which is the same
 * endpoint Email/SMS needs.
 *
 * Suppression is therefore a best-effort improvement over "the switch is a lie",
 * not a guarantee, and the UI must not promise more than that.
 */
object PushChannelGate {

    /** Which section of Notification Settings a push belongs to. */
    enum class Category { PACKAGE, PROMO, OTHER }

    /**
     * Classify by the `type` / `notification_type` already on the wire.
     *
     * Deliberately conservative: anything unrecognised is [Category.OTHER] and
     * is **never** suppressed. A misclassified package update that gets dropped
     * is a customer missing news about their shipment — far worse than a promo
     * that slips through.
     */
    fun categorize(rawType: String?): Category {
        val type = rawType?.trim()?.lowercase(Locale.US)?.replace('-', '_')
            ?.takeIf { it.isNotEmpty() } ?: return Category.OTHER

        if (PROMO_MARKERS.any { it in type }) return Category.PROMO
        if (PACKAGE_MARKERS.any { it in type }) return Category.PACKAGE
        return Category.OTHER
    }

    /**
     * True when this push must NOT be posted.
     *
     * Every uncertain path returns false. No session, unreadable prefs, an
     * unrecognised type — all deliver the notification. **Absence of a
     * preference is not permission to withhold it.**
     */
    fun shouldSuppress(
        rawType: String?,
        matrix: NotificationPreferenceMatrix? = NotificationAccountPreferences.currentMatrixOrNull(),
    ): Boolean {
        val prefs = matrix ?: return false
        // The master is enforced server-side by token registration. If it is off
        // and a push still arrives, the customer has asked for silence — honour
        // it rather than second-guessing the server.
        if (!prefs.master) return true

        // ⚠️ MASTER ONLY while the per-category rows are hidden, and this early
        // return is the whole safety property — not a performance shortcut.
        //
        // The sub-flags default to FALSE and `NotificationAccountPreferences.load()`
        // COMMITS that all-false matrix to disk on first read. So for any customer
        // who has never opened Notification Settings, `packageMaster` is false on
        // disk — indistinguishable from "I turned this off". Reading it below
        // would suppress every package notification for the entire installed
        // base, which is the exact opposite of the rule stated above.
        //
        // Flipping the flag on therefore requires BOTH the server fields and a
        // way to tell "never answered" from "answered no". See AirdropFeatureFlags.
        //
        // Swift does the same thing at delivery — `wantsDevicePush { master }` —
        // and treats the per-category matrix as inert drafts. This is that parity.
        if (!AirdropFeatureFlags.notificationCategoryPreferences) return false

        // The SECTION switch only. The per-category Push sub-toggles are gone —
        // deleted rather than hidden, because the server has no push-preference
        // column to back them (Kemar 2026-08-01; iOS did the same at 49fee24).
        // What remains maps onto fields Laravel actually stores.
        return when (categorize(rawType)) {
            Category.PACKAGE -> !prefs.packageMaster
            Category.PROMO -> !prefs.promosMaster
            Category.OTHER -> false
        }
    }

    // Substrings, not exact matches: the backend has shipped at least a dozen
    // spellings for the same event (package_status_update, shipment_status,
    // shipment_ready_for_pickup, paid_and_ready_for_pickup …) and new ones
    // appear without notice. Matching on the stem survives that; an exact-match
    // list would silently stop classifying the day a spelling changes and the
    // switch would quietly go back to doing nothing.
    private val PROMO_MARKERS = listOf(
        "promo", "offer", "deal", "discount", "sale", "marketing", "campaign",
    )

    private val PACKAGE_MARKERS = listOf(
        "package", "shipment", "pickup", "pick_up", "delivery", "delivered",
        "tracking", "drop_alert", "dropalert",
    )
}

/**
 * What each channel can actually enforce today, so the UI can stop implying
 * more than it delivers.
 *
 * `PUT /api/v1/user/profile` is validated by `UpdateUserRequest`, whose rules do
 * **not** include `user_email_notification` / `user_sms_notification` /
 * `user_offers_notification`. Laravel's `validated()` drops unknown keys
 * silently, so sending them is a no-op rather than an error — which is exactly
 * why this went unnoticed.
 *
 * Note the fields are NOT missing from Laravel: `CustomerController` validates
 * all three (`:606-608`) and persists them (`:655`). The web portal writes them
 * today. **Only the mobile API's FormRequest omits them**, so the fix is three
 * lines in an existing request class, not a new feature.
 */
enum class NotificationChannelSupport {
    /** Enforced on the device by [PushChannelGate]. */
    CLIENT_ENFORCED,

    /** Server-owned delivery; no client can intercept it. Needs the API. */
    REQUIRES_BACKEND,
}
