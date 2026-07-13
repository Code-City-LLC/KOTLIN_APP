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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
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

/** Swift FigmaServicesViewController.onCopy (:174-178) — the fixed 3-line
 *  service-info block copied by the header copy button, verbatim. */
private val SERVICE_COPY_TEXT = listOf(
    "Fast, secure, and reliable delivery services across Jamaica and beyond.",
    "Order online anytime with AirDrop and skip the long drives to the store.",
    "Shop Tax Free in Thousands of Stores.",
).joinToString("\n")

/**
 * Services — Swift FigmaServicesViewController and Figma 40005190:30624.
 * Reuses the canonical hero, customer pill, two-row logo marquee, and benefits
 * card instead of the former placeholder hero.
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

@Composable
fun ServicesScreen(onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    val clipboard = LocalClipboardManager.current
    var toast by remember { mutableStateOf<String?>(null) }

    if (toast != null) {
        LaunchedEffect(toast) {
            delay(2000)
            toast = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .testTag("services-root"),
    ) {
    Column(
        Modifier
            .fillMaxSize()
    ) {
        HomeDetailsHeader(
            title = "Services",
            onBack = onBack,
            showDivider = false,
            // Swift figma.services.copy — copies the 3-line service info block.
            trailingIconRes = R.drawable.ic_copy,
            trailingContentDescription = "Copy service information",
            onTrailingClick = {
                clipboard.setText(AnnotatedString(SERVICE_COPY_TEXT))
                toast = "Content Copied"
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CustomerSatisfactionPill()
            Spacer(Modifier.height(Spacing.lg))
            MainHeading()
            Spacer(Modifier.height(Spacing.sm))
            CenteredParagraph(
                text = "Fast, secure, and reliable delivery services across Jamaica and beyond",
                modifier = Modifier.width(293.dp),
            )
            Spacer(Modifier.height(Spacing.sm1))
            ServicesHero()
            Spacer(Modifier.height(Spacing.sm1))
            CenteredParagraph(
                "Order online anytime with AirDrop and skip the long drives to the store. " +
                    "Sit back while we take care of the forwarding, customs clearance, and " +
                    "processing on your behalf.\n\n" +
                    "We make shipping your favorite U.S. products fast, reliable, and affordable. " +
                    "Central to our ethos is a dedication to customer service excellence."
            )
            Spacer(Modifier.height(Spacing.lg))
            TaxFreeHeading()
            Spacer(Modifier.height(Spacing.md))
            LogoMarquee(storeLogos)
            Spacer(Modifier.height(25.dp))
            BenefitsCard()
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }

        toast?.let {
            CopiedToastPill(
                text = it,
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
            .height(68.dp)
            .testTag("services-customer-pill")
            .clip(RoundedCornerShape(34.dp))
            .background(colors.gray150)
            .border(1.dp, colors.cardHairline, RoundedCornerShape(34.dp))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Swift/Figma: five 27dp avatars with 8.44dp overlap.
        Box(
            Modifier
                .width(101.dp)
                .height(27.dp)
                .testTag("services-customer-avatars")
        ) {
            customerPhotos.forEachIndexed { index, res ->
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = (18.56f * index).dp)
                        .size(27.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.gray700, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(5) {
                    Image(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier.size(16.dp),
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
            withStyle(SpanStyle(color = colors.orangeDark)) { append("Worldwide,") }
            append(" & Ship To ")
            withStyle(SpanStyle(color = BrandPalette.BlueAccentMain)) { append("Jamaica") }
        },
        style = AirdropType.h4,
        color = colors.textDarkTitle,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("services-main-heading"),
    )
}

@Composable
private fun CenteredParagraph(text: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    Text(
        text = text,
        style = AirdropType.body2,
        color = AirdropTheme.colors.textDarkTitle,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun TaxFreeHeading() {
    val colors = AirdropTheme.colors
    Text(
        text = buildAnnotatedString {
            append("Shop Tax Free in ")
            withStyle(SpanStyle(color = colors.orangeDark)) { append("Thousands of Stores") }
        },
        style = AirdropType.h4,
        color = colors.textDarkTitle,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("services-tax-free-heading"),
    )
}

// ─── Hero block ────────────────────────────────────────────────────────────

/**
 * Swift's ServicesHeroAnimated asset is the API-exported Figma
 * 40005190:30632 artwork. Its 667x253 canvas is centered inside the 335x250
 * viewport so the waves bleed to the screen edges exactly like iOS.
 */
@Composable
private fun ServicesHero() {
    val colors = AirdropTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(250.2766f.dp)
            .testTag("services-hero"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                if (colors.isDark) {
                    R.drawable.img_homedet_services_hero_dark
                } else {
                    R.drawable.img_homedet_services_hero_light
                }
            ),
            contentDescription = null,
            modifier = Modifier
                .requiredSize(width = 667.dp, height = 253.dp)
                .testTag("services-hero-image"),
            contentScale = ContentScale.FillBounds,
        )
        if (colors.isDark) {
            Box(
                Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(colors.gray150),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.img_airdrop_logo_dark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.requiredSize(width = 70.dp, height = 72.dp),
                )
            }
        }
    }
}

// ─── Store logo rows ───────────────────────────────────────────────────────

@Composable
private fun LogoMarquee(logos: List<Int>) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
            .testTag("services-logo-marquee")
            .padding(vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LogoMarqueeRow(
            logos = logos,
            movesForward = true,
            initialIndex = 0,
            initialDelayMillis = 350,
            intervalMillis = 2_000,
            tag = "services-logo-row-top",
        )
        LogoMarqueeRow(
            logos = logos,
            movesForward = false,
            initialIndex = logos.size,
            initialDelayMillis = 600,
            intervalMillis = 2_200,
            tag = "services-logo-row-bottom",
        )
    }
}

@Composable
private fun LogoMarqueeRow(
    logos: List<Int>,
    movesForward: Boolean,
    initialIndex: Int,
    initialDelayMillis: Long,
    intervalMillis: Long,
    tag: String,
) {
    val colors = AirdropTheme.colors
    val displayLogos = remember(logos) { logos + logos + logos }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LaunchedEffect(logos, movesForward) {
        var index = initialIndex
        delay(initialDelayMillis)
        while (true) {
            index += if (movesForward) 1 else -1
            when {
                movesForward && index >= logos.size * 2 -> {
                    index = logos.size
                    state.scrollToItem(index)
                }
                !movesForward && index <= 0 -> {
                    index = logos.size
                    state.scrollToItem(index)
                }
                else -> state.animateScrollToItem(index)
            }
            delay(intervalMillis)
        }
    }

    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(displayLogos) { res ->
            Box(
                Modifier
                    .size(width = 100.dp, height = 40.dp)
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.gray300)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
            .border(1.dp, colors.cardHairline, RoundedCornerShape(Radius.xs)),
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
                .background(colors.divider)
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
                            colorFilter = ColorFilter.tint(colors.orangeDark),
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
