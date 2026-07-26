package com.ga.airdrop.core.session

/**
 * The canonical local sweep, reachable from the HTTP layer.
 *
 * ⚠️ WHY A SEAM RATHER THAN A DIRECT CALL. `clearLocalUserSession` needs a
 * `Context`; `AuthInterceptor` is a singleton on the OkHttp chain and has none.
 * Handing it one would either leak an Activity or add a static Context to the
 * network layer. `AirdropApp` already owns the application Context and already
 * wires seams this way (see `NotificationBadgeSync.gateway`), so the same idiom
 * is used here rather than inventing a second one.
 *
 * ⚠️ WHY NOT REUSE THE EXISTING snapshotFlow OBSERVER IN AirdropApp. That
 * observer fires on ANY owner change and is what already clears Cart /
 * Saved-for-Later / CheckoutFlow. Hanging the full sweep off it would run the
 * sweep on every transient null the flow ever emits. This seam fires only on the
 * one path Kemar named.
 *
 * BACKGROUND. Kemar 2026-07-26, after being shown that
 * `AirdropAPI.swift:769-770` runs `wipeDeviceSessionArtifacts()` +
 * `wipeAllUserData()` on the same path: **"Android should sweep too."** The
 * previous Kotlin comment claimed the skip matched SwiftHawk. It did not — the
 * platforms had silently diverged, and on a shared handset the next person to
 * sign in inherited the previous customer's AI-disclosure acceptance, biometric
 * app-lock preference, quiet hours, calculator history and shop search history.
 */
object ForcedSignOutSweep {

    /**
     * Installed by `AirdropApp` with the application Context bound.
     *
     * Null in unit tests and in any process that never completed app startup —
     * a forced sign-out there simply clears the token, which is the old
     * behaviour and is safe.
     */
    @Volatile
    var handler: (() -> Unit)? = null

    /**
     * ⚠️ CALL THIS ONLY WHEN `AuthTokenStore.clear(expected)` RETURNED TRUE.
     *
     * That guard is what proves the session being torn down is still the
     * current one. `clearLocalUserSession` internally calls the UNGUARDED
     * `AuthTokenStore.clear()`, so running it after a *superseded* 401 would
     * wipe the data of whichever session replaced it — the precise defect the
     * generation guard exists to prevent.
     */
    fun run() {
        handler?.invoke()
    }
}
