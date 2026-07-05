package com.ga.airdrop.core.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.components.AirdropBottomBar
import com.ga.airdrop.core.designsystem.components.AirdropTab
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRootNavigationParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appRootSwitchesFromMoreToHomeWithoutLeavingMoreContentVisible() {
        prepareApp(ThemeController.Mode.LIGHT)

        try {
            compose.setContent {
                AirdropTheme {
                    AppRoot()
                }
            }

            waitForHome()
            compose.onNodeWithContentDescription("More").performClick()
            compose.waitUntil(timeoutMillis = 8_000) {
                compose.onAllNodesWithText("FAQs").fetchSemanticsNodes().isNotEmpty()
            }
            saveRootScreenshot("app_root_more_before_home_tab.png")

            compose.onNodeWithContentDescription("Home").performClick()
            waitForHome()
            compose.waitUntil(timeoutMillis = 8_000) {
                compose.onAllNodesWithText("FAQs").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(0, compose.onAllNodesWithText("FAQs").fetchSemanticsNodes().size)
            saveRootScreenshot("app_root_home_after_more_tab.png")
        } finally {
            clearAuth()
        }
    }

    @Test
    fun switchTabRootSwapsEvenWhenHomeIsNotAlreadyInBackStack() {
        setNavigationHarness(startDestination = Routes.MORE)

        compose.onNodeWithTag("nav-more-root").assertExists()
        compose.onNodeWithContentDescription("Home").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("nav-home-root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-previous-route").assertTextEquals("Previous route: none")
        assertEquals(0, compose.onAllNodesWithTag("nav-more-root").fetchSemanticsNodes().size)
        saveRootScreenshot("harness_home_after_more_start.png")
    }

    @Test
    fun moreDrillDownDoesNotShowTabBarAsIfHomeWereSelected() {
        setNavigationHarness(startDestination = Routes.MORE)

        compose.onNodeWithText("Open FAQs").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("nav-faq").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(0, compose.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription("More").fetchSemanticsNodes().size)
    }

    private fun prepareApp(mode: ThemeController.Mode) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(mode)
            AuthTokenStore.save("ui-proof-token")
        }
    }

    private fun clearAuth() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AuthTokenStore.clear()
        }
    }

    private fun waitForHome() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("home-warehouse-standard").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun setNavigationHarness(startDestination: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                NavigationHarness(startDestination = startDestination)
            }
        }
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/home_tab_navigation")
            .also { it.mkdirs() }
    }
}

@Composable
private fun NavigationHarness(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = when (backStackEntry?.destination?.route) {
        Routes.HOME -> AirdropTab.Home
        Routes.MORE -> AirdropTab.More
        else -> null
    }
    val previousRoute = navController.previousBackStackEntry?.destination?.route ?: "none"

    Box(
        Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray200)
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) { HarnessDestination("Home root", "nav-home-root") }
            composable(Routes.MORE) { HarnessMoreRoot(navController) }
            composable(Routes.FAQ) { HarnessDestination("FAQs", "nav-faq") }
        }

        Text(
            text = "Previous route: $previousRoute",
            modifier = Modifier.testTag("nav-previous-route"),
        )

        if (currentTab != null) {
            AirdropBottomBar(
                selected = currentTab,
                onSelect = { navController.switchTab(it) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun HarnessMoreRoot(navController: NavHostController) {
    Column(
        Modifier
            .fillMaxSize()
            .testTag("nav-more-root")
    ) {
        Text("More root")
        Text(
            text = "Open FAQs",
            modifier = Modifier.clickable { navController.navigate(Routes.FAQ) },
        )
    }
}

@Composable
private fun HarnessDestination(text: String, tag: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}
