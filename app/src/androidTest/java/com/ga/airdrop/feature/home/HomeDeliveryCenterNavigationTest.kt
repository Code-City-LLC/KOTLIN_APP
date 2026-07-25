package com.ga.airdrop.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDeliveryCenterNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deliveryCenterCompanionTileOpensCanonicalRoute() {
        var destination: String? = null
        compose.setContent {
            AirdropTheme {
                HomeScreen(onNavigate = { destination = it })
            }
        }

        compose.onNodeWithTag("home-activity-delivery-center")
            .performScrollTo()
            .performClick()

        compose.runOnIdle {
            assertEquals(Routes.deliveryCenter(), destination)
        }
    }
}
