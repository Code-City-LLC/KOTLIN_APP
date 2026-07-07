package com.ga.airdrop.feature.more2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Add/Edit Authorized User — Figma node 40001541:45296, behavior from
 * FigmaAddAuthorizedUserViewController: name row, ID-type picker, ID number,
 * email, mobile ("+CC number"), TRN; POST /authorized-users, or edit mode via
 * PUT /authorized-users/{editId} when an existing authorized user is supplied.
 */
@Composable
fun AddAuthorizedUserScreen(
    editId: Int?,
    onBack: () -> Unit,
    viewModel: AddAuthorizedUserViewModel = viewModel(
        factory = addAuthorizedUserFactory(editId),
        key = "addAuthorizedUser_$editId",
    ),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    var idTypeMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("add-authorized-user-root")
            .background(colors.gray100)
            .imePadding()
    ) {
        More2InnerHeader(
            title = if (state.isEditMode) "Edit User" else "Add Authorized User",
            onBack = onBack,
        )

        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("add-authorized-user-scroll")
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // First / Last name side-by-side (RN nameRow, 12dp gap).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("add-authorized-user-name-row"),
                ) {
                    More2Field(
                        label = "First Name",
                        value = state.firstName,
                        onValueChange = viewModel::onFirstName,
                        fieldTag = "add-authorized-user-first-name-input",
                        cardTag = "add-authorized-user-first-name-card",
                        placeholder = "e.g. John",
                        required = true,
                        modifier = Modifier.weight(1f),
                    )
                    More2Field(
                        label = "Last Name",
                        value = state.lastName,
                        onValueChange = viewModel::onLastName,
                        fieldTag = "add-authorized-user-last-name-input",
                        cardTag = "add-authorized-user-last-name-card",
                        placeholder = "e.g. Maat",
                        required = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                More2Field(
                    label = "Identity Type",
                    value = state.idType,
                    onValueChange = {},
                    fieldTag = "add-authorized-user-id-type-input",
                    cardTag = "add-authorized-user-id-type-card",
                    placeholder = "Select identity type",
                    required = true,
                    onClick = { idTypeMenuOpen = true },
                    trailing = {
                        Image(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.textDarkTitle),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )

                More2Field(
                    label = "Identification Number",
                    value = state.idNumber,
                    onValueChange = viewModel::onIdNumber,
                    fieldTag = "add-authorized-user-id-number-input",
                    cardTag = "add-authorized-user-id-number-card",
                    placeholder = "e.g. 194049512",
                    required = true,
                )
                More2Field(
                    label = "Email Address",
                    value = state.email,
                    onValueChange = viewModel::onEmail,
                    fieldTag = "add-authorized-user-email-input",
                    cardTag = "add-authorized-user-email-card",
                    placeholder = "e.g. jane.smith@example.com",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                More2Field(
                    label = "Mobile Number",
                    value = state.mobileNumber,
                    onValueChange = viewModel::onMobileNumber,
                    fieldTag = "add-authorized-user-mobile-input",
                    cardTag = "add-authorized-user-mobile-card",
                    placeholder = "+1 876-5290736",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                More2Field(
                    label = "Tax Registration Number",
                    value = state.trn,
                    onValueChange = viewModel::onTrn,
                    fieldTag = "add-authorized-user-trn-input",
                    cardTag = "add-authorized-user-trn-card",
                    placeholder = "e.g. 194049512",
                    required = true,
                )
            }

            if (state.loadingUser) More2Loading()
        }

        More2BottomBar {
            More2PrimaryButton(
                text = if (state.isEditMode) "Save Changes" else "Add User",
                onClick = viewModel::save,
                loading = state.saving,
                modifier = Modifier.testTag("add-authorized-user-primary"),
            )
        }
    }

    state.validationError?.let { message ->
        More2Alert(
            title = "Validation Error",
            message = message,
            onDismiss = viewModel::dismissValidation,
        )
    }
    state.error?.let { message ->
        More2Alert(title = "Error", message = message, onDismiss = viewModel::dismissError)
    }
    if (idTypeMenuOpen) {
        More2PickerSheet(
            title = "Identity Type",
            options = ID_TYPE_OPTIONS,
            selected = state.idType,
            onSelect = { option ->
                viewModel.onIdType(option)
                idTypeMenuOpen = false
            },
            onDismiss = { idTypeMenuOpen = false },
        )
    }
}

private fun addAuthorizedUserFactory(editId: Int?): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddAuthorizedUserViewModel(editId) as T
    }
