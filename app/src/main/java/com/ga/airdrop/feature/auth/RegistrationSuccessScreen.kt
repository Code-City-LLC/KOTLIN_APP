package com.ga.airdrop.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme

/**
 * Registration Successful — Figma node 40006240:23983: wave background with
 * the bottom glass sheet (drag indicator, img_auth_success check
 * illustration, H5 title, Body-2 verification copy) and the outline
 * "Back to Log In" CTA that routes to Log In.
 */
@Composable
fun RegistrationSuccessScreen(onLogin: () -> Unit) {
    val colors = AirdropTheme.colors
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(
                if (colors.isDark) R.drawable.bg_auth_dark else R.drawable.bg_auth_light
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        AuthSuccessSheet(
            title = "Registration Successful",
            message = "A verification link has been sent to your email. Please click " +
                "the link to activate your account.\n\n" +
                "You must use this verification link to sign in for the first time " +
                "and complete your account verification.",
            buttonText = "Back to Log In",
            onButtonClick = onLogin,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
