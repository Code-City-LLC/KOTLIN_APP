package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette

/**
 * Post-payment confirmation — Swift FigmaPaymentSuccessViewController parity.
 * Reached only after the return deeplink's session verify says PAID.
 */
@Composable
fun PaymentSuccessScreen(
    orderReference: String?,
    formattedAmount: String?,
    onDone: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .testTag("payment-success-root"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .background(AlertPalette.Completed.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(AlertPalette.Completed),
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "You have successfully paid for this",
                style = AirdropType.h5.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("payment-success-headline"),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (formattedAmount != null) {
                    "Your payment of $formattedAmount has been received. " +
                        "A receipt has been emailed to you."
                } else {
                    "Your payment has been received. A receipt has been emailed to you."
                },
                style = AirdropType.body2,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("payment-success-subline"),
            )
            if (!orderReference.isNullOrBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Order reference: $orderReference",
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("payment-success-reference"),
                )
            }
        }
        GradientButton(
            text = "Done",
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .testTag("payment-success-done"),
        )
    }
}
