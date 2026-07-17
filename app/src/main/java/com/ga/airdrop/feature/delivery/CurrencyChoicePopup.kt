package com.ga.airdrop.feature.delivery

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType

/**
 * "Choose Currency" decision popup — copy from Swift
 * FigmaCheckoutCurrencyPopupViewController (FigmaCartViewController.swift:2773):
 * title "How do you want to pay?", options "USD — United States Dollar" (🇺🇸)
 * and "JMD — Jamaican Dollar" (🇯🇲). Swift renders a glass card; Kotlin uses
 * the app's standard AlertDialog treatment (CartScreen parity) with a Cancel
 * action.
 */
@Composable
fun CurrencyChoicePopup(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = {
            Text(
                text = "How do you want to pay?",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
            )
        },
        text = {
            // Swift stackView spacing 14 between the two option cards.
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CurrencyOptionRow(
                    flag = "🇺🇸",
                    code = "USD",
                    subtitle = "United States Dollar",
                    testTag = "delivery-currency-usd",
                    onClick = { onPick("USD") },
                )
                CurrencyOptionRow(
                    flag = "🇯🇲",
                    code = "JMD",
                    subtitle = "Jamaican Dollar",
                    testTag = "delivery-currency-jmd",
                    onClick = { onPick("JMD") },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", style = AirdropType.button, color = colors.textDarkTitle)
            }
        },
    )
}

@Composable
private fun CurrencyOptionRow(
    flag: String,
    code: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = flag, style = AirdropType.title2.copy(fontSize = 22.sp))
        Column {
            Text(
                text = code,
                style = AirdropType.subtitle1,
                color = AirdropTheme.colors.orangeMain,
            )
            Text(
                text = subtitle,
                style = AirdropType.body3,
                color = colors.textDescription,
            )
        }
    }
}
