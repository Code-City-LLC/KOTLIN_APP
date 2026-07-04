package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Figma "Type Input Field": Cairo SemiBold 16 label (+ red asterisk when
 * required), 50dp min-height box — gray150 fill, 1dp iconShape border,
 * radius 10, 20/13 padding — with Cairo Regular 14 content/placeholder.
 */
@Composable
fun TypeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    required: Boolean = false,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    error: String? = null,
    /** Enables system autofill / password-manager for this field. */
    autofillContentType: ContentType? = null,
)
{
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (label.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = label, style = AirdropType.subtitle1, color = colors.textDarkTitle)
                if (required) {
                    Text(text = "*", style = AirdropType.subtitle1, color = AlertPalette.Error)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .background(colors.gray150, androidx.compose.foundation.shape.RoundedCornerShape(Radius.xs))
                .border(
                    width = 1.dp,
                    color = if (error != null) AlertPalette.Error else colors.iconShape,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xs),
                )
                .padding(horizontal = Spacing.md, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = AirdropType.body2,
                        color = colors.textDescription,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                    cursorBrush = SolidColor(BrandPalette.OrangeMain),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (autofillContentType != null) {
                                Modifier.semantics { contentType = autofillContentType }
                            } else {
                                Modifier
                            }
                        ),
                )
            }
            if (isPassword && onTogglePasswordVisibility != null) {
                Image(
                    painter = painterResource(
                        if (passwordVisible) R.drawable.ic_eye else R.drawable.ic_hide
                    ),
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onTogglePasswordVisibility),
                )
            }
        }
        if (error != null) {
            Text(text = error, style = AirdropType.body3, color = AlertPalette.Error)
        }
    }
}
