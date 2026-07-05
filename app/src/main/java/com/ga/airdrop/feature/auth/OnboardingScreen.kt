package com.ga.airdrop.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.ThemeToggle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.GradientPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.ThemeController
import kotlinx.coroutines.launch

/** Figma onboarding title accents: navy in light mode, white in dark. */
private val TitleBlue = Color(0xFF0A96D4)
private val TitleNavyLight = Color(0xFF243141)

/** One intro slide (Figma 40006240:23774/23798/23823/23848/23872). */
private data class OnboardIntroPage(
    val imageRes: Int,
    /** Intrinsic size of the 3x export, in dp (px / 3). */
    val imageWidth: Dp,
    val imageHeight: Dp,
    /** Top edge of the illustration cluster in the 812-tall frame. */
    val imageTop: Dp,
    /** (navy chunk, blue chunk) pairs concatenated in order. */
    val title: List<Pair<String, Boolean>>,
    val description: String,
)

private val introPages = listOf(
    OnboardIntroPage(
        imageRes = R.drawable.img_auth_onboard_1,
        imageWidth = 259.dp, imageHeight = 303.67.dp, imageTop = 122.34.dp,
        title = listOf("Shop & Send\n" to false, "Easily" to true),
        description = "Browse products or upload shipments directly from your phone in just seconds.",
    ),
    OnboardIntroPage(
        imageRes = R.drawable.img_auth_onboard_2,
        imageWidth = 253.dp, imageHeight = 282.dp, imageTop = 144.dp,
        title = listOf("Smart Pickup &\n" to false, "Tracking" to true),
        description = "Select pickup points, schedule pickups, and track your parcels in real time.",
    ),
    OnboardIntroPage(
        imageRes = R.drawable.img_auth_onboard_3,
        imageWidth = 285.67.dp, imageHeight = 282.dp, imageTop = 144.dp,
        title = listOf("Ship " to false, "Worldwide" to true, "\nwith Ease" to false),
        description = "Send packages anywhere around the globe fast, reliable, and affordable shipping.",
    ),
    OnboardIntroPage(
        imageRes = R.drawable.img_auth_onboard_4,
        imageWidth = 253.67.dp, imageHeight = 282.dp, imageTop = 144.dp,
        title = listOf("Manage Deliveries\n" to false, "Securely" to true),
        description = "Stay updated with shipment stages, customs clearance, and delivery confirmation",
    ),
    OnboardIntroPage(
        imageRes = R.drawable.img_auth_onboard_5,
        imageWidth = 312.67.dp, imageHeight = 282.dp, imageTop = 144.dp,
        title = listOf("Unbox\n" to false, "Happiness" to true),
        description = "Receive your packages safely and enjoy fast, smooth delivery experiences every time",
    ),
)

/** Index of the trailing "Choose Your Look" page (Figma 40006240:24027). */
private const val THEME_PAGE = 5
private const val PAGE_COUNT = 6

/**
 * First-run onboarding — Figma "Onboarding - Design Done" nodes
 * 40006240:23774/23798/23823/23848/23872 (intro pager with Skip/Next and
 * the 5-dash indicator, 40006240:23781) + 40006240:24027 ("Choose Your
 * Look" theme picker with Continue). Completing (or skipping through) marks
 * [OnboardingStore] seen and routes to the auth landing.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { PAGE_COUNT }

    val finish = {
        OnboardingStore.markSeen(context)
        onFinished()
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(
                if (colors.isDark) R.drawable.bg_auth_dark else R.drawable.bg_auth_light
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page < THEME_PAGE) {
                OnboardIntroPageContent(introPages[page])
            } else {
                OnboardThemePage(onContinue = finish)
            }
        }
        if (pagerState.currentPage < THEME_PAGE) {
            // Header row with the small light/dark pill (Figma Header Type).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(62.dp)
                    .padding(horizontal = Spacing.md, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeToggle()
            }
            // Static indicator + Skip/Next chrome (identical across slides).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardPageIndicator(current = pagerState.currentPage)
                Spacer(Modifier.height(61.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnboardSkipButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(THEME_PAGE) } },
                    )
                    OnboardNextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Illustration + two-tone H4 title + Body-1 description of an intro slide.
 *
 * Figma 40006240:23774… lays these out top-down at fixed offsets: the
 * illustration group at [OnboardIntroPage.imageTop], the title 13dp below it
 * and the description 10dp under the title. The illustrations are transparent
 * PNGs (re-exported from the Figma group fills) so the 3D objects float on the
 * auth gradient — they must NOT sit inside a white plate, and the title must
 * hug the illustration rather than drift to the bottom of the screen.
 */
@Composable
private fun OnboardIntroPageContent(page: OnboardIntroPage) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(page.imageTop))
        Image(
            painter = painterResource(page.imageRes),
            contentDescription = null,
            modifier = Modifier.size(page.imageWidth, page.imageHeight),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(13.dp))
        Text(
            text = onboardTitle(page.title, colors.isDark),
            style = AirdropType.h4,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = page.description,
            style = AirdropType.body1,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        )
    }
}

private fun onboardTitle(parts: List<Pair<String, Boolean>>, isDark: Boolean): AnnotatedString =
    buildAnnotatedString {
        parts.forEach { (chunk, isBlue) ->
            withStyle(
                SpanStyle(
                    color = when {
                        isBlue -> TitleBlue
                        isDark -> Color.White
                        else -> TitleNavyLight
                    },
                ),
            ) { append(chunk) }
        }
    }

/**
 * Figma Frame 2147207861: five 35dp lines, 3dp round-cap stroke, 10dp gaps
 * (38dp visual dashes, 7dp apart) — active #F15114, inactive gray500 @20%.
 */
@Composable
private fun OnboardPageIndicator(current: Int, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(introPages.size) { index ->
            Box(
                Modifier
                    .width(38.dp)
                    .height(3.dp)
                    .background(
                        if (index == current) BrandPalette.OrangeMain
                        else colors.gray500.copy(alpha = 0.2f),
                        RoundedCornerShape(Radius.full),
                    ),
            )
        }
    }
}

/** Ghost small button (Figma Static/Ghost/Small): 46dp, orange border, radius 10. */
@Composable
private fun OnboardSkipButton(onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(Radius.xs))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Skip", style = AirdropType.button, color = colors.textDarkTitle)
    }
}

/** Primary small button (Figma Static/Primary/Small): 106x46 gradient + arrow. */
@Composable
private fun OnboardNextButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(106.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(Brush.verticalGradient(GradientPalette.SignInButton))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.75.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Next", style = AirdropType.button, color = BrandPalette.White)
        Image(
            painter = painterResource(R.drawable.ic_auth_arrow_next),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * "Choose Your Look" page — Figma 40006240:24027: sun illustration, two-tone
 * H4 title, Dark/Light labels around the large 78x45 toggle, gradient
 * Continue and the settings note.
 */
@Composable
private fun OnboardThemePage(onContinue: () -> Unit) {
    val colors = AirdropTheme.colors
    val labelStyle = AirdropType.subtitle1.copy(fontSize = 24.sp, lineHeight = 26.sp)
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.img_auth_onboard_6),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 150.dp)
                .size(320.33.dp, 281.dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 455.dp)
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TitleBlue)) { append("Choose") }
                    withStyle(
                        SpanStyle(color = if (colors.isDark) Color.White else TitleNavyLight),
                    ) { append(" Your Look") }
                },
                style = AirdropType.h4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Select Light or Dark mode for the best experience.",
                style = AirdropType.body1,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(25.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Dark", style = labelStyle, color = colors.textDarkTitle)
                LargeThemeToggle()
                Text(text = "Light", style = labelStyle, color = colors.textDarkTitle)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GradientButton(text = "Continue", onClick = onContinue)
            Spacer(Modifier.height(27.dp))
            Text(
                text = "Note: You can change this anytime in Settings.",
                style = AirdropType.body1,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The 78x45 pill from Figma 40006240:24042 — the header ThemeToggle scaled
 * up: knob right in light mode, left in dark, 26dp sun glyph.
 */
@Composable
private fun LargeThemeToggle(modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    val isDark = colors.isDark
    Row(
        modifier = modifier
            .width(78.dp)
            .height(45.dp)
            .background(
                if (isDark) colors.gray200 else Color(0xFFE9E8E8),
                CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                ThemeController.set(
                    if (isDark) ThemeController.Mode.LIGHT else ThemeController.Mode.DARK
                )
            }
            .padding(3.dp),
        horizontalArrangement = if (isDark) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .shadow(2.dp, CircleShape)
                .background(if (isDark) Color(0xFF4D4D4D) else colors.gray100, CircleShape)
                .padding(6.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Sun in light, blue moon in dark — must track the theme (same
            // fix as the header ThemeToggle; a fixed sun was the wrong icon).
            Image(
                painter = painterResource(
                    if (isDark) R.drawable.ic_toggle_moon else R.drawable.ic_toggle_sun
                ),
                contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
