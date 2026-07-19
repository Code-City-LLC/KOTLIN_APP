package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.data.model.ActiveSession
import com.ga.airdrop.data.repo.UserRepository
import com.ga.airdrop.feature.shop.ShopInnerHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Active Sessions — Swift §D.1 (FigmaActiveSessionsViewController parity):
 * one row per signed-in device from GET /user/sessions, with revoke
 * (DELETE /user/sessions/{id}) behind a confirm. The current device is
 * labeled and not revocable from here (log out does that).
 */
data class ActiveSessionsUiState(
    val loading: Boolean = false,
    val sessions: List<ActiveSession> = emptyList(),
    val error: String? = null,
    val confirmRevoke: ActiveSession? = null,
    val revokingId: String? = null,
)

class ActiveSessionsViewModel(
    private val repo: UserRepository = UserRepository(
        com.ga.airdrop.core.network.ApiClient.service,
    ),
) : ViewModel() {

    private val _state = MutableStateFlow(ActiveSessionsUiState())
    val state: StateFlow<ActiveSessionsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.activeSessions()
                .onSuccess { sessions ->
                    _state.update { it.copy(loading = false, sessions = sessions) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun askRevoke(session: ActiveSession) {
        if (session.isCurrent == true) return
        _state.update { it.copy(confirmRevoke = session) }
    }

    fun dismissRevoke() = _state.update { it.copy(confirmRevoke = null) }

    fun confirmRevoke() {
        val target = _state.value.confirmRevoke ?: return
        val id = target.id ?: return
        _state.update { it.copy(confirmRevoke = null, revokingId = id) }
        viewModelScope.launch {
            repo.revokeSession(id)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            revokingId = null,
                            sessions = s.sessions.filterNot { it.id == id },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(revokingId = null, error = e.message) }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}

/** Human row label: device name, else platform, else the session id. */
internal fun sessionTitle(session: ActiveSession): String =
    session.deviceName?.takeIf(String::isNotBlank)
        ?: session.platform?.takeIf(String::isNotBlank)
        ?: "Session ${session.id.orEmpty()}"

internal fun sessionSubtitle(session: ActiveSession): String = listOfNotNull(
    session.platform?.takeIf { it.isNotBlank() && it != sessionTitle(session) },
    session.lastSeenAt?.takeIf(String::isNotBlank)?.let { "Last seen $it" },
    session.lastSeenIp?.takeIf(String::isNotBlank),
).joinToString(" · ")

@Composable
fun ActiveSessionsScreen(
    onBack: () -> Unit,
    viewModel: ActiveSessionsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150),
    ) {
        ShopInnerHeader(title = "Active Sessions", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Devices signed in to your account. Revoking one signs " +
                    "that device out on its next request.",
                style = AirdropType.body3,
                color = colors.textDescription,
            )
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = BrandPalette.OrangeMain)
                }
            } else if (state.sessions.isEmpty()) {
                Text(
                    text = "No session details are available for your account yet.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .testTag("sessions-empty"),
                )
            }
            state.sessions.forEachIndexed { index, session ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.gray100, RoundedCornerShape(10.dp))
                        .padding(horizontal = 15.dp, vertical = 12.dp)
                        .testTag("session-row-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sessionTitle(session),
                                style = AirdropType.subtitle2,
                                color = colors.textDarkTitle,
                            )
                            if (session.isCurrent == true) {
                                Text(
                                    text = "  This device",
                                    style = AirdropType.body3,
                                    color = BrandPalette.OrangeMain,
                                )
                            }
                        }
                        val subtitle = sessionSubtitle(session)
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = AirdropType.body3,
                                color = colors.textDescription,
                            )
                        }
                    }
                    if (session.isCurrent != true) {
                        TextButton(
                            onClick = { viewModel.askRevoke(session) },
                            enabled = state.revokingId == null,
                            modifier = Modifier.testTag("session-revoke-$index"),
                        ) {
                            Text(
                                text = if (state.revokingId == session.id) "Revoking…" else "Revoke",
                                style = AirdropType.subtitle2,
                                color = BrandPalette.OrangeMain,
                            )
                        }
                    }
                }
            }
        }
    }

    state.confirmRevoke?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRevoke,
            containerColor = colors.gray100,
            title = {
                Text("Sign out this device?", style = AirdropType.title2, color = colors.textDarkTitle)
            },
            text = {
                Text(
                    "${sessionTitle(target)} will be signed out of your Airdrop account.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRevoke) {
                    Text("Sign Out", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRevoke) {
                    Text("Cancel", style = AirdropType.subtitle2, color = colors.textDescription)
                }
            },
        )
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            containerColor = colors.gray100,
            title = { Text("Couldn't update sessions", style = AirdropType.title2, color = colors.textDarkTitle) },
            text = { Text(state.error.orEmpty(), style = AirdropType.body2, color = colors.textDescription) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
                }
            },
        )
    }
}
