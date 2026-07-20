package com.ga.airdrop.feature.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.data.model.AirdropUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live Agent Chat — Swift FigmaLiveAgentChatViewController parity.
 *
 * 2026-07-20 (lane #154): the Trengo web widget this screen used to boot
 * was decommissioned vendor-side, leaving the WebView blank. Replaced with
 * the same native Nirvana chat the Swift app ships: per-user identity mint
 * via Airdrop Laravel, then direct app-channel session/send/poll. The
 * send response carries Nirvana's reply inline, so first contact lands
 * even before the poll loop runs.
 */
@Composable
fun LiveAgentChatScreen(onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember { mutableStateListOf<LiveAgentChatRepository.ChatMessage>() }
    val displayedIds = remember { mutableSetOf<String>() }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Connecting…") }
    var sending by remember { mutableStateOf(false) }

    var identity by remember { mutableStateOf<LiveAgentChatRepository.ChatIdentity?>() }
    var user by remember { mutableStateOf<AirdropUser?>(null) }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    fun appendUnique(message: LiveAgentChatRepository.ChatMessage) {
        // The inline send reply and the later polled copy of that same reply
        // carry DIFFERENT server ids ("<msg>:reply" vs the persisted row id),
        // so agent bubbles must dedup on body text as well as id.
        val isDuplicate = message.id in displayedIds ||
            (!message.isCustomer && messages.any { !it.isCustomer && it.body == message.body })
        if (!isDuplicate) {
            displayedIds.add(message.id)
            messages.add(message)
        }
    }

    fun startPolling(id: LiveAgentChatRepository.ChatIdentity, convId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var failures = 0
            repeat(6) {
                delay(1500)
                val polled = runCatching {
                    LiveAgentChatRepository.pollMessages(id, convId)
                }.getOrElse {
                    failures += 1
                    if (failures >= 2) {
                        status = "Waiting for reply…"
                        return@launch
                    }
                    status = "Reconnecting…"
                    return@repeat
                }
                val fresh = polled.filter { !it.isCustomer && it.id !in displayedIds }
                if (fresh.isNotEmpty()) {
                    fresh.forEach(::appendUnique)
                    status = "Agent connected"
                    return@launch
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val minted = LiveAgentChatRepository.mintIdentity()
            identity = minted
            val profile = LiveAgentChatRepository.currentUser()
            user = profile
            val session = LiveAgentChatRepository.startSession(minted, profile)
            conversationId = session.conversationId
            session.messages.forEach(::appendUnique)
            status = if (messages.isEmpty()) "Say hello — Nirvana replies in seconds" else "Agent connected"
        } catch (error: Exception) {
            status = "Chat is unavailable right now — please try again"
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun send() {
        val text = input.trim()
        val id = identity
        if (text.isEmpty() || sending || id == null) return
        input = ""
        sending = true
        appendUnique(
            LiveAgentChatRepository.ChatMessage(
                id = "local-" + System.currentTimeMillis(),
                body = text,
                isCustomer = true,
                senderName = null,
                createdAt = null,
            ),
        )
        scope.launch {
            try {
                // First-ever session may have handed us the external id; the
                // send response always carries the canonical UUID (adopt it,
                // same as Swift FigmaRouteViewController.swift:1871-1875).
                val convId = conversationId ?: id.userId
                val result = LiveAgentChatRepository.sendMessage(id, convId, text, user)
                result.conversationId?.takeIf { it.isNotBlank() }?.let { conversationId = it }
                result.replyMessage?.let {
                    appendUnique(it)
                    status = "Agent connected"
                }
                sending = false
                startPolling(id, conversationId ?: convId)
            } catch (error: Exception) {
                sending = false
                status = "Message not sent"
                appendUnique(
                    LiveAgentChatRepository.ChatMessage(
                        id = "error-" + System.currentTimeMillis(),
                        body = "I couldn't send that yet. Please try again.",
                        isCustomer = false,
                        senderName = null,
                        createdAt = null,
                    ),
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.gray200)) {
        // Inner header — unchanged from the Trengo version: gray100, back
        // chevron 24, SubTitle1 centered title, 1dp iconShape divider.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(40.dp)
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
                    text = "Live Agent Chat",
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
        }

        // Status line — mirrors Swift setStatus labels.
        Text(
            text = status,
            style = AirdropType.body3,
            color = colors.textDescription,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }

        // Input bar — gray100 surface over a hairline divider, send button
        // in the brand orange like Swift's send affordance.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100)
                .imePadding()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                    cursorBrush = SolidColor(colors.orangeMain),
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.gray200, RoundedCornerShape(Radius.m))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                text = "Message Nirvana…",
                                style = AirdropType.body2,
                                color = colors.textPlaceholder,
                            )
                        }
                        inner()
                    },
                )
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .background(
                            if (sending) colors.buttonLoading else colors.orangeMain,
                            RoundedCornerShape(Radius.m),
                        )
                        .clickable(enabled = !sending) { send() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (sending) "…" else "Send",
                        style = AirdropType.button,
                        color = colors.iconWhite,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: LiveAgentChatRepository.ChatMessage) {
    val colors = AirdropTheme.colors
    Column(Modifier.fillMaxWidth()) {
        if (!message.isCustomer) {
            Text(
                text = message.senderName?.takeIf { it.isNotBlank() } ?: "Nirvana",
                style = AirdropType.body3,
                color = colors.textDescription,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isCustomer) Arrangement.End else Arrangement.Start,
        ) {
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        if (message.isCustomer) colors.orangeMain else colors.gray100,
                        RoundedCornerShape(Radius.m),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.body,
                    style = AirdropType.body2,
                    color = if (message.isCustomer) colors.iconWhite else colors.textDarkTitle,
                )
            }
        }
    }
}
