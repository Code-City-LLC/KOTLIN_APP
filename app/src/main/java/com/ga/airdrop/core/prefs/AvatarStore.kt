package com.ga.airdrop.core.prefs

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The customer's profile photo, shared by every screen that shows it.
 *
 * Two ViewModels can change the avatar — MoreViewModel and ProfileViewModel —
 * and each used to update only its OWN state. So changing your photo on Profile
 * left the More tab showing the old one, and deleting it from More left Profile
 * showing a photo that no longer existed. Whichever screen you were not looking
 * at kept the stale image until it happened to reload.
 *
 * This is the "shared avatar store" that fix was waiting on: a single in-memory
 * owner both sides publish to and observe.
 *
 * MEMORY ONLY, deliberately. The photo is re-fetched from /user/profile-image
 * on load, so persisting it would buy a marginally faster cold paint at the cost
 * of another copy of a customer's face on disk. [ExchangeRateStore] persists
 * because a stale exchange rate is harmless; a stale face is not.
 *
 * ⚠️ Cleared by `clearLocalUserSession`. Without that, the previous customer's
 * photo would still be in memory at the next login on a shared device — the
 * exact class of leak that sweep exists to prevent.
 */
object AvatarStore {

    private val _avatar = MutableStateFlow<Bitmap?>(null)

    /** Current photo, or null when there is none (or none loaded yet). */
    val avatar: StateFlow<Bitmap?> = _avatar

    /**
     * Publish a newly loaded or uploaded photo to every observing screen.
     * Passing null is a no-op — use [clear] to mean "there is no photo", so a
     * screen that simply failed to load one cannot blank it everywhere else.
     */
    fun publish(bitmap: Bitmap?) {
        if (bitmap == null) return
        _avatar.value = bitmap
    }

    /** The customer removed their photo, or the session ended. */
    fun clear() {
        _avatar.value = null
    }
}
