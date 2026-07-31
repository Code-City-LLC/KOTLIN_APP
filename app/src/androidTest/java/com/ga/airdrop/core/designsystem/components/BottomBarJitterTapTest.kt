package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression net for the porous-dock fix.
 *
 * ⚠️ WHY THIS EXISTS, and why performClick() is not enough.
 *
 * Sealing the dock needs a pointer-input node on the bar so hit-testing stops
 * there and content behind never receives the touch. The first attempt ALSO
 * consumed every change on the Main pass — which cancelled each tab's own
 * `detectTapAndPress`: the child checks its pass, sees nothing consumed, and
 * suspends on PointerEventPass.Final; Final tunnels parent-first, so by then
 * the bar had consumed and `waitForUpOrCancellation` returned null. The click
 * never fired.
 *
 * That killed all five tabs on a real device, because a finger tap almost
 * always emits position-changing MOVE samples — and the entire existing suite
 * stayed green, because `performClick()` injects down->move->up in a way that
 * did not reproduce it and TalkBack's semantics OnClick bypasses pointer input
 * entirely. So this test taps WITH deliberate jitter, which is the only shape
 * that catches the regression.
 *
 * Both properties are asserted together: a jittery tap must still navigate AND
 * dead dock space must still not reach the content behind.
 */
@RunWith(AndroidJUnit4::class)
class BottomBarJitterTapTest {

    @get:Rule
    val compose = createComposeRule()

    private fun content(onTab: (AirdropTab) -> Unit, onBehind: () -> Unit) {
        compose.setContent {
            AirdropTheme {
                Box(Modifier.fillMaxSize()) {
                    // Stands in for the NavHost the real bar is drawn over.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Magenta)
                            .testTag("content-behind")
                            .clickable { onBehind() }
                    )
                    AirdropBottomBar(
                        selected = AirdropTab.Home,
                        onSelect = onTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag("dock"),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun tabTapSurvivesFingerJitter() {
        var picked: AirdropTab? = null
        content(onTab = { picked = it }, onBehind = {})

        // A real finger never lands perfectly still.
        compose.onNodeWithContentDescription("Shop").performTouchInput {
            down(center)
            moveBy(Offset(2f, 2f))
            moveBy(Offset(1f, -1f))
            up()
        }
        compose.waitForIdle()

        assertEquals("a tab tap with finger jitter must still navigate", AirdropTab.Shop, picked)
    }

    @Test
    fun deadDockSpaceDoesNotReachContentBehind() {
        var behindTaps = 0
        var picked: AirdropTab? = null
        content(onTab = { picked = it }, onBehind = { behindTaps++ })

        // Top-left of the bar: the divider / side-margin strip, which is dock
        // chrome and belongs to no tab.
        compose.onNodeWithTag("dock").performTouchInput {
            down(Offset(2f, 2f))
            up()
        }
        compose.waitForIdle()

        assertEquals("the dock must swallow taps on its own chrome", 0, behindTaps)
        assertNull("dock chrome must not select a tab either", picked)
    }
}
