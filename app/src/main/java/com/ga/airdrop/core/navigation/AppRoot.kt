package com.ga.airdrop.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType

/**
 * App entry: token present → Home, else Login. Mirrors SceneDelegate logic
 * in the Swift app. Screen graph filled in per feature as screens land.
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val token by AuthTokenStore.tokenFlow.collectAsState()
    val startDestination = if (token != null) Routes.HOME else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray200),
    ) {
        composable(Routes.LOGIN) { PlaceholderScreen("Login") }
        composable(Routes.HOME) { PlaceholderScreen("Home") }
    }
}

/** Temporary stand-in while screens are being built out; never shipped. */
@Composable
internal fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray200),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = AirdropType.h5,
            color = AirdropTheme.colors.textDarkTitle,
        )
    }
}
