package com.ga.airdrop.core.prefs

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contract for the shared avatar.
 *
 * MoreViewModel and ProfileViewModel can both change the customer's photo, and
 * each used to update only its own state — so changing it on Profile left the
 * More tab showing the old one, and deleting it from More left Profile showing
 * a photo that no longer existed.
 *
 * The two rules that make a shared owner safe rather than merely convenient:
 *
 *  - publish(null) is a NO-OP. A screen that merely failed to load a photo must
 *    not blank it everywhere else; only an authoritative "there is none" does
 *    that, and that is what clear() is for. Collapsing the two would let a
 *    dropped network call erase a photo the customer still has.
 *
 *  - clear() genuinely empties it, because `clearLocalUserSession` calls it on
 *    logout. This store is memory-only and holds a picture of someone's face;
 *    if clear() were lax, the previous customer's photo would still be in
 *    memory at the next login on a shared device.
 *
 * Instrumented rather than a JVM test purely because Bitmap is an Android type.
 */
@RunWith(AndroidJUnit4::class)
class AvatarStoreTest {

    private fun bitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    @After
    fun tearDown() = AvatarStore.clear()

    @Test
    fun aPublishedPhotoIsVisibleToEveryObserver() {
        val bmp = bitmap()

        AvatarStore.publish(bmp)

        assertSame(bmp, AvatarStore.avatar.value)
    }

    @Test
    fun publishingNullDoesNotEraseAnExistingPhoto() {
        val bmp = bitmap()
        AvatarStore.publish(bmp)

        AvatarStore.publish(null)

        assertNotNull(
            "a failed load must not blank the photo on every other screen",
            AvatarStore.avatar.value,
        )
        assertSame(bmp, AvatarStore.avatar.value)
    }

    @Test
    fun clearEmptiesItBecauseLogoutDependsOnThat() {
        AvatarStore.publish(bitmap())

        AvatarStore.clear()

        assertNull(
            "a customer's photo must not survive into the next session",
            AvatarStore.avatar.value,
        )
    }
}
