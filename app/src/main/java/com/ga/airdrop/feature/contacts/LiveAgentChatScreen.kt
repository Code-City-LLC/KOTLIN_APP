package com.ga.airdrop.feature.contacts

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette

private const val AI_CONSENT_KEY = "AirdropAutoPilotAIConsent.v1"
private const val AI_CONSENT_PREFS = "airdrop_live_chat"

@Composable
fun LiveAgentChatScreen(onBack: () -> Unit) {
    val viewModel: LiveAgentChatViewModel = viewModel()
    LiveAgentChatRoute(onBack = onBack, viewModel = viewModel)
}

@Composable
internal fun LiveAgentChatRoute(
    onBack: () -> Unit,
    viewModel: LiveAgentChatViewModel,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(AI_CONSENT_PREFS, Context.MODE_PRIVATE)
    }
    var consentAccepted by remember {
        mutableStateOf(prefs.getBoolean(AI_CONSENT_KEY, false))
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(consentAccepted) {
        if (consentAccepted) {
            viewModel.start()
        }
    }

    LiveAgentChatContent(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
    )

    if (!consentAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Chat with Nirvana", style = AirdropType.title1) },
            text = {
                Text(
                    "Nirvana is an AI agent handling this conversation. AI may make mistakes — please verify important information.\n\n" +
                        "Your name, account number, and what you type here will be sent to the agent service to power the conversation. You can ask for a human agent at any time.",
                    style = AirdropType.body2,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit().putBoolean(AI_CONSENT_KEY, true).apply()
                        consentAccepted = true
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("Not Now")
                }
            },
        )
    }
}

@Composable
internal fun LiveAgentChatContent(
    state: LiveAgentChatUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.gray100)
            .testTag("live-chat-screen"),
    ) {
        LiveChatBackdrop()

        if (state.messages.isEmpty()) {
            LiveChatEmptyState(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 186.dp, start = 20.dp, end = 20.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 106.dp, bottom = 141.dp),
                contentPadding = PaddingValues(top = 300.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(state.messages, key = { it.id }) { turn ->
                    LiveChatBubble(
                        turn = turn,
                        customerName = state.customerDisplayName,
                        agentName = state.agentDisplayName,
                    )
                }
            }
        }

        LiveChatHeader(
            onBack = onBack,
            menuExpanded = menuExpanded,
            onMenuExpandedChange = { menuExpanded = it },
            onAbout = { aboutOpen = true },
            onHistory = { historyOpen = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        LiveChatInputBar(
            input = state.input,
            loading = state.loading,
            sending = state.sending,
            error = state.error,
            onInputChange = onInputChange,
            onSend = onSend,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("About Nirvana", style = AirdropType.title1) },
            text = {
                Text(
                    "Nirvana is the AI agent that answers here. The assistant may occasionally produce inaccurate or incomplete answers — please verify any package, shipping, or billing detail with a human agent before acting on it.\n\n" +
                        "Your name, account number, and message contents are sent to the agent service to power this conversation. No vendor secret is stored on your device. Live human handoff is available by asking Nirvana for an agent.",
                    style = AirdropType.body2,
                )
            },
            confirmButton = {
                TextButton(onClick = { aboutOpen = false }) {
                    Text("OK")
                }
            },
        )
    }

    if (historyOpen) {
        AlertDialog(
            onDismissRequest = { historyOpen = false },
            title = { Text("Chat History", style = AirdropType.title1) },
            text = {
                Text(
                    if (state.historyCount == 0) {
                        "This new session does not have previous messages to show."
                    } else {
                        "${state.historyCount} message${if (state.historyCount == 1) "" else "s"} in this session."
                    },
                    style = AirdropType.body2,
                )
            },
            confirmButton = {
                TextButton(onClick = { historyOpen = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun LiveChatBackdrop() {
    val colors = AirdropTheme.colors
    Image(
        painter = painterResource(R.drawable.img_live_chat_bg_vector_2),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        alpha = if (colors.isDark) 0.32f else 0.42f,
        modifier = Modifier
            .size(width = 760.dp, height = 840.dp)
            .offset(x = (-320).dp, y = (-210).dp)
            .rotate(-139.42f),
    )
    Image(
        painter = painterResource(R.drawable.img_live_chat_bg_vector_1),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        alpha = if (colors.isDark) 0.22f else 0.38f,
        modifier = Modifier
            .size(width = 680.dp, height = 870.dp)
            .offset(x = (-390).dp, y = 60.dp)
            .rotate(68.09f),
    )
}

@Composable
private fun LiveChatHeader(
    onBack: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.glassOverlay70)
            .border(1.dp, colors.divider)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_small_arrow_down),
                    contentDescription = "Back",
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(90f),
                )
            }
            Text(
                "Live Chat",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
            )
            Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onMenuExpandedChange(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_live_chat_more_square),
                        contentDescription = "About Nirvana and chat history",
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier.size(24.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("About Nirvana", style = AirdropType.body2) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onAbout()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Chat History", style = AirdropType.body2) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onHistory()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChatEmptyState(modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(61.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.img_live_chat_ai_chat),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 154.dp, height = 199.dp),
        )
        Text(
            "How may I help\nyou today!",
            style = AirdropType.h4,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LiveChatInputBar(
    input: String,
    loading: Boolean,
    sending: Boolean,
    error: String?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val sendEnabled = input.trim().isNotEmpty() && !loading && !sending

    Column(
        modifier
            .fillMaxWidth()
            .height(141.dp)
            .background(colors.glassOverlay70)
            .border(1.dp, colors.divider)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 67.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(colors.gray100)
                    .border(
                        width = 1.dp,
                        color = if (colors.isDark) Color(0xFF4D4D4D) else Color(0xFFE5E5E5),
                        shape = RoundedCornerShape(50.dp),
                    )
                    .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 24.dp, max = 88.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        textStyle = AirdropType.subtitle2.copy(color = colors.textDarkTitle),
                        cursorBrush = SolidColor(BrandPalette.OrangeMain),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading && !sending,
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    "Type your question here...",
                                    style = AirdropType.subtitle2,
                                    color = colors.textPlaceholder,
                                )
                            }
                            inner()
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(47.dp)
                        .clip(CircleShape)
                        .background(colors.gray100)
                        .border(
                            width = 1.dp,
                            color = if (colors.isDark) Color(0xFF8C2F0C) else Color(0xFFF1CBBC),
                            shape = CircleShape,
                        )
                        .clickable(enabled = sendEnabled, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_live_chat_send),
                        contentDescription = "Send message",
                        colorFilter = ColorFilter.tint(colors.textDarkTitle.copy(alpha = if (sendEnabled) 1f else 0.45f)),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (error != null) {
                Text(
                    error,
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 135.dp, height = 5.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (colors.isDark) Color(0xFFF2F2F2) else Color(0xFF292929)),
            )
        }
    }
}

@Composable
private fun LiveChatBubble(
    turn: LiveAgentChatTurn,
    customerName: String,
    agentName: String,
) {
    val isCustomer = turn.role == LiveChatRole.Customer
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCustomer) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isCustomer) {
            CustomerBubbleHeader(customerName)
        } else {
            AssistantBubbleHeader(turn.senderName?.takeIf { it.isNotBlank() } ?: agentName)
        }
        Box(
            modifier = Modifier
                .widthIn(max = if (isCustomer) 222.dp else 236.dp)
                .clip(chatBubbleShape(isCustomer))
                .background(chatBubbleColor(isCustomer))
                .then(
                    if (AirdropTheme.colors.isDark) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, Color(0xFFE5E5E5), chatBubbleShape(isCustomer))
                    },
                )
                .padding(20.dp),
        ) {
            Text(
                text = noteAwareText(turn.body),
                style = AirdropType.body2,
                color = AirdropTheme.colors.textDarkTitle,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun CustomerBubbleHeader(name: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = AirdropType.body1, color = AirdropTheme.colors.textDarkTitle)
        Box(
            modifier = Modifier
                .size(31.dp)
                .clip(CircleShape)
                .background(Color(0xFF6B5F5B)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                style = AirdropType.subtitle3,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun AssistantBubbleHeader(name: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.img_live_chat_assistant_badge),
            contentDescription = null,
            modifier = Modifier.size(31.dp),
        )
        Text(name, style = AirdropType.body1, color = AirdropTheme.colors.textDarkTitle)
    }
}

private fun chatBubbleShape(customer: Boolean): RoundedCornerShape =
    if (customer) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

@Composable
private fun chatBubbleColor(customer: Boolean): Color {
    val dark = AirdropTheme.colors.isDark
    return when {
        customer && dark -> Color(0xFF243B45)
        customer -> Color(0xFFEDF9FF)
        dark -> Color(0xFF3A2A22)
        else -> Color(0xFFFAF6F5)
    }
}

private fun noteAwareText(body: String) = buildAnnotatedString {
    if (body.startsWith("AI Note:", ignoreCase = true)) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append("AI Note:")
        }
        append(body.removePrefix("AI Note:"))
    } else {
        append(body)
    }
}
