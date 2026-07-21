package com.ga.airdrop.feature.cart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType

/**
 * Post-payment confirmation — pixel port of Figma "My Cart Page" success
 * (node 40008282:24479). A status card (orange check illustration + Title-1 copy)
 * inside a bordered white sheet, with two fulfillment variants:
 *   • PICKUP   → "...your package is now ready for collection at our branch office."
 *   • DELIVERY → "...your package will be sent out for delivery — contact us for details."
 * Reached after a verified-paid Stripe return or a completed NCB (JMD) settle.
 */
@Composable
fun PaymentSuccessScreen(
    orderReference: String?,
    formattedAmount: String?,
    fulfillment: String?, // "delivery" | "pickup" | null
    onContinueShopping: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val isDelivery = fulfillment.equals("delivery", ignoreCase = true)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .statusBarsPadding()
            .testTag("payment-success-root"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Figma places the card at y≈106 (below the 44px status bar).
        Spacer(Modifier.height(62.dp))

        // Success status card — Figma 40008282:24521: white, 1px #e5e5e5 border,
        // 15px radius, drop shadow 0/30/25 @10%, 30px padding, 20px gap.
        Column(
            Modifier
                .padding(horizontal = 30.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(15.dp),
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.10f),
                )
                .background(colors.gray100, RoundedCornerShape(15.dp))
                .border(1.dp, colors.iconShape, RoundedCornerShape(15.dp))
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.img_auth_success),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(354f / 249f),
                contentScale = ContentScale.Fit,
            )

            // Title 1 (Cairo Bold 18/28) base is regular; the amount is bold orange.
            val headline = buildAnnotatedString {
                if (!formattedAmount.isNullOrBlank()) {
                    append("You have successfully paid an amount of ")
                    withStyle(SpanStyle(color = colors.orangeMain, fontWeight = FontWeight.Bold)) {
                        append(formattedAmount)
                    }
                    append(".")
                } else {
                    append("Your payment was successful.")
                }
            }
            Text(
                text = headline,
                style = AirdropType.title1.copy(fontWeight = FontWeight.Normal),
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("payment-success-headline"),
            )

            Text(
                text = if (isDelivery) {
                    "A receipt has been sent to your email. Your package will be sent out " +
                        "for delivery — contact us for more details."
                } else {
                    "A receipt has been sent to your email and your package is now ready " +
                        "for collection at our branch office."
                },
                style = AirdropType.title1.copy(fontWeight = FontWeight.Normal),
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("payment-success-subline"),
            )

            if (!orderReference.isNullOrBlank()) {
                Text(
                    text = orderReference,
                    style = AirdropType.subtitle2,
                    color = colors.textDescription,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("payment-success-reference"),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Figma 40008282:24526 — outlined button: white fill, orange border (10px
        // radius), DARK label (#292929). "Label" placeholder = Continue Shopping.
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.gray100, RoundedCornerShape(10.dp))
                .border(1.dp, colors.orangeMain, RoundedCornerShape(10.dp))
                .clickable(onClick = onContinueShopping)
                .testTag("payment-success-done"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Continue Shopping",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
        }
    }
}
