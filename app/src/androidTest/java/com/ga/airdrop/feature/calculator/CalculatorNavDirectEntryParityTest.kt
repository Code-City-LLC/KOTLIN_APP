package com.ga.airdrop.feature.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorNavDirectEntryParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun calculatorResultsDirectRouteUsesSwiftPlaceholderInputs() {
        setCalculatorNav(startDestination = Routes.CALCULATOR_RESULTS)

        waitForText("Results")
        compose.onNodeWithText("Total Weight").assertIsDisplayed()
        compose.onNodeWithText("1.00 lbs").assertIsDisplayed()
        compose.onNodeWithText("\$123.00").assertIsDisplayed()
        compose.onNodeWithText("Fuel").assertIsDisplayed()
        compose.onNodeWithText("Drop Alert").assertIsDisplayed()
        compose.onNodeWithText("Make Payment").assertIsDisplayed()
    }

    @Test
    fun governmentChargesDirectRouteCanReturnToCalculator() {
        setCalculatorNav(startDestination = Routes.CALCULATOR_GOVERNMENT_CHARGES)

        waitForText("Government Charges")
        compose.onNodeWithText("Cost (Declared Value/Invoice Amount)").assertIsDisplayed()
        compose.onNodeWithText("CIF Value").assertIsDisplayed()
        compose.onNodeWithText("Back to Calculator").assertIsDisplayed().performClick()

        waitForText("Shipping Calculator")
        compose.onNodeWithText("Invoice Amount USD").assertIsDisplayed()
    }

    private fun setCalculatorNav(startDestination: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                val navController = rememberNavController()
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                    ) {
                        calculatorGraph(navController)
                    }
                }
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
