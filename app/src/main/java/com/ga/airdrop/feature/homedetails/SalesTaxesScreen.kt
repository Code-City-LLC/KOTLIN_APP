package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader

/**
 * Sales Taxes (Ship Tax) — Figma 40001531:11704, behavior/copy from
 * FigmaSalesTaxesViewController (RN SalesTaxesView port): intro + sub
 * paragraph, "How to Activate…" card with 6 numbered step cards (icon +
 * Title2 + Body2 with inline bolds), closing blue "That's it! 🎉" card.
 */

private data class TaxStep(
    val iconRes: Int,
    val darkIconRes: Int,
    val iconTag: String,
    val title: String,
    val body: List<Pair<String, Boolean>>, // text to bold-flag runs
)

internal object SalesTaxesTags {
    const val ROOT = "sales-taxes-root"
    const val LOGIN_ICON = "sales-taxes-login-icon"
    const val MARKETPLACE_ICON = "sales-taxes-marketplace-icon"
    const val BUILDING_ICON = "sales-taxes-building-icon"
    const val FILE_SETTINGS_ICON = "sales-taxes-file-settings-icon"
    const val FOLDER_CURVE_ICON = "sales-taxes-folder-curve-icon"
    const val SHOPPING_BAG_ICON = "sales-taxes-shopping-bag-icon"
}

private val taxSteps = listOf(
    TaxStep(
        iconRes = R.drawable.ic_login,
        darkIconRes = R.drawable.ic_login_sales_tax_dark,
        iconTag = SalesTaxesTags.LOGIN_ICON,
        title = "Sign in to your store account",
        body = listOf(
            "Log in to the U.S. online store where you plan to make purchases." to false,
        ),
    ),
    TaxStep(
        iconRes = R.drawable.ic_marketplace,
        darkIconRes = R.drawable.ic_marketplace_sales_tax_dark,
        iconTag = SalesTaxesTags.MARKETPLACE_ICON,
        title = "Navigate to the marketplace’s tax-exemption section",
        body = listOf(
            "Look for options such as “" to false,
            "Tax Exemption Program" to true,
            "”, “" to false,
            "Buyer Exemption Program”" to true,
            ", “" to false,
            "Tax Information" to true,
            "”, or similar. You’ll usually find this under " to false,
            "Account Settings" to true,
            " or " to false,
            "Customer Service" to true,
            "." to false,
        ),
    ),
    TaxStep(
        iconRes = R.drawable.ic_building,
        darkIconRes = R.drawable.ic_building_sales_tax_dark,
        iconTag = SalesTaxesTags.BUILDING_ICON,
        title = "Select the state and entity type",
        body = listOf(
            ("When prompted, choose Florida as your state and select the entity type " +
                "that matches your certificate — typically “") to false,
            "Resale" to true,
            "”." to false,
        ),
    ),
    TaxStep(
        iconRes = R.drawable.ic_file_settings,
        darkIconRes = R.drawable.ic_file_settings_sales_tax_dark,
        iconTag = SalesTaxesTags.FILE_SETTINGS_ICON,
        title = "Upload your certificate",
        body = listOf(
            "Upload a clear copy of the " to false,
            "Florida Annual Resale Certificate (PDF or image)" to true,
            (" provided by AirDrop. Ensure your business information matches exactly " +
                "what appears on the certificate.") to false,
        ),
    ),
    TaxStep(
        iconRes = R.drawable.ic_folder_curve,
        darkIconRes = R.drawable.ic_folder_curve_sales_tax_dark,
        iconTag = SalesTaxesTags.FOLDER_CURVE_ICON,
        title = "Submit and wait for verification",
        body = listOf(
            "The marketplace will review your submission. For example, " to false,
            "eBay" to true,
            " may take up to " to false,
            "5 business days" to true,
            " to verify and will send a confirmation email once approved." to false,
        ),
    ),
    TaxStep(
        iconRes = R.drawable.ic_shopping_bag,
        darkIconRes = R.drawable.ic_shopping_bag_sales_tax_dark,
        iconTag = SalesTaxesTags.SHOPPING_BAG_ICON,
        title = "Start shopping tax-free",
        body = listOf(
            "Once approved, " to false,
            "sales tax will automatically be removed" to true,
            " at checkout for eligible purchases on that marketplace." to false,
        ),
    ),
)

private val outroBullets = listOf(
    "Works across major U.S. platforms",
    "Fast verification and setup assistance",
    "Unlimited tax-free purchases",
)

@Composable
fun SalesTaxesScreen(onBack: () -> Unit) {
    val colors = AirdropTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .testTag(SalesTaxesTags.ROOT)
    ) {
        HomeDetailsHeader(title = "Shop Tax-Free with AirDrop Limited", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Enjoy tax-free shopping on Amazon, eBay, Walmart, Alibaba and more " +
                    "with AirDrop’s official Florida Tax Exemption Certificate.",
                // Swift FigmaSalesTaxesViewController.swift:134 = Typography.h5() (24pt).
                style = AirdropType.h5,
                color = colors.textDarkTitle,
            )
            Text(
                text = "We prepare and provide the certificate for you — simply follow these " +
                    "steps on the marketplace where you want to shop:",
                style = AirdropType.body2,
                color = colors.textDarkTitle,
            )
            Spacer(Modifier.height(Spacing.sm)) // md gap before the steps card
            StepsCard()
            Spacer(Modifier.height(Spacing.sm)) // md gap before the outro card
            OutroCard()
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

// ─── "How to Activate" card ────────────────────────────────────────────────

@Composable
private fun StepsCard() {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "How to Activate Your Tax-Free Status",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
        Text(
            text = "Note: This step is only needed if your tax-free benefit isn’t " +
                "automatically applied. All AirDrop customers shipping from Amazon already " +
                "enjoy this benefit by default. Use these steps only if you’re shopping on " +
                "another website or if you’re not seeing the tax-free savings on Amazon.",
            style = AirdropType.body2,
            color = colors.textDarkTitle,
        )
        taxSteps.forEach { step -> StepCard(step) }
    }
}

@Composable
private fun StepCard(step: TaxStep) {
    val colors = AirdropTheme.colors
    val iconRes = if (colors.isDark) step.darkIconRes else step.iconRes
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.md),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .testTag(step.iconTag),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = step.title,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = boldRuns(step.body),
            style = AirdropType.body2,
            color = colors.textDarkTitle,
        )
    }
}

private fun boldRuns(runs: List<Pair<String, Boolean>>): AnnotatedString =
    buildAnnotatedString {
        runs.forEach { (text, bold) ->
            if (bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
            } else {
                append(text)
            }
        }
    }

// ─── Closing InfoCard ("That's it! 🎉") ────────────────────────────────────

@Composable
private fun OutroCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .background(AlertPalette.Light.OnHold)
            .border(1.dp, AlertPalette.Middle.OnHold, RoundedCornerShape(Radius.xs))
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFF292929)),
            modifier = Modifier.size(20.dp),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "That’s it! 🎉",
                style = AirdropType.subtitle1,
                color = Color(0xFF292929),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                outroBullets.forEach { bullet ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(AlertPalette.Light.Completed)
                                .border(1.dp, AlertPalette.Middle.Completed, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(AlertPalette.Completed),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Text(
                            text = bullet,
                            style = AirdropType.body2,
                            color = Color(0xFF292929),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
