package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.SessionStore
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
    private val repository: MoreRepository = MoreRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(MoreUiState())
    val state: StateFlow<MoreUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.currentUser().onSuccess { user ->
                _state.update {
                    it.copy(
                        name = user.fullName.ifEmpty { "AirDrop Customer" },
                        account = user.formattedAccount ?: it.account,
                    )
                }
                SessionStore.update {
                    it.copy(
                        firstName = user.firstName.orEmpty(),
                        tierName = user.tierName.orEmpty(),
                    )
                }
                SessionStore.updateIdentity(
                    accountNumber = user.accountNumber,
                    userId = user.id,
                )
                loadAvatar(fallbackUrl = user.profileImageUrl)
            }.onFailure {
                // Fallback row stays ("AirDrop Customer" / "AIR Account") — Swift parity.
                loadAvatar(fallbackUrl = null)
            }
            repository.airCoinsBalance().onSuccess { balance ->
                SessionStore.update { it.copy(airCoins = balance.toString()) }
            }
        }
    }

    private suspend fun loadAvatar(fallbackUrl: String?) {
        val asset = repository.profileImage().getOrNull()
        val url = asset?.resolvedUrl ?: fallbackUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (url == null) {
            _state.update { it.copy(avatar = null) }
            return
        }
        repository.fetchImage(url)
            .onSuccess { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                _state.update { it.copy(avatar = bitmap) }
            }
            .onFailure { _state.update { it.copy(avatar = null) } }
    }

    /** Optimistic upload — POST /user/profile/image, then re-fetch server state. */
    fun uploadAvatar(bitmap: Bitmap) {
        _state.update { it.copy(avatar = bitmap, avatarLoading = true, avatarError = null) }
        viewModelScope.launch {
            repository.uploadProfileImage(
                bytes = bitmap.toUploadJpeg(),
                fileName = "profile.jpg",
                mimeType = "image/jpeg",
            )
                .onSuccess {
                    loadAvatar(fallbackUrl = null)
                    _state.update { it.copy(avatarLoading = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(avatarLoading = false, avatarError = e.message ?: "Upload failed.")
                    }
                }
        }
    }

    fun deleteAvatar() {
        _state.update { it.copy(avatarLoading = true, avatarError = null) }
        viewModelScope.launch {
            repository.deleteProfileImage()
                .onSuccess { _state.update { it.copy(avatar = null, avatarLoading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(avatarLoading = false, avatarError = e.message ?: "Remove failed.")
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
