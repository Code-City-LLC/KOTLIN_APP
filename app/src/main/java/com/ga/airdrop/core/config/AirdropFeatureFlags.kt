package com.ga.airdrop.core.config

/**
 * Kotlin counterpart of Swift `AirdropFeatureFlags`, for features whose UI is
 * built but whose backing contract does not exist yet.
 *
 * The rule this file exists to enforce: **a control the customer can see must
 * be a control that does something.** Shipping a switch that persists and
 * survives a restart while changing nothing is worse than shipping no switch —
 * it looks more trustworthy than a control that plainly does not work.
 */
object AirdropFeatureFlags {

    /**
     * Per-category notification preferences — the eight Package/Promotions
     * Email/SMS/Push sub-toggles on Notification Settings.
     *
     * ## OFF, and it must stay off. Read this before flipping it.
     *
     * One boolean deliberately governs **three** things, because any two of them
     * drifting apart is a bug:
     *  1. whether the eight rows are VISIBLE (NotificationSettingsScreen)
     *  2. whether [com.ga.airdrop.core.push.PushChannelGate] ENFORCES them
     *  3. what the status card CLAIMS about them
     *
     * Swift states the same rule at `FigmaNotificationSettingsViewController.swift`
     * (~:687): *"visibility and enforcement cannot drift apart — showing the rows
     * without sending, or sending without showing, would each be a bug."*
     *
     * ## ⚠️ What has to be TRUE before this goes on
     *
     * It is **not** "once Laravel accepts the three notification fields". That was
     * written down on both platforms and is wrong. Those three fields are
     * `user_email_notification`, `user_sms_notification`, `user_offers_notification`
     * — email, SMS and offers. They are server-delivered channels; the client is
     * not in that path at all.
     *
     * There is **no push-preference column anywhere on the server** — not in the
     * users table (`2024_01_01_000001_create_legacy_core_tables:53-55`), not in
     * `UserResource` (`:70-72`), not in any later migration. So the two flags this
     * gate actually reads — `packagePush` and `promosPush` — are backed by nothing,
     * and landing the three fields would not change that.
     *
     * Required before flipping, all of it:
     *  - `package_push` / `promos_push` columns + resource fields + validation
     *    server-side. **Not scoped by anyone as of 2026-08-01.**
     *  - The gate must stop suppressing on an unanswered flag. Today a
     *    default-constructed [com.ga.airdrop.core.prefs.NotificationPreferenceMatrix]
     *    is all-false, and `load()` COMMITS that to disk on first read — so a
     *    stored `false` cannot be told apart from a customer who chose "off".
     *    Flipping this flag without fixing that silently drops package
     *    notifications for the whole installed base.
     *  - Email/SMS/offers should be hydrated from the user response, where the
     *    server already distinguishes `null` (never answered) from `'yes'`/`'no'`.
     *    Kotlin already parses all three at `User.kt:163-165` and discards them.
     *
     * If the answer is that push columns are not wanted, these two toggles should
     * be **deleted** rather than hidden — they can never be made real.
     */
    @Volatile
    var notificationCategoryPreferences: Boolean = false
}
