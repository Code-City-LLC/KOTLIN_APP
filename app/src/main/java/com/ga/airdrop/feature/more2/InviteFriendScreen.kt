package com.ga.airdrop.feature.more2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.frostedGlassSurface
import com.ga.airdrop.core.designsystem.theme.infoBoxBackground
import com.ga.airdrop.core.designsystem.theme.infoBoxBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Send Invitation form.
 *
 * Source of truth: Swift `FigmaInviteFriendViewController` and Figma node
 * 40001432:18800. The visible form has Contacts, First Name, Last Name,
 * Email Address, optional Description, info card, and sticky Save.
 */
@Composable
fun InviteFriendScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    onViewReferralHistory: () -> Unit = {},
    viewModel: InviteFriendViewModel = viewModel(),
    contactsPermissionOverride: Boolean? = null,
    onRequestContactsPermission: (((Boolean) -> Unit) -> Unit)? = null,
    contactsProvider: (() -> List<InviteContact>)? = null,
    onOpenContactsSettings: (() -> Unit)? = null,
    externalInviteHandoff: ((InviteFriendHandoff, InviteContact, String) -> Boolean)? = null,
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inviteHandoff = externalInviteHandoff ?: remember(context) { defaultInviteHandoff(context) }
    var contacts by remember { mutableStateOf<List<InviteContact>>(emptyList()) }
    var showContactsSheet by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf<ContactsPermissionDialog?>(null) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var loadingContacts by remember { mutableStateOf(false) }
    var contactsError by remember { mutableStateOf<String?>(null) }
    var screenActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        screenActive = true
        onDispose { screenActive = false }
    }

    fun handoffIfReady(
        type: InviteFriendHandoff,
        contact: InviteContact,
        message: String,
    ): Boolean = viewModel.requireReferralLink() && inviteHandoff(type, contact, message)

    fun presentContacts() {
        loadingContacts = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { contactsProvider?.invoke() ?: readContacts(context) }
                    .sortedBy { it.displayName.lowercase() }
            }.onSuccess {
                contacts = it
                showContactsSheet = true
            }.onFailure {
                contactsError = it.message ?: "Contacts could not be loaded."
            }
            loadingContacts = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequested = true
        if (granted) {
            presentContacts()
        } else {
            permissionDialog = if (context.findActivity()?.shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_CONTACTS,
                ) == true
            ) ContactsPermissionDialog.Rationale else ContactsPermissionDialog.Settings
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .imePadding()
            .testTag("invite-friend-screen"),
    ) {
        More2InnerHeader(
            title = "Send Invitation",
            onBack = onBack,
            modifier = Modifier.testTag("invite-friend-glass-header"),
            surfaceColor = colors.frostedGlassSurface,
            dividerColor = colors.cardHairline,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ContactsRow(
                onClick = {
                    val granted = contactsPermissionOverride
                        ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                            PackageManager.PERMISSION_GRANTED)
                    if (granted) {
                        presentContacts()
                    } else if (onRequestContactsPermission != null) {
                        onRequestContactsPermission { allowed ->
                            permissionRequested = true
                            if (allowed) presentContacts() else permissionDialog = ContactsPermissionDialog.Settings
                        }
                    } else if (
                        permissionRequested &&
                        context.findActivity()?.shouldShowRequestPermissionRationale(
                            Manifest.permission.READ_CONTACTS,
                        ) != true
                    ) {
                        permissionDialog = ContactsPermissionDialog.Settings
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
            )

            Text(
                text = "View Referral History  \u2192",
                style = AirdropType.body2.copy(textDecoration = TextDecoration.Underline),
                color = colors.orangeDark,
                modifier = Modifier
                    .clickable(onClick = onViewReferralHistory)
                    .testTag("invite-friend-history-link"),
            )

            Text(
                text = "Send Invitation",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
            )

            More2Field(
                label = "First Name",
                value = state.firstName,
                onValueChange = viewModel::onFirstName,
                fieldTag = "invite-friend-first-name-input",
                placeholder = "e.g.Joe",
                required = true,
                asteriskColor = AlertPalette.Error,
            )
            More2Field(
                label = "Last Name",
                value = state.lastName,
                onValueChange = viewModel::onLastName,
                fieldTag = "invite-friend-last-name-input",
                placeholder = "e.g. Doe",
                required = true,
                asteriskColor = AlertPalette.Error,
            )
            More2Field(
                label = "Email Address",
                value = state.email,
                onValueChange = viewModel::onEmail,
                fieldTag = "invite-friend-email-input",
                placeholder = "e.g. ChaseCamp@email.com",
                required = true,
                asteriskColor = AlertPalette.Error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                trailing = {
                    Image(
                        painter = painterResource(R.drawable.ic_chevron),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("invite-friend-email-chevron"),
                    )
                },
            )
            More2Field(
                label = "Description",
                value = state.description,
                onValueChange = viewModel::onDescription,
                fieldTag = "invite-friend-description-input",
                placeholder = "Good friend",
            )

            InfoCard()
        }

        More2BottomBar(
            modifier = Modifier.testTag("invite-friend-footer"),
            verticalPadding = 20.dp,
            surfaceColor = colors.gray150,
            dividerColor = colors.cardHairline,
        ) {
            More2PrimaryButton(
                text = "Save",
                onClick = viewModel::save,
                loading = state.saving,
                modifier = Modifier.testTag("invite-friend-save"),
                height = 50.dp,
                radius = 10.dp,
                gradient = Brush.verticalGradient(
                    listOf(Color(0xFFFF783E), BrandPalette.OrangeMain),
                ),
            )
        }
    }

    if (loadingContacts) More2Loading(Modifier.testTag("invite-friend-contacts-loading"))

    state.selectedContact?.let { contact ->
        ContactOptionsDialog(
            contact = contact,
            referralLink = state.referralLink,
            onDismiss = viewModel::dismissContactOptions,
            onEmail = { viewModel.sendEmailInvitation(contact) },
            onSms = {
                viewModel.dismissContactOptions()
                if (
                    handoffIfReady(
                        InviteFriendHandoff.Sms,
                        contact,
                        referralMessage(state.referralLink, contact.displayName),
                    )
                ) {
                    viewModel.onInvitationShared()
                }
            },
            onWhatsApp = {
                viewModel.dismissContactOptions()
                if (
                    handoffIfReady(
                        InviteFriendHandoff.WhatsApp,
                        contact,
                        referralMessage(state.referralLink, contact.displayName),
                    )
                ) {
                    viewModel.onInvitationShared()
                }
            },
            onShare = {
                viewModel.dismissContactOptions()
                if (
                    handoffIfReady(
                        InviteFriendHandoff.Share,
                        contact,
                        referralMessage(state.referralLink, contact.displayName),
                    )
                ) {
                    viewModel.onInvitationShared()
                }
            },
            onUseForm = { viewModel.useContactInForm(contact) },
        )
    }

    if (showContactsSheet) {
        ContactsSheet(
            contacts = contacts,
            onDismiss = { showContactsSheet = false },
            onPrefill = { contact ->
                showContactsSheet = false
                viewModel.useContactInForm(contact)
            },
            onInvite = { contact ->
                showContactsSheet = false
                viewModel.useContactInForm(contact)
                val message = referralMessage(state.referralLink, contact.displayName)
                when {
                    contact.email.isNotBlank() -> viewModel.sendEmailInvitation(
                        contact = contact,
                        showSuccess = contact.phone.isBlank(),
                        onSuccess = if (contact.phone.isNotBlank()) {
                            {
                                if (
                                    screenActive &&
                                    handoffIfReady(InviteFriendHandoff.WhatsApp, contact, message)
                                ) {
                                    viewModel.onInvitationShared()
                                }
                            }
                        } else null,
                    )
                    contact.phone.isNotBlank() -> if (
                        handoffIfReady(InviteFriendHandoff.WhatsApp, contact, message)
                    ) {
                        viewModel.onInvitationShared()
                    }
                    handoffIfReady(InviteFriendHandoff.Share, contact, message) ->
                        viewModel.onInvitationShared()
                }
            },
        )
    }

    permissionDialog?.let { dialog ->
        InviteRectDialog(
            title = "Contacts Permission",
            message = if (dialog == ContactsPermissionDialog.Rationale) {
                "Contacts access is needed to invite people from your address book."
            } else {
                "Allow Contacts access in Settings to invite people from your address book."
            },
            actions = if (dialog == ContactsPermissionDialog.Rationale) {
                listOf(
                    InviteDialogAction("Continue") {
                        permissionDialog = null
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    InviteDialogAction("Not Now", secondary = true) { permissionDialog = null },
                )
            } else {
                listOf(
                    InviteDialogAction("Open Settings") {
                        permissionDialog = null
                        onOpenContactsSettings?.invoke() ?: openAppSettings(context)
                    },
                    InviteDialogAction("OK", secondary = true) { permissionDialog = null },
                )
            },
            onDismiss = { permissionDialog = null },
        )
    }

    contactsError?.let { message ->
        InviteRectDialog(
            title = "Send Invitation",
            message = message,
            actions = listOf(InviteDialogAction("OK") { contactsError = null }),
            onDismiss = { contactsError = null },
        )
    }

    state.successMessage?.let { message ->
        InviteRectDialog(
            title = "Invitation Sent",
            message = message,
            actions = listOf(InviteDialogAction("OK", onClick = onSaved)),
            onDismiss = onSaved,
        )
    }
    state.validationError?.let { message ->
        InviteRectDialog(
            title = "Send Invitation",
            message = message,
            actions = listOf(InviteDialogAction("OK", onClick = viewModel::dismissValidation)),
            onDismiss = viewModel::dismissValidation,
        )
    }
    state.error?.let { message ->
        InviteRectDialog(
            title = "Send Invitation",
            message = message,
            actions = listOf(InviteDialogAction("OK", onClick = viewModel::dismissError)),
            onDismiss = viewModel::dismissError,
        )
    }
}

enum class InviteFriendHandoff {
    Sms,
    WhatsApp,
    Share,
}

@Composable
private fun ContactsRow(onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(59.dp)
            .testTag("invite-friend-contacts-row")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(
                if (colors.isDark) R.drawable.ic_preferences_dark else R.drawable.ic_preferences
            ),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .testTag("invite-friend-contacts-icon"),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Contacts",
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier
                .size(13.dp)
                .rotate(-90f),
        )
    }
}

@Composable
private fun InfoCard() {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invite-friend-info-card")
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.infoBoxBackground)
            .border(1.dp, colors.infoBoxBorder, RoundedCornerShape(Radius.xs))
            .padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "To qualify for the referral reward, your friend must sign up using the unique link " +
                "provided in your invitation email. Referrals made without this link will not be eligible.",
            style = AirdropType.body2,
            color = colors.textDarkTitle,
            modifier = Modifier
                .weight(1f)
                .testTag("invite-friend-info-body"),
        )
    }
}

@Composable
private fun ContactOptionsDialog(
    contact: InviteContact,
    referralLink: String,
    onDismiss: () -> Unit,
    onEmail: () -> Unit,
    onSms: () -> Unit,
    onWhatsApp: () -> Unit,
    onShare: () -> Unit,
    onUseForm: () -> Unit,
) {
    val actions = buildList {
        if (contact.email.isNotBlank()) add(InviteDialogAction("Send email invitation", onClick = onEmail))
        if (contact.phone.isNotBlank()) {
            add(InviteDialogAction("Send by text message", onClick = onSms))
            add(InviteDialogAction("Send by WhatsApp", onClick = onWhatsApp))
        }
        add(InviteDialogAction("Share referral link", onClick = onShare))
        add(InviteDialogAction("Use in form", onClick = onUseForm))
        add(InviteDialogAction("Cancel", secondary = true, onClick = onDismiss))
    }
    InviteRectDialog(
        title = "Invite ${contact.displayName}",
        message = referralLink,
        actions = actions,
        onDismiss = onDismiss,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ContactsSheet(
    contacts: List<InviteContact>,
    onDismiss: () -> Unit,
    onPrefill: (InviteContact) -> Unit,
    onInvite: (InviteContact) -> Unit,
) {
    val colors = AirdropTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.gray100.copy(alpha = 0.96f),
        scrimColor = Color.Black.copy(alpha = 0.3f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 14.dp, bottom = 28.dp)
                    .size(width = 63.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textDarkTitle),
            )
        },
        modifier = Modifier
            .fillMaxHeight()
            .testTag("invite-friend-contacts-sheet"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Invite your contacts",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
            Text(
                "×",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onDismiss)
                    .testTag("invite-friend-contacts-close")
                    .padding(12.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
        if (contacts.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No contacts available.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .testTag("invite-friend-contacts-list"),
            ) {
                items(contacts, key = { "${it.displayName}|${it.email}|${it.phone}" }) { contact ->
                    ContactSheetRow(contact, onPrefill, onInvite)
                }
            }
        }
    }
}

@Composable
private fun ContactSheetRow(
    contact: InviteContact,
    onPrefill: (InviteContact) -> Unit,
    onInvite: (InviteContact) -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onPrefill(contact) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(colors.iconShape.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contactInitials(contact.displayName),
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
            )
        }
        Text(
            text = contact.displayName,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp)
                .testTag("invite-friend-contact-${contact.displayName}"),
        )
        if (contact.email.isNotBlank() || contact.phone.isNotBlank()) {
            Text(
                text = "Invite",
                style = AirdropType.subtitle2.copy(textDecoration = TextDecoration.Underline),
                color = colors.orangeMain,
                modifier = Modifier
                    .clickable { onInvite(contact) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("invite-friend-contact-invite-${contact.displayName}"),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
}

private data class InviteDialogAction(
    val title: String,
    val secondary: Boolean = false,
    val onClick: () -> Unit,
)

private enum class ContactsPermissionDialog { Rationale, Settings }

@Composable
private fun InviteRectDialog(
    title: String,
    message: String?,
    actions: List<InviteDialogAction>,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(10.dp))
                .padding(20.dp)
                .testTag("invite-friend-rect-dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle)
            if (!message.isNullOrBlank()) {
                Text(text = message, style = AirdropType.body2, color = colors.textDescription)
            }
            actions.forEach { action ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (action.secondary) Modifier.border(1.dp, colors.iconShape, RoundedCornerShape(8.dp))
                            else Modifier.background(
                                Brush.verticalGradient(listOf(Color(0xFFFF783E), BrandPalette.OrangeMain)),
                            )
                        )
                        .clickable(onClick = action.onClick)
                        .testTag("invite-friend-dialog-${action.title.lowercase().replace(" ", "-")}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = action.title,
                        style = AirdropType.subtitle2,
                        color = if (action.secondary) colors.textDarkTitle else Color.White,
                    )
                }
            }
        }
    }
}

private fun readContacts(context: Context): List<InviteContact> {
    val resolver = context.contentResolver
    val contacts = mutableListOf<InviteContact>()
    resolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
        null,
        null,
        "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE ASC",
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getString(0).orEmpty()
            val displayName = cursor.getString(1).orEmpty().ifBlank { "Unnamed Contact" }
            val email = resolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?",
                arrayOf(id),
                null,
            )?.use { values -> if (values.moveToFirst()) values.getString(0).orEmpty() else "" }.orEmpty()
            val phone = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(id),
                null,
            )?.use { values -> if (values.moveToFirst()) values.getString(0).orEmpty() else "" }.orEmpty()
            val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            contacts += InviteContact(
                displayName = displayName,
                firstName = parts.firstOrNull().orEmpty(),
                lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else "",
                email = email,
                phone = phone,
            )
        }
    }
    return contacts
}

private fun contactInitials(name: String): String =
    name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull() }
        .joinToString("").uppercase().ifEmpty { "?" }

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun referralMessage(referralLink: String, contactName: String): String =
    "Hi $contactName! I'd like to invite you to join AirDrop. Download the app and get started " +
        "with fast and reliable shipping services: $referralLink"

private fun defaultInviteHandoff(
    context: Context,
): (InviteFriendHandoff, InviteContact, String) -> Boolean =
    { handoff, contact, message ->
        when (handoff) {
            InviteFriendHandoff.Sms -> openSms(context, contact.phone, message)
            InviteFriendHandoff.WhatsApp -> openWhatsApp(context, contact.phone, message)
            InviteFriendHandoff.Share -> shareReferral(context, message)
        }
    }

private fun openSms(context: Context, phone: String, message: String): Boolean {
    val digits = phone.filter { it.isDigit() || it == '+' }
    val body = Uri.encode(message)
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$digits?body=$body")))
        true
    }.getOrElse {
        shareReferral(context, message)
    }
}

private fun openWhatsApp(context: Context, phone: String, message: String): Boolean {
    val digits = phone.filter { it.isDigit() }
    val body = Uri.encode(message)
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$digits&text=$body")))
        true
    }.getOrElse {
        openSms(context, phone, message)
    }
}

private fun shareReferral(context: Context, message: String): Boolean {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(share, "Share referral link"))
        true
    }.getOrDefault(false)
}
