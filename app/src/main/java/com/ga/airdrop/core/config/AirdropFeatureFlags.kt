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
     * ## What this now covers — SIX rows, not eight
     *
     * The two per-category **Push** sub-toggles are gone: deleted, not hidden
     * (Kemar 2026-08-01; iOS did the same at `49fee24`). There is **no
     * push-preference column anywhere on the server** — not in the users table
     * (`2024_01_01_000001_create_legacy_core_tables:53-55`), not in `UserResource`
     * (`:70-72`), not in any later migration — so they could never be made real,
     * and a flag guarding them would have guarded a permanent no-op.
     *
     * What remains are the two section masters and the four Email/SMS rows, which
     * DO map onto fields Laravel stores: `user_email_notification`,
     * `user_sms_notification`, `user_offers_notification`.
     *
     * ## ⚠️ What has to be TRUE before this goes on
     *
     *  - `UpdateUserRequest::rules()` must accept those three fields. Today it
     *    omits them and Laravel's `validated()` drops unknown keys silently, so
     *    sending them is a no-op rather than an error. BronzeMountain owns this.
     *  - The category mapping must be confirmed: **three server fields against six
     *    client rows** is not 1:1, and guessing it would write preferences the
     *    customer never expressed.
     *  - The gate must stop suppressing on an unanswered flag. Today a
     *    default-constructed [com.ga.airdrop.core.prefs.NotificationPreferenceMatrix]
     *    is all-false, and `load()` COMMITS that to disk on first read — so a
     *    stored `false` cannot be told apart from a customer who chose "off".
     *    Flipping this flag without fixing that silently drops package
     *    notifications for the whole installed base.
     *  - Preferences should be hydrated from the user response, where the server
     *    already distinguishes `null` (never answered) from `'yes'`/`'no'`. Kotlin
     *    parses all three at `User.kt:163-165` and currently discards them.
     */
    @Volatile
    var notificationCategoryPreferences: Boolean = false

    /**
     * JMD checkout — the NCB / PowerTranz card rail.
     *
     * ## OFF for v27 (3.2.1). Kemar's call, 2026-08-02.
     *
     * One boolean governs **three** things, because any two of them drifting
     * apart is a bug — the same rule [notificationCategoryPreferences] follows:
     *  1. whether JMD is **OFFERED** (CurrencyChoicePopup)
     *  2. whether the rail can **INITIATE** (DeliveryMethodViewModel.onCurrencyChosen
     *     and CheckoutFlowStore.checkoutPaymentRail)
     *  3. what the customer is **TOLD** when they would have picked it
     *
     * ## Why it is off, and why v27 specifically
     *
     * v26 shipped pointing at the retired host, so it cannot reach the API at
     * all — which means NO customer is completing an NCB payment today. The rail
     * is dead in the field by accident, not by decision.
     *
     * **v27 fixes the host, and that RE-ENABLES this rail as a side effect.**
     * So shipping v27 without this flag is not "leaving the NCB risk unchanged";
     * it is turning a payment path back on. BrightHarbor gated the release on
     * exactly that (ORC 88698) and they were right to.
     *
     * ## ⚠️ What has to be TRUE before this goes on
     *
     * Laravel's NCB completion is still under active rejection — ORC 88519,
     * 88520, 88588. Specifically:
     *  - the replay path checks `$checkout->invoice_id`, but `ncb_checkouts` has
     *    no such column, so the claimed idempotency path is DEAD
     *  - `GET_LOCK` only serialises overlapping requests; on provider timeout,
     *    process death, or approval-then-local-failure the lock releases while
     *    status stays `3ds_complete`, so a retry can call `/payment` again —
     *    the unknown-outcome DOUBLE CHARGE
     *  - raw SpiToken is still logged and persisted
     *
     * Flip this **only** after BronzeMountain's contract is green, HazyGorge's
     * independent review passes, and the strict callback recognizer
     * (`isNcbCallback`, ORC 88696) has landed with its negative tests. Turning it
     * on early risks a real customer being charged twice.
     *
     * USD / Stripe is deliberately untouched by this flag.
     */
    @Volatile
    var jmdNcbCheckout: Boolean = true
}
