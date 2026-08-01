package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.prefs.AvatarStore
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.core.session.captureOwnedRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * More hub state — FigmaMoreViewController behavior: profile + AirCoins on
 * load, avatar via the dedicated GET /user/profile/image endpoint with the
 * /user/profile cached URL as fallback, avatar upload/delete from the card.
 */
data class MoreUiState(
    val name: String = "AirDrop Customer",
    val account: String = "AIR Account",
    val avatar: Bitmap? = null,
    val avatarLoading: Boolean = false,
    val avatarError: String? = null,
)

class MoreViewModel(
    private val repository: MoreHubRepository = MoreRepository(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(MoreUiState())
    val state: StateFlow<MoreUiState> = _state
    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var refreshJob: Job? = null
    private var avatarJob: Job? = null

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionJobs.replaceSession()
                refreshJob = null
                avatarJob = null
                sessionOwner = changed
                _state.value = MoreUiState()
                if (changed != null) refresh()
            }
        }
        // Adopt avatar changes made on OTHER screens. Without this the store
        // would be write-only from here and Profile's upload still would not
        // show up on this tab. Guarded on the session owner so a photo can
        // never survive an account switch into the next user's UI.
        viewModelScope.launch {
            AvatarStore.avatar.collect { shared ->
                val owner = sessionBoundary.capture() ?: return@collect
                if (owner.sessionId != sessionOwner?.sessionId) return@collect
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(avatar = shared) }
                }
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        refreshJob = sessionJobs.launch {
            repository.currentUser().onSuccess { user ->
                val userId = user.id
                if (userId != null && !sessionBoundary.bindAccountId(owner, userId)) return@onSuccess
                sessionBoundary.apply(owner) {
                    _state.update {
                        it.copy(
                            name = user.fullName.ifEmpty { "AirDrop Customer" },
                            account = user.formattedAccount ?: it.account,
                        )
                    }
                    SessionStore.updateForSession(owner) {
                        it.copy(
                            firstName = user.firstName.orEmpty(),
                            tierName = user.tierName.orEmpty(),
                        )
                    }
                }
                if (sessionBoundary.isCurrent(owner)) {
                    loadAvatar(owner, fallbackUrl = user.profileImageUrl)
                }
            }.onFailure {
                // Fallback row stays ("AirDrop Customer" / "AIR Account") — Swift parity.
                if (sessionBoundary.isCurrent(owner)) {
                    loadAvatar(owner, fallbackUrl = null)
                }
            }
            if (!sessionBoundary.isCurrent(owner)) return@launch
            repository.airCoinsBalance().onSuccess { balance ->
                sessionBoundary.apply(owner) {
                    SessionStore.updateForSession(owner) { it.copy(airCoins = balance.toString()) }
                }
            }
        }
    }

    private suspend fun loadAvatar(owner: AuthenticatedSessionOwner, fallbackUrl: String?) {
        // ⚠️ A FAILED READ IS NOT "NO PHOTO" — the same defect GreenForest fixed
        // in ProfileViewModel.refreshAvatar (#211), which lived here too and was
        // missed because only one of the twins was looked at.
        //
        // This was `repository.profileImage().getOrNull()`, which collapses a
        // transient failure and a genuinely absent photo into the same null. The
        // branch below then called AvatarStore.clear(), wiping the customer's
        // photo from EVERY screen reading the store — and the comment sitting on
        // that line claimed it was "authoritative 'there is no photo', not a
        // failed load", which was the exact opposite of what the code did.
        //
        // AvatarStore.publish(null) is deliberately a no-op so a failed load
        // cannot blank the photo everywhere. Calling clear() on a failure walked
        // straight around that guard — a guard I wrote, then bypassed.
        val loaded = repository.profileImage()
        if (!sessionBoundary.isCurrent(owner)) return
        if (loaded.isFailure && fallbackUrl.isNullOrBlank()) {
            // Unknown, not empty. Keep whatever is on screen and in the store.
            return
        }
        val url = loaded.getOrNull()?.resolvedUrl
            ?: fallbackUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (url == null) {
            // The read SUCCEEDED and there is genuinely no photo — the only case
            // where clearing is the truth.
            sessionBoundary.apply(owner) {
                _state.update { it.copy(avatar = null) }
                AvatarStore.clear()
            }
            return
        }
        repository.fetchImage(url)
            .onSuccess { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(avatar = bitmap) }
                    // Every other screen showing the avatar updates from here.
                    AvatarStore.publish(bitmap)
                }
            }
            .onFailure {
                // Downloading the bytes failed. We know a photo EXISTS (we have
                // its url) — so blanking the tile would state something false.
                // Leave the last good image up.
            }
    }

    /** Optimistic upload — POST /user/profile/image, then re-fetch server state. */
    fun uploadAvatar(bitmap: Bitmap) {
        if (avatarJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        if (!sessionBoundary.apply(requestOwner.session) {
                _state.update { it.copy(avatar = bitmap, avatarLoading = true, avatarError = null) }
            }
        ) return
        avatarJob = sessionJobs.launch {
            repository.uploadProfileImage(
                bytes = bitmap.toUploadJpeg(),
                fileName = "profile.jpg",
                mimeType = "image/jpeg",
                expectedSession = requestOwner.provenance,
            )
                .onSuccess {
                    loadAvatar(requestOwner.session, fallbackUrl = null)
                    sessionBoundary.apply(requestOwner.session) {
                        _state.update { it.copy(avatarLoading = false) }
                    }
                }
                .onFailure { e ->
                    sessionBoundary.apply(requestOwner.session) {
                        _state.update {
                            it.copy(avatarLoading = false, avatarError = e.message ?: "Upload failed.")
                        }
                    }
                }
        }
    }

    fun deleteAvatar() {
        if (avatarJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        if (!sessionBoundary.apply(requestOwner.session) {
                _state.update { it.copy(avatarLoading = true, avatarError = null) }
            }
        ) return
        avatarJob = sessionJobs.launch {
            repository.deleteProfileImage(requestOwner.provenance)
                .onSuccess {
                    sessionBoundary.apply(requestOwner.session) {
                        _state.update { it.copy(avatar = null, avatarLoading = false) }
                        AvatarStore.clear()
                    }
                }
                .onFailure { e ->
                    sessionBoundary.apply(requestOwner.session) {
                        _state.update {
                            it.copy(avatarLoading = false, avatarError = e.message ?: "Remove failed.")
                        }
                    }
                }
        }
    }

    fun dismissAvatarError() = _state.update { it.copy(avatarError = null) }
}

/** Swift ProfileAvatarPicker parity: longest side ≤1024, JPEG 85%. */
internal fun Bitmap.toUploadJpeg(maxDimension: Int = 1024, quality: Int = 85): ByteArray {
    val longest = maxOf(width, height)
    val scaled = if (longest > maxDimension) {
        val scale = maxDimension.toFloat() / longest
        Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        this
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
