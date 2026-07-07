package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServicesScreenParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun copyServiceInformationMatchesSwiftClipboardAndToast() {
        val clipboard = FakeClipboardManager()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                AirdropTheme {
                    Box(
                        Modifier
                            .width(375.dp)
                            .height(812.dp)
                            .background(AirdropTheme.colors.gray150)
                    ) {
                        ServicesScreen(onBack = {})
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Copy service information").performClick()

        compose.onNodeWithText("Content Copied").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                "Fast, secure, and reliable delivery services across Jamaica and beyond.\n" +
                    "Order online anytime with AirDrop and skip the long drives to the store.\n" +
                    "Shop Tax Free in Thousands of Stores.",
                clipboard.storedText?.text,
            )
        }
    }

    private class FakeClipboardManager : ClipboardManager {
        var storedText: AnnotatedString? = null

        override fun setText(annotatedString: AnnotatedString) {
            storedText = annotatedString
        }

        override fun getText(): AnnotatedString? = storedText
    }
}
