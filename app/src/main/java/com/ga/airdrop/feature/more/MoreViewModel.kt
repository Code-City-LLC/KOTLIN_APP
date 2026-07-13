package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
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
        val asset = repository.profileImage().getOrNull()
        if (!sessionBoundary.isCurrent(owner)) return
        val url = asset?.resolvedUrl ?: fallbackUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (url == null) {
            sessionBoundary.apply(owner) {
                _state.update { it.copy(avatar = null) }
            }
            return
        }
        repository.fetchImage(url)
            .onSuccess { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(avatar = bitmap) }
                }
            }
            .onFailure {
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(avatar = null) }
                }
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
