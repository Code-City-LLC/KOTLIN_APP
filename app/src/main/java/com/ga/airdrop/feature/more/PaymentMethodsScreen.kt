package com.ga.airdrop.feature.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

/**
 * Payment Methods — FigmaPaymentMethodsViewController: informational status
 * card ("No saved payment methods") plus a "Go to Checkout" row that jumps
 * to the cart, where cards are actually added.
 */
@Composable
fun PaymentMethodsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val colors = AirdropTheme.colors
    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(title = "Payment Methods", onBack = onBack)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.s))
                        .background(colors.gray100)
                        .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_more_payment_methods),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "No saved payment methods",
                            style = AirdropType.subtitle1,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = "Cards and other payment methods are added during checkout.",
                            style = AirdropType.body3,
                            color = colors.textDescription,
                        )
                    }
                }
                MoreRowCard(
                    iconRes = R.drawable.ic_cart,
                    title = "Go to Checkout",
                    tint = colors.iconSelected,
                    onClick = { onNavigate(Routes.CART) },
                )
            }
        }
    }
}
