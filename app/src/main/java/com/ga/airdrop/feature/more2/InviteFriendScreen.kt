package com.ga.airdrop.feature.more2

import android.content.Intent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
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
 * Invite Friend / Send Invitation — Figma node 40001940:26797, behavior from
 * FigmaInviteFriendViewController: Contacts picker row, first/last/email +
 * description form, info card, Save → POST /refer-friend.
 *
 * Contacts: uses ACTION_PICK on the Email data table, which grants one-shot
 * read access to the picked row — no READ_CONTACTS permission required.
 */
@Composable
fun InviteFriendScreen(
    onBack: () -> Unit,
    viewModel: InviteFriendViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.Contacts.DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val email = cursor.getString(0).orEmpty()
                    val displayName = cursor.getString(1).orEmpty()
                    val parts = displayName.trim().split(Regex("\\s+"))
                    val first = parts.firstOrNull().orEmpty()
                    val last = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
                    viewModel.prefillContact(first, last, email)
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .imePadding()
    ) {
        More2InnerHeader(title = "Send Invitation", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // RN SelectionButton "Contacts" row (59dp, radius 15).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(59.dp)
                    .clip(RoundedCornerShape(Radius.s))
                    .background(colors.gray100)
                    .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                    .clickable {
                        runCatching {
                            contactPicker.launch(
                                Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                ),
                            )
                        }
                    }
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_contact_number),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
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
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier
                        .size(13.dp)
                        .rotate(-90f),
                )
            }

            Text(
                text = "Send Invitation",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
            )

            More2Field(
                label = "First Name",
                value = state.firstName,
                onValueChange = viewModel::onFirstName,
                placeholder = "Chase",
                required = true,
                asteriskColor = BrandPalette.OrangeMain,
            )
            More2Field(
                label = "Last Name",
                value = state.lastName,
                onValueChange = viewModel::onLastName,
                placeholder = "Campbell",
                required = true,
                asteriskColor = BrandPalette.OrangeMain,
            )
            More2Field(
                label = "Email Address",
                value = state.email,
                onValueChange = viewModel::onEmail,
                placeholder = "chase.campbell@gmail.com",
                required = true,
                asteriskColor = BrandPalette.OrangeMain,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            More2Field(
                label = "Description",
                value = state.description,
                onValueChange = viewModel::onDescription,
                placeholder = "Good friend",
            )

            // Info card — Alert Light/Middle OnHold, info icon, Body2 copy.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.s))
                    .background(AlertPalette.Light.OnHold)
                    .border(1.dp, AlertPalette.Middle.OnHold, RoundedCornerShape(Radius.s))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(AlertPalette.OnHold),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "To qualify for the referral reward, your friend must sign up " +
                        "using the unique link provided in your invitation email. " +
                        "Referrals made without this link will not be eligible.",
                    style = AirdropType.body2,
                    color = if (AirdropTheme.colors.isDark) BrandPalette.BlueMain
                    else AirdropTheme.colors.textDarkTitle,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        More2BottomBar {
            More2PrimaryButton(text = "Save", onClick = viewModel::save, loading = state.saving)
        }
    }

    state.successMessage?.let { message ->
        More2Alert(
            title = "Invitation Sent",
            message = message,
            onDismiss = onBack,
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
