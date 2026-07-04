package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Android port of the Swift ProfileAvatarPicker action sheet ("Take Photo /
 * Choose from Library / Remove Photo / Cancel"), backed by the ActivityResult
 * photo picker (no runtime permissions needed) and TakePicturePreview for
 * the camera path. Swift left the More-tab avatar unwired in places; here
 * upload + delete are fully implemented via the callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    hasExistingPhoto: Boolean,
    onPicked: (Bitmap) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        onDismiss()
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }.getOrNull()?.let(onPicked)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        onDismiss()
        bitmap?.let(onPicked)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.gray100,
    ) {
        Column(Modifier.padding(bottom = Spacing.lg)) {
            SheetAction("Take Photo") { cameraLauncher.launch(null) }
            SheetAction("Choose from Library") {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            if (hasExistingPhoto) {
                SheetAction("Remove Photo", destructive = true) {
                    onDismiss()
                    onRemove()
                }
            }
            SheetAction("Cancel", onClick = onDismiss)
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Text(
        text = label,
        style = AirdropType.subtitle1,
        color = if (destructive) AlertPalette.Error else colors.textDarkTitle,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
    )
}
