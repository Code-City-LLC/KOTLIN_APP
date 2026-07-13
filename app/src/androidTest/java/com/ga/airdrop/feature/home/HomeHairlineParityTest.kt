package com.ga.airdrop.feature.home

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.designsystem.theme.darkAirdropColors
import com.ga.airdrop.core.designsystem.theme.lightAirdropColors
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeHairlineParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun standardSeaDropAndReferUseSwiftHairlineLight() {
        assertHomeHairlines(
            mode = ThemeController.Mode.LIGHT,
            expectedArgb = lightAirdropColors.cardHairline.toArgb(),
            expectedSurfaceArgb = lightAirdropColors.gray150.toArgb(),
        )
    }

    @Test
    fun standardSeaDropAndReferUseSwiftHairlineDark() {
        assertHomeHairlines(
            mode = ThemeController.Mode.DARK,
            expectedArgb = darkAirdropColors.cardHairline.toArgb(),
            expectedSurfaceArgb = darkAirdropColors.gray150.toArgb(),
        )
    }

    private fun assertHomeHairlines(
        mode: ThemeController.Mode,
        expectedArgb: Int,
        expectedSurfaceArgb: Int,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                HomeScreen(onNavigate = {})
            }
        }
        compose.waitForIdle()

        listOf("standard", "seadrop", "express").forEach { type ->
            compose.onNodeWithTag("home-warehouse-carousel")
                .performScrollToNode(hasTestTag("home-warehouse-$type"))
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("home-warehouse-$type")
                .captureToImage()
                .asAndroidBitmap()
            assertEdgeUsesHairline(
                label = "$mode $type warehouse card",
                bitmap = bitmap,
                expectedArgb = expectedArgb,
            )
            assertOpaqueSurface(
                label = "$mode $type warehouse card",
                bitmap = bitmap,
                expectedArgb = expectedSurfaceArgb,
                horizontalInsetDp = 12f,
            )
            saveProof(bitmap, mode, type)
        }

        compose.onNodeWithTag("home-refer-friend").performScrollTo()
        compose.waitForIdle()
        val referBitmap = compose.onNodeWithTag("home-refer-friend")
            .captureToImage()
            .asAndroidBitmap()
        assertEdgeUsesHairline(
            label = "$mode Refer a friend card",
            bitmap = referBitmap,
            expectedArgb = expectedArgb,
        )
        assertOpaqueSurface(
            label = "$mode Refer a friend card",
            bitmap = referBitmap,
            expectedArgb = expectedSurfaceArgb,
            horizontalInsetDp = 8f,
        )
        saveProof(referBitmap, mode, "refer")
    }

    private fun assertEdgeUsesHairline(label: String, bitmap: Bitmap, expectedArgb: Int) {
        // A 1dp Compose border is multiple physical pixels on the proof AVD.
        // Sample only its outer two pixels so the card fill cannot make an
        // incorrect border look green (especially in dark mode).
        val edgeBand = minOf(2, bitmap.width / 4, bitmap.height / 4).coerceAtLeast(1)
        val verticalInset = (bitmap.height * 0.2f).toInt()
        var matchingPixels = 0

        // Use only the straight left/right rails. The warehouse card sits over
        // a photo, so top/bottom pixels can coincidentally equal the dark
        // hairline even when the actual translucent border is wrong.
        for (y in verticalInset until (bitmap.height - verticalInset)) {
            for (offset in 0 until edgeBand) {
                if (bitmap.getPixel(offset, y).isNear(expectedArgb)) matchingPixels += 1
                if (bitmap.getPixel(bitmap.width - 1 - offset, y).isNear(expectedArgb)) matchingPixels += 1
            }
        }

        assertTrue(
            "$label must render the shared Swift cardHairline on its actual edge; " +
                "matchingPixels=$matchingPixels size=${bitmap.width}x${bitmap.height}",
            matchingPixels >= 20,
        )
    }

    private fun Int.isNear(target: Int, tolerance: Int = 4): Boolean {
        val alpha = (this ushr 24) and 0xFF
        if (alpha < 180) return false
        return abs(((this shr 16) and 0xFF) - ((target shr 16) and 0xFF)) <= tolerance &&
            abs(((this shr 8) and 0xFF) - ((target shr 8) and 0xFF)) <= tolerance &&
            abs((this and 0xFF) - (target and 0xFF)) <= tolerance
    }

    private fun assertOpaqueSurface(
        label: String,
        bitmap: Bitmap,
        expectedArgb: Int,
        horizontalInsetDp: Float,
    ) {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .displayMetrics
            .density
        val x = (horizontalInsetDp * density).toInt().coerceIn(0, bitmap.width - 1)
        val actual = bitmap.getPixel(x, bitmap.height / 2)
        assertTrue(
            "$label must render the opaque Swift/Figma post-blur surface; " +
                "expected=${expectedArgb.toUInt().toString(16)} " +
                "actual=${actual.toUInt().toString(16)}",
            actual.isNear(expectedArgb),
        )
    }

    @Suppress("InlinedApi")
    private fun saveProof(bitmap: Bitmap, mode: ThemeController.Mode, surface: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "home_hairline_${mode.name.lowercase()}_$surface.png",
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PROOF_DIRECTORY)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Unable to create $mode $surface hairline proof" }
        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Unable to open $mode $surface hairline proof" }
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode $mode $surface hairline proof"
            }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private companion object {
        const val PROOF_DIRECTORY = "Pictures/AirdropProofs/HomeHairlines"
    }
}
