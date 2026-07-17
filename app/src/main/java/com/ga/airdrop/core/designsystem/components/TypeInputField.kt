package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
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

/**
 * Shared Swift makeField parity: subtitle2 label (+ orange asterisk), 48dp
 * gray100 card, radius 12, 1dp iconShape border, body1 content.
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
    /** Optional stable tags for parity tests: "$prefix-card", "$prefix-required". */
    testTagPrefix: String? = null,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = label, style = AirdropType.subtitle2, color = colors.textDarkTitle)
                if (required) {
                    Text(
                        text = "*",
                        style = AirdropType.subtitle2,
                        color = AirdropTheme.colors.orangeMain,
                        modifier = testTagPrefix?.let { Modifier.testTag("$it-required") }
                            ?: Modifier,
                    )
                }
            }
        }
        val cardModifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.gray100, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (error != null) AlertPalette.Error else colors.iconShape,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp)
            .then(testTagPrefix?.let { Modifier.testTag("$it-card") } ?: Modifier)
        Row(
            modifier = cardModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = AirdropType.body1,
                        color = colors.textDescription,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = AirdropType.body1.copy(color = colors.textDarkTitle),
                    cursorBrush = SolidColor(AirdropTheme.colors.orangeMain),
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
                    colorFilter = ColorFilter.tint(colors.gray500),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onTogglePasswordVisibility)
                        .then(
                            testTagPrefix?.let { Modifier.testTag("$it-password-toggle") }
                                ?: Modifier,
                        ),
                )
            }
        }
        if (error != null) {
            Text(text = error, style = AirdropType.body3, color = AlertPalette.Error)
        }
    }
}
