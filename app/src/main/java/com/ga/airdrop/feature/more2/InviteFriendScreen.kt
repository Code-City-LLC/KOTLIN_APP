package com.ga.airdrop.feature.more2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Send Invitation form.
 *
 * Source of truth: Swift `FigmaInviteFriendViewController` and Figma node
 * 40001432:18800. The visible form has Contacts, First Name, Last Name,
 * Email Address, info card, and sticky Save. Optional description is retained
 * only for Swift's contact-email action, not as a visible field.
 */
@Composable
fun InviteFriendScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: InviteFriendViewModel = viewModel(),
    onContactPickerIntent: ((Intent) -> Unit)? = null,
    externalInviteHandoff: ((InviteFriendHandoff, InviteContact, String) -> Boolean)? = null,
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val inviteHandoff = externalInviteHandoff ?: remember(context) { defaultInviteHandoff(context) }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPickedContact(context, uri)?.let { contact ->
            viewModel.onContactPicked(
                displayName = contact.displayName,
                email = contact.email,
                phone = contact.phone,
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .imePadding()
            .testTag("invite-friend-screen"),
    ) {
        More2InnerHeader(title = "Send Invitation", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ContactsRow(
                onClick = {
                    val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                    if (onContactPickerIntent != null) {
                        onContactPickerIntent(intent)
                    } else {
                        runCatching { contactPicker.launch(intent) }
                    }
                },
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

            InfoCard()
        }

        More2BottomBar {
            More2PrimaryButton(
                text = "Save",
                onClick = viewModel::save,
                loading = state.saving,
                modifier = Modifier.testTag("invite-friend-save"),
            )
        }
    }

    state.selectedContact?.let { contact ->
        ContactOptionsDialog(
            contact = contact,
            referralLink = state.referralLink,
            onDismiss = viewModel::dismissContactOptions,
            onEmail = { viewModel.sendEmailInvitation(contact) },
            onSms = {
                viewModel.dismissContactOptions()
                if (
                    inviteHandoff(
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
                    inviteHandoff(
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
                    inviteHandoff(
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

    state.successMessage?.let { message ->
        More2Alert(
            title = "Invitation Sent",
            message = message,
            onDismiss = onSaved,
        )
    }
    state.validationError?.let { message ->
        More2Alert(
            title = "Send Invitation",
            message = message,
            onDismiss = viewModel::dismissValidation,
        )
    }
    state.error?.let { message ->
        More2Alert(
            title = "Send Invitation",
            message = message,
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
    val background = if (colors.isDark) Color(0x1A0993D1) else AlertPalette.Light.OnHold
    val border = if (colors.isDark) Color(0x6B0992D1) else AlertPalette.Middle.OnHold
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invite-friend-info-card")
            .clip(RoundedCornerShape(Radius.xs))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(Radius.xs))
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
    val colors = AirdropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = {
            Text(
                text = "Invite ${contact.displayName}",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (contact.email.isNotBlank()) {
                    ContactOption("Send email invitation", onEmail)
                }
                if (contact.phone.isNotBlank()) {
                    ContactOption("Send by text message", onSms)
                    ContactOption("Send by WhatsApp", onWhatsApp)
                }
                ContactOption("Share referral link", onShare)
                ContactOption("Use in form", onUseForm)
                Text(
                    text = referralLink,
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    modifier = Modifier.testTag("invite-friend-referral-link"),
                )
            }
        },
        confirmButton = {
            Text(
                text = "Cancel",
                style = AirdropType.subtitle2,
                color = colors.textDescription,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(Spacing.sm),
            )
        },
    )
}

@Composable
private fun ContactOption(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = AirdropType.subtitle1,
        color = BrandPalette.OrangeMain,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("invite-friend-option-${text.lowercase().replace(" ", "-")}"),
    )
}

private fun readPickedContact(context: Context, uri: Uri): InviteContact? {
    val resolver = context.contentResolver
    var id = ""
    var displayName = ""
    resolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            id = cursor.getString(0).orEmpty()
            displayName = cursor.getString(1).orEmpty()
        }
    }
    if (id.isBlank()) return null

    val email = resolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?",
        arrayOf(id),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty()

    val phone = resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
        arrayOf(id),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty()

    val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return InviteContact(
        displayName = displayName.ifBlank { "this contact" },
        firstName = parts.firstOrNull().orEmpty(),
        lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else "",
        email = email,
        phone = phone,
    )
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
