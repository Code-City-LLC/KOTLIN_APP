package com.ga.airdrop.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import kotlinx.coroutines.delay

/** How long the splash stays up before routing on, mirroring the Swift
 *  RevealingSplashView delay (icon animation ~1.5s). */
private const val SPLASH_DURATION_MS = 1_600L

/**
 * Splash — Figma "Splash Screen" node 40006240:23896 (dark section
 * 40006149:75890): soft-wave background, centered logo, "Welcome to AirDrop"
 * and tricolor "Shop. Ship. Simplified." tagline. Static composable stand-in
 * for the Swift RevealingSplashView (logo fades in, then routes on).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val colors = AirdropTheme.colors

    var visible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "splashAlpha",
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_DURATION_MS)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.img_airdrop_logo),
                contentDescription = "AirDrop",
                modifier = Modifier
                    .fillMaxWidth(324f / 375f)
                    .aspectRatio(324.032f / 89.57f),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(61.dp))
            Text(
                text = "Welcome to AirDrop",
                style = AirdropType.h4,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp),
            )
            Spacer(Modifier.height(40.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFF88435))) { append("Shop.") }
                    withStyle(
                        SpanStyle(color = if (colors.isDark) Color(0xFFFFFFFF) else Color(0xFF243141))
                    ) { append(" Ship. ") }
                    withStyle(SpanStyle(color = Color(0xFF10BBE9))) { append("Simplified.") }
                },
                style = AirdropType.h5.copy(fontSize = 26.sp, lineHeight = 30.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp),
            )
        }
    }
}
