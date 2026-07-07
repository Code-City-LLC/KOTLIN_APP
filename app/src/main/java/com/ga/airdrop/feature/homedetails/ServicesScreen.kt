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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.feature.homedetails.components.CopiedToastPill
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import kotlinx.coroutines.delay

/**
 * Services — behavior/copy from FigmaServicesViewController (RN ServicesView
 * canonical; no exact Figma frame exists for this marketing page — the node
 * referenced in planning, 40000798:7711 "Sales - Option 1", is a Shop-tab
 * auction layout, not this screen). Layout: satisfaction pill, mixed-color
 * heading, tagline, hero block, paragraphs, tax-free heading + logo rows,
 * "We Offer You" benefits card.
 */

private val benefits = listOf(
    "Duty-free allowance up to $100",
    "Store pickups in Miami and Fort Lauderdale",
    "Customs Brokerage",
    "A tax-free U.S. address",
    "Access to general support",
    "Package Insurance",
    "Free returns to the U.S. (conditions apply)",
    "Specialized Sourcing",
    "AirCoin Rewards",
    "No credit card? Use ours!",
    "Worldwide express exports from Jamaica",
    "Island wide delivery",
)

private val storeLogos = listOf(
    R.drawable.img_homedet_logo_amazon,
    R.drawable.img_homedet_logo_ebay,
    R.drawable.img_homedet_logo_walmart,
    R.drawable.img_homedet_logo_rhode,
    R.drawable.img_homedet_logo_alani,
)

private val customerPhotos = listOf(
    R.drawable.img_homedet_customer_1,
    R.drawable.img_homedet_customer_2,
    R.drawable.img_homedet_customer_3,
    R.drawable.img_homedet_customer_4,
    R.drawable.img_homedet_customer_5,
)

private const val SERVICES_COPY_TEXT =
    "Fast, secure, and reliable delivery services across Jamaica and beyond.\n" +
        "Order online anytime with AirDrop and skip the long drives to the store.\n" +
        "Shop Tax Free in Thousands of Stores."

@Composable
fun ServicesScreen(onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    val clipboard = LocalClipboardManager.current
    var showCopiedToast by remember { mutableStateOf(false) }

    if (showCopiedToast) {
        LaunchedEffect(showCopiedToast) {
            delay(2000)
            showCopiedToast = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
    ) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
            HomeDetailsHeader(
                title = "Services",
                onBack = onBack,
                trailingIconRes = R.drawable.ic_copy,
                trailingContentDescription = "Copy service information",
                onTrailingClick = {
                    clipboard.setText(AnnotatedString(SERVICES_COPY_TEXT))
                    showCopiedToast = true
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CustomerSatisfactionPill()
                Spacer(Modifier.height(Spacing.sm)) // md gap after the pill
                MainHeading()
                CenteredParagraph("Fast, secure, and reliable delivery services across Jamaica and beyond.")
                ServicesHero()
                CenteredParagraph(
                    "Order online anytime with AirDrop and skip the long drives to the store. " +
                        "Sit back while we take care of the forwarding, customs clearance, and " +
                        "processing on your behalf."
                )
                CenteredParagraph(
                    "We make shipping your favorite U.S. products fast, reliable, and affordable. " +
                        "Central to our ethos is a dedication to customer service excellence."
                )
                Spacer(Modifier.height(Spacing.md)) // lg gap before the tax-free block
                TaxFreeHeading()
                LogoRow(storeLogos)
                LogoRow(storeLogos.reversed())
                Spacer(Modifier.height(Spacing.md)) // lg gap before the benefits card
                BenefitsCard()
                Spacer(Modifier.height(Spacing.xxxl))
            }
        }

        if (showCopiedToast) {
            CopiedToastPill(
                text = "Content Copied",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp),
            )
        }
    }
}

// ─── Customer-satisfaction pill ────────────────────────────────────────────

@Composable
private fun CustomerSatisfactionPill() {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .shadow(4.dp, RoundedCornerShape(33.dp))
            .clip(RoundedCornerShape(33.dp))
            .background(colors.gray150)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Overlapping 37dp avatars, -12dp overlap, 2dp gray700 ring.
        Box(Modifier.width((37 + 4 * 25).dp).height(37.dp)) {
            customerPhotos.forEachIndexed { index, res ->
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = (25 * index).dp)
                        .size(37.dp)
                        .clip(CircleShape)
                        .border(2.dp, colors.gray700, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) {
                    Image(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = "13,000 Happy Customers",
                style = AirdropType.body3,
                color = colors.textDarkTitle,
            )
        }
    }
}

// ─── Headings & paragraphs ─────────────────────────────────────────────────

@Composable
private fun MainHeading() {
    val colors = AirdropTheme.colors
    Text(
        text = buildAnnotatedString {
            append("Shop From Any Store ")
            withStyle(SpanStyle(color = BrandPalette.OrangeMain)) { append("Worldwide,") }
            append(" & Ship To ")
            withStyle(SpanStyle(color = BrandPalette.BlueAccentMain)) { append("Jamaica") }
        },
        style = AirdropType.title1,
        color = colors.textDarkTitle,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CenteredParagraph(text: String) {
    Text(
        text = text,
        style = AirdropType.body2,
        color = AirdropTheme.colors.textDarkTitle,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TaxFreeHeading() {
    val colors = AirdropTheme.colors
    Text(
        text = buildAnnotatedString {
            append("Shop Tax Free in ")
            withStyle(SpanStyle(color = BrandPalette.OrangeMain)) { append("Thousands of Stores") }
        },
        style = AirdropType.title2,
        color = colors.textDarkTitle,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─── Hero block ────────────────────────────────────────────────────────────

/**
 * RN renders an animated flame PNG here (assets/AnimationIcons/animated.png)
 * that is bundled in neither repo; Swift ships an SF-Symbol flame stand-in.
 * Until the real asset lands, an orange-tinted block anchors the same slot
 * with the brand logo (no invented vector art, per the icon policy).
 */
@Composable
private fun ServicesHero() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(BrandPalette.OrangeTertiary5),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.img_airdrop_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

// ─── Store logo rows ───────────────────────────────────────────────────────

@Composable
private fun LogoRow(logos: List<Int>) {
    val colors = AirdropTheme.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(logos) { res ->
            Box(
                Modifier
                    .size(width = 100.dp, height = 60.dp)
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.gray300)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ─── Benefits card ─────────────────────────────────────────────────────────

@Composable
private fun BenefitsCard() {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs)),
    ) {
        // Header strip.
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.gray150)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "We Offer You",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
            Image(
                painter = painterResource(R.drawable.ic_small_arrow_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(-90f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            benefits.forEach { benefit ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.iconShape, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = benefit,
                        style = AirdropType.body2,
                        color = colors.textDescription,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
