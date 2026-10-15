package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.session.LiveAgentChatConsentStore
import com.ga.airdrop.feature.contacts.LiveAgentChatConsentDialog
import kotlinx.coroutines.delay

@Composable
internal fun BusinessInquirySheet(onDismiss: () -> Unit, onOpenChat: (String) -> Unit,
                                  model: BusinessInquiryViewModel = remember { BusinessInquiryViewModel() }) {
    // The sheet owns this instance. Dismissal cancels its account observer and work;
    // reopening starts a fresh draft/request ID instead of retaining a sent form.
    DisposableEffect(model) { onDispose { model.close() } }
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var opened by remember { mutableStateOf(false) }
    val openChat = {
        state.conversationId?.let { if (!opened) { opened = true; onOpenChat(it) } }
        Unit
    }
    LaunchedEffect(state.conversationId) {
        if (state.stage == BusinessStage.Success) { delay(1_200); openChat() }
    }
    BusinessInquirySheetContent(
        state = state,
        onPrimary = {
            when (state.stage) {
                BusinessStage.Overview -> model.openForm()
                BusinessStage.Form -> {
                    keyboard?.hide()
                    if (state.phase == BusinessPhase.ProfileFailed) model.loadProfile()
                    else model.submit(LiveAgentChatConsentStore.isAccepted(context))
                }
                BusinessStage.Success -> openChat()
            }
        },
        onSecondary = { if (state.stage == BusinessStage.Form) model.back() else onDismiss() },
        onDismiss = { if (state.stage == BusinessStage.Success) openChat() else onDismiss() },
        onName = { model.edit(name = it) },
        onPounds = { model.edit(pounds = it) },
        onCubicFeet = { model.edit(cubicFeet = it) },
    )
    if (state.needsConsent) LiveAgentChatConsentDialog(
        onAccept = {
            model.acceptConsent { LiveAgentChatConsentStore.accept(context) }
        },
        onDecline = model::declineConsent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BusinessInquirySheetContent(
    state: BusinessInquiryState,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
    onName: (String) -> Unit = {},
    onPounds: (String) -> Unit = {},
    onCubicFeet: (String) -> Unit = {},
) {
    val corporate = tierPages.single { it.id == "corporate" }
    val busy = state.busy || state.needsConsent
    val latestBusy = rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !latestBusy.value })
    val scroll = rememberScrollState()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    key(busy) {
        ModalBottomSheet(
            onDismissRequest = { if (!busy) onDismiss() },
            sheetState = sheetState,
            containerColor = corporate.gradientBottom,
            scrimColor = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = null,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy),
            modifier = Modifier.testTag("business-inquiry-sheet"),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(corporate.gradientTop, corporate.gradientBottom),
                        start = Offset(size.width * 0.64f, 0f), end = Offset(size.width * 0.36f, size.height)))
                    drawRect(Color.Black.copy(alpha = 0.30f))
                }
                .imePadding().navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp)
                    .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(2.5.dp)))
                Column(Modifier.weight(1f, fill = false).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.stage == BusinessStage.Overview) Image(painterResource(corporate.iconRes),
                        contentDescription = null, modifier = Modifier.size(64.dp))
                    Text(when (state.stage) {
                        BusinessStage.Overview -> "Business"
                        BusinessStage.Form -> "Business rate request"
                        BusinessStage.Success -> "Business request sent"
                    }, style = AirdropType.h5, color = Color.White, textAlign = TextAlign.Center)
                    when (state.stage) {
                        BusinessStage.Overview -> {
                            BusinessCopy("Tailored rates and dedicated support for consistent high-volume shipping.")
                            BusinessPanel("Airdrop") {
                                BusinessCopy("Ship at least 400 lb per month through Airdrop.")
                                BusinessCopy("For established accounts, AirDrop reviews consistent qualifying volume across the previous three months.")
                            }
                            BusinessCopy("OR")
                            BusinessPanel("SeaDrop") {
                                BusinessCopy("Ship at least 106.7 ft³ per month through SeaDrop—the volume of two loaded 48 × 40 × 48 in pallets.")
                            }
                        }
                        BusinessStage.Form -> {
                            BusinessCopy("Tell us your typical shipping volume. We’ll attach your profile and ask the team to follow up now.")
                            BusinessPanel("Your details") {
                                BusinessField("Business name", state.businessName, onName, busy,
                                    "business-name", KeyboardType.Text,
                                    "Prefilled from your profile; edit it if your trading name differs.")
                                BusinessField("Airdrop — pounds per month", state.airDropPounds, onPounds, busy,
                                    "business-pounds", KeyboardType.Decimal,
                                    "Optional. Enter at least 400 lb if this is your shipping route.")
                                BusinessField("SeaDrop — cubic feet per month", state.seaDropCubicFeet, onCubicFeet, busy,
                                    "business-cubic-feet", KeyboardType.Decimal,
                                    "Optional. 106.7 ft³ equals two loaded 48 × 40 × 48 in pallets.")
                            }
                            BusinessCopy(if (state.phase == BusinessPhase.LoadingProfile) "Loading your profile…"
                                else state.account?.let { "Profile loaded • $it" } ?: "")
                            BusinessCopy("Complete either monthly AirDrop weight or monthly SeaDrop volume. The other field may be left blank.")
                        }
                        BusinessStage.Success -> BusinessCopy(if (state.alreadySubmitted)
                            "Your matching Business request is in the AirDrop support inbox. Opening your conversation…"
                            else "Your high-priority Business request was sent to the AirDrop support inbox. Opening your conversation…")
                    }
                    state.error?.let { Text(it, color = Color.White, style = AirdropType.body2,
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp).testTag("business-error")) }
                }
                TierSheetPrimaryButton(label = when {
                    state.stage == BusinessStage.Overview -> "Contact Us"
                    state.stage == BusinessStage.Success -> "Open Chat"
                    state.phase == BusinessPhase.ProfileFailed -> "Retry"
                    state.phase == BusinessPhase.Failed -> "Try Again"
                    else -> "Send Request"
                }, loading = state.busy, enabled = !busy && state.phase != BusinessPhase.Expired,
                    onClick = onPrimary, tag = "business-primary")
                if (state.stage != BusinessStage.Success) TierSheetQuietButton(
                    label = if (state.stage == BusinessStage.Form) "Back" else "Not Now",
                    loading = false, enabled = !busy, onClick = onSecondary, tag = "business-secondary")
            }
        }
    }
}

@Composable
private fun BusinessCopy(text: String) {
    if (text.isNotEmpty()) Text(text, style = AirdropType.body2, color = Color.White.copy(alpha = 0.85f),
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun BusinessPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = AirdropType.title2, color = Color.White)
        content()
    }
}

@Composable
private fun BusinessField(label: String, value: String, onChange: (String) -> Unit, busy: Boolean,
                          tag: String, keyboard: KeyboardType, helper: String) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = AirdropType.body2, color = Color.White)
        OutlinedTextField(value, onChange, enabled = !busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            shape = RoundedCornerShape(12.dp),
            textStyle = AirdropType.body2,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                unfocusedTextColor = Color.White, cursorColor = Color.White,
                focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.testTag(tag))
        Text(helper, style = AirdropType.body3, color = Color.White.copy(alpha = 0.85f))
    }
}
