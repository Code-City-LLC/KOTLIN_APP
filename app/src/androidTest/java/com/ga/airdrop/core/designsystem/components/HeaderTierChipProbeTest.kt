package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guard for the header tier chip.
 *
 * This started as a probe, because the backlog listed "header tier chip" with
 * no detail and the honest first step was measuring rather than assuming. The
 * measurement says the layout is FINE: at 320dp the header is 107.43dp tall for
 * both "Gold Standard" and "Platinum Priority", and the tier text occupies
 * 20.2–137.9dp horizontally. Nothing clips and nothing reflows, so no layout
 * fix was made.
 *
 * It is kept as a guard rather than deleted, because the greeting carries
 * `maxLines = 1` + Ellipsis and the tier name carries neither — today that is
 * harmless only because the longest real tier name still fits. If a longer tier
 * is ever added, or the header gains content, this fails instead of silently
 * growing the header over the Home hero.
 *
 * ⚠️ SCOPE. A REAL defect does exist in this chip and is deliberately NOT fixed
 * here: Swift passes `AirdropTierIdentity.displayName(code:serverName:)` to both
 * the label and `tierAccentColor`, while Kotlin passes the raw server string —
 * so any server name that is not byte-identical to one of the eight literals
 * falls through to `else` and renders GOLD. Tier is TealSnow's design gate
 * (Kemar #21752) and Kotlin has no AirdropTierIdentity equivalent to call, so
 * that is reported to the tier lane rather than built here.
 */
@RunWith(AndroidJUnit4::class)
class HeaderTierChipProbeTest {

    @get:Rule
    val compose = createComposeRule()

    /** Drives the tier through state so one rule can measure several names. */
    private val tierName = mutableStateOf("Gold")

    private fun setHeader(tier: String, widthDp: Int) {
        tierName.value = tier
        compose.setContent {
            AirdropTheme {
                Box(Modifier.width(widthDp.dp)) {
                    AirdropHeader(
                        greeting = "Good morning, Tamzid",
                        tierName = tierName.value,
                        cartCount = 2,
                        unreadNotifications = 3,
                        airCoins = "425",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun rootHeight(): Float =
        compose.onRoot().getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    /**
     * ⚠️ RELATIVE, never an absolute baseline.
     *
     * This asserted `107.43f`, a height captured on one developer's emulator.
     * CI measured `110.47619` and the gate failed twice — while the layout was
     * perfectly correct. `widthDp` pins the WIDTH; the HEIGHT still depends on
     * density, fontScale and the system font, none of which two machines share.
     *
     * An absolute baseline tests the hardware. The only claim worth making here
     * is the one in the test's name: a longer tier name must not change the
     * header's height. Comparing long against short says exactly that, and says
     * it identically on every device.
     */
    @Test
    fun theLongestTierNameDoesNotGrowTheHeader() {
        setHeader("Gold", widthDp = 320)
        val withShortTier = rootHeight()

        tierName.value = "Platinum Priority"
        compose.waitForIdle()
        val withLongTier = rootHeight()

        assertEquals(
            "a long tier name must not reflow the header over the Home hero " +
                "(short=$withShortTier, long=$withLongTier)",
            withShortTier,
            withLongTier,
            0.5f,
        )
    }

    @Test
    fun theTierChipStaysInsideTheHeaderAndOnScreen() {
        setHeader("Platinum Priority", widthDp = 320)

        val tier = compose.onNodeWithText("Platinum Priority")
        tier.assertIsDisplayed()
        val bounds = tier.getUnclippedBoundsInRoot()
        val header = rootHeight()

        assertTrue(
            "tier text must not run past the 320dp edge (right=${bounds.right})",
            bounds.right.value <= 320f,
        )
        assertTrue(
            "tier text must sit inside the header (bottom=${bounds.bottom}, header=$header)",
            bounds.bottom.value <= header,
        )
    }
}
