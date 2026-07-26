package com.ga.airdrop.feature.more2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * What a legal screen shows when its document cannot be fetched.
 *
 * Terms & Conditions used to fall back to a hardcoded copy of another
 * product's legal text and render it as AirDrop's own. Saying "we couldn't
 * load this" is worse-looking and enormously better: a customer can tell the
 * difference between a failure and a document, which is the whole point.
 */
@Composable
fun LegalLoadFailed(message: String, onRetry: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl)
            .testTag("legal-load-failed"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = message,
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
        )
        OutlineButton(
            text = "Try Again",
            onClick = onRetry,
            modifier = Modifier.testTag("legal-retry"),
        )
    }
}
