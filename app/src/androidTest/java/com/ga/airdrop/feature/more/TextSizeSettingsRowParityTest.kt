package com.ga.airdrop.feature.more

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.TextSizeController
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PR92 review: row + recomposition pins for the Settings Text Size entry.
 * Renders the exact row composition SettingsScreen uses (MoreRowCard with
 * the controller-bound trailing value) and proves the trailing label
 * recomposes when the store changes — the same mechanism that rescales
 * every screen through the AirdropTheme density funnel.
 */
@RunWith(AndroidJUnit4::class)
class TextSizeSettingsRowParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun resetLevel() {
        TextSizeController.set(TextSizeController.Level.STANDARD)
    }

    @Test
    fun rowShowsTitleAndCurrentLevelAndOpensPickerOnTap() {
        var pickerOpened = false
        compose.setContent {
            AirdropTheme {
                MoreRowCard(
                    iconRes = R.drawable.ic_text_size,
                    title = "Text Size",
                    tint = AirdropTheme.colors.iconSelected,
                    onClick = { pickerOpened = true },
                    trailing = {
                        Text(
                            text = TextSizeController.level.displayName,
                            style = AirdropType.body2,
                            color = AirdropTheme.colors.gray500,
                        )
                    },
                    testTagPrefix = SettingsTags.TEXT_SIZE,
                )
            }
        }

        compose.onNodeWithText("Text Size").assertIsDisplayed()
        compose.onNodeWithText("Standard").assertIsDisplayed()

        compose.onNodeWithTag("${SettingsTags.TEXT_SIZE}-row").performClick()
        assertTrue("row tap must open the picker", pickerOpened)
    }

    @Test
    fun trailingValueRecomposesWhenTheStoreChanges() {
        compose.setContent {
            AirdropTheme {
                MoreRowCard(
                    iconRes = R.drawable.ic_text_size,
                    title = "Text Size",
                    tint = AirdropTheme.colors.iconSelected,
                    onClick = {},
                    trailing = {
                        Text(
                            text = TextSizeController.level.displayName,
                            style = AirdropType.body2,
                            color = AirdropTheme.colors.gray500,
                        )
                    },
                    testTagPrefix = SettingsTags.TEXT_SIZE,
                )
            }
        }

        compose.onNodeWithText("Standard").assertIsDisplayed()

        compose.runOnUiThread { TextSizeController.set(TextSizeController.Level.LARGEST) }
        compose.waitForIdle()

        compose.onNodeWithText("Largest").assertIsDisplayed()
    }
}
