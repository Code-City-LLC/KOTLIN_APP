package com.ga.airdrop.feature.more

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Edit Profile — Figma node 40007189:63763, behavior from
 * FigmaProfileViewController + RN ProfileView: avatar with photo picker
 * (camera/library/remove → POST/DELETE /user/profile/image), the full RN
 * field set, and the pinned gradient Save bar (PUT /user/profile).
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    var showAvatarSheet by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Swift FigmaProfileViewController.swift:120 — gray100 background.
    Box(Modifier.fillMaxSize().background(colors.gray100)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(title = "Edit Profile", onBack = onBack)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(
                    avatar = state.avatar,
                    loading = state.avatarLoading,
                    onClick = { showAvatarSheet = true },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TypeInputField(
                        label = "First Name",
                        value = state.firstName,
                        onValueChange = { v -> viewModel.set { it.copy(firstName = v) } },
                        placeholder = "e.g. John",
                        required = true,
                        modifier = Modifier.weight(1f),
                    )
                    TypeInputField(
                        label = "Last Name",
                        value = state.lastName,
                        onValueChange = { v -> viewModel.set { it.copy(lastName = v) } },
                        placeholder = "e.g. Maat",
                        required = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TypeInputField(
                    label = "Tax Registration Number",
                    value = state.taxId,
                    onValueChange = { v -> viewModel.set { it.copy(taxId = v) } },
                    placeholder = "e.g. 194049512",
                    required = true,
                )
                MoreSelectField(
                    label = "ID Type",
                    value = state.idType,
                    required = true,
                    onClick = { pickerFor = "idType" },
                )
                TypeInputField(
                    label = "ID Number",
                    value = state.idNumber,
                    onValueChange = { v -> viewModel.set { it.copy(idNumber = v) } },
                    placeholder = "e.g. 194049512",
                    required = true,
                )
                MoreSelectField(
                    label = "Date of Birth",
                    value = state.dob,
                    placeholder = "MM/DD/YYYY",
                    required = true,
                    onClick = { showDatePicker = true },
                    trailingIconRes = R.drawable.ic_more_calendar,
                )
                TypeInputField(
                    label = "Email Address",
                    value = state.email,
                    onValueChange = { v -> viewModel.set { it.copy(email = v) } },
                    placeholder = "e.g. kemi2627@yahoo.com",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                TypeInputField(
                    label = "Password",
                    value = state.password,
                    onValueChange = { v -> viewModel.set { it.copy(password = v) } },
                    required = true,
                    isPassword = true,
                    passwordVisible = state.passwordVisible,
                    onTogglePasswordVisibility = viewModel::togglePasswordVisible,
                )
                TypeInputField(
                    label = "Confirm Password",
                    value = state.confirmPassword,
                    onValueChange = { v -> viewModel.set { it.copy(confirmPassword = v) } },
                    required = true,
                    isPassword = true,
                    passwordVisible = state.confirmPasswordVisible,
                    onTogglePasswordVisibility = viewModel::toggleConfirmVisible,
                )
                MoreSelectField(
                    label = "Language",
                    value = state.language,
                    required = true,
                    onClick = { pickerFor = "language" },
                )
                TypeInputField(
                    label = "Address line 1",
                    value = state.addressLine1,
                    onValueChange = { v -> viewModel.set { it.copy(addressLine1 = v) } },
                    placeholder = "e.g. 6175 NW 167th Street",
                    required = true,
                )
                TypeInputField(
                    label = "Address line 2",
                    value = state.addressLine2,
                    onValueChange = { v -> viewModel.set { it.copy(addressLine2 = v) } },
                    placeholder = "e.g. G6",
                )
                MoreSelectField(
                    label = "Country",
                    value = state.country,
                    required = true,
                    onClick = { pickerFor = "country" },
                )
                MoreSelectField(
                    label = "State/Province/Department",
                    value = state.state,
                    required = true,
                    onClick = { pickerFor = "state" },
                )
                TypeInputField(
                    label = "City",
                    value = state.city,
                    onValueChange = { v -> viewModel.set { it.copy(city = v) } },
                    placeholder = "e.g. Miami",
                    required = true,
                )
                TypeInputField(
                    label = "Phone",
                    value = state.phone,
                    onValueChange = { v -> viewModel.set { it.copy(phone = v) } },
                    placeholder = "e.g. 18765290736",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                TypeInputField(
                    label = "Mobile Number",
                    value = state.mobile,
                    onValueChange = { v -> viewModel.set { it.copy(mobile = v) } },
                    placeholder = "e.g. +1876-5290736",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            MoreBottomButtonBar(
                text = "Save",
                loading = state.saving,
                onClick = viewModel::save,
            )
        }
    }

    if (showAvatarSheet) {
        AvatarPickerSheet(
            hasExistingPhoto = state.avatar != null,
            onPicked = viewModel::uploadAvatar,
            onRemove = viewModel::deleteAvatar,
            onDismiss = { showAvatarSheet = false },
        )
    }
    when (pickerFor) {
        "idType" -> MoreOptionSheet(
            title = "ID Type",
            options = viewModel.idTypeOptions,
            selected = state.idType,
            onSelect = { v -> viewModel.set { it.copy(idType = v) } },
            onDismiss = { pickerFor = null },
        )
        "language" -> MoreOptionSheet(
            title = "Language",
            options = viewModel.languageOptions,
            selected = state.language,
            onSelect = { v -> viewModel.set { it.copy(language = v) } },
            onDismiss = { pickerFor = null },
        )
        "country" -> MoreOptionSheet(
            title = "Country",
            options = viewModel.countryOptions,
            selected = state.country,
            onSelect = { v -> viewModel.set { it.copy(country = v) } },
            onDismiss = { pickerFor = null },
        )
        "state" -> MoreOptionSheet(
            title = "State/Province/Department",
            options = viewModel.stateOptions,
            selected = state.state,
            onSelect = { v -> viewModel.set { it.copy(state = v) } },
            onDismiss = { pickerFor = null },
        )
    }
    if (showDatePicker) {
        DobPickerDialog(
            onPicked = { v -> viewModel.set { it.copy(dob = v) } },
            onDismiss = { showDatePicker = false },
        )
    }
    state.alert?.let { (title, message) ->
        MoreAlertDialog(title = title, message = message, onDismiss = viewModel::dismissAlert)
    }
}

/**
 * Figma 40007210:64109 — 107dp avatar circle (gray200 fill, iconShape ring)
 * with the 28dp white edit badge (orange pencil) overlapping bottom-right.
 */
@Composable
private fun ProfileAvatar(
    avatar: android.graphics.Bitmap?,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Box(Modifier.size(107.dp)) {
        Box(
            modifier = Modifier
                .size(107.dp)
                .clip(CircleShape)
                .background(colors.gray200)
                .border(1.dp, colors.iconShape, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar.asImageBitmap(),
                    contentDescription = "Edit profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_profile),
                    contentDescription = "Edit profile photo",
                    colorFilter = ColorFilter.tint(colors.gray500),
                    modifier = Modifier.size(44.dp),
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colors.gray100,
                    strokeWidth = 2.dp,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 79.dp, y = 79.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_more_edit_pen),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DobPickerDialog(
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    val formatter = SimpleDateFormat("MM/dd/yyyy", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    onPicked(formatter.format(Date(millis)))
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}
