package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.prefs.AvatarStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A failed profile-image read must not wipe the customer's photo everywhere.
 *
 * Found auditing my own work after GreenForest fixed the identical defect in
 * ProfileViewModel (#211). It lived in BOTH twins; only one was looked at.
 *
 * `repository.profileImage().getOrNull()` collapses "the call failed" and "there
 * genuinely is no photo" into the same null. `loadAvatar` then called
 * `AvatarStore.clear()`, blanking the avatar on every screen reading the store —
 * on a transient network failure.
 *
 * The sharp part: `AvatarStore.publish(null)` is a deliberate no-op precisely so
 * a failed load cannot blank the photo elsewhere, and its KDoc says so. Calling
 * `clear()` on the failure path walked straight around that guard. And the
 * comment on the offending line asserted it was "authoritative 'there is no
 * photo', not a failed load" — the exact opposite of what the code did.
 *
 * A guard is only worth the call sites that respect it.
 */
@RunWith(AndroidJUnit4::class)
class MoreAvatarFailedReadTest {

    private fun bitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    @After
    fun tearDown() = AvatarStore.clear()

    @Test
    fun aFailedProfileImageReadMustNotWipeTheSharedAvatar() = runBlocking {
        val existing = bitmap()
        AvatarStore.publish(existing)

        val viewModel = MoreViewModel(
            repository = FakeRepo(profileImageResult = Result.failure(RuntimeException("network"))),
            sessionBoundary = TestBoundary(),
        )
        viewModel.refresh()
        repeat(24) { kotlinx.coroutines.delay(25) }

        assertNotNull(
            "a transient failure must not blank the photo on every screen",
            AvatarStore.avatar.value,
        )
        assertSame(existing, AvatarStore.avatar.value)
    }

    /** The other half: a SUCCESSFUL read with no photo is authoritative. */
    @Test
    fun aSuccessfulReadWithNoPhotoDoesClearIt() = runBlocking {
        AvatarStore.publish(bitmap())

        val viewModel = MoreViewModel(
            repository = FakeRepo(profileImageResult = Result.success(ProfileAsset(url = null, path = null))),
            sessionBoundary = TestBoundary(),
        )
        viewModel.refresh()
        repeat(24) { kotlinx.coroutines.delay(25) }

        assertNull(
            "a successful read that reports no photo IS authoritative",
            AvatarStore.avatar.value,
        )
    }

    private class FakeRepo(
        private val profileImageResult: Result<ProfileAsset>,
    ) : MoreHubRepository {
        override suspend fun airCoinsBalance() = Result.success(0)
        override suspend fun currentUser(expectedSession: AuthTokenStore.RequestProvenance?) =
            Result.success(MoreUser(firstName = "Ada", lastName = "L", profileImageUrl = null))
        override suspend fun updateProfile(
            fields: Map<String, String?>,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.success<String?>(null)
        override suspend fun profileImage() = profileImageResult
        override suspend fun uploadProfileImage(
            bytes: ByteArray,
            fileName: String,
            mimeType: String,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.failure<ProfileAsset>(UnsupportedOperationException())
        override suspend fun deleteProfileImage(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.failure<Unit>(UnsupportedOperationException())
        override suspend fun fetchImage(url: String) =
            Result.failure<ByteArray>(UnsupportedOperationException())
    }

    private class TestBoundary : AuthenticatedSessionBoundary {
        private val owner = AuthenticatedSessionOwner(sessionId = "session-a", accountId = 1)
        private val flow = MutableStateFlow<AuthenticatedSessionOwner?>(owner)
        override val changes: Flow<AuthenticatedSessionOwner?> = flow
        override fun capture() = owner
        override fun isCurrent(owner: AuthenticatedSessionOwner) = owner.sessionId == this.owner.sessionId
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            action(); return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) = action()
        override fun requestOwner(owner: AuthenticatedSessionOwner) =
            AuthenticatedRequestOwner(
                session = owner,
                provenance = AuthTokenStore.RequestProvenance(revision = 1L, sessionId = owner.sessionId),
            )
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = true
    }
}
