package com.ga.airdrop.feature.delivery

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Eyes-on proof for the glyphs converted straight out of Figma.
 *
 * A vector drawable that parses is not a vector drawable that is *right* — the
 * Out for Delivery glyph is nine separate Figma children, each with its own
 * inset, translated into one 24dp viewport by hand. A mistake in that maths
 * produces valid XML and wrong art, which no compiler and no assertion catches.
 * So this renders the three that have been confused with one another, large,
 * side by side.
 */
@RunWith(AndroidJUnit4::class)
class TimelineGlyphProofTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theThreeVehicleGlyphsAreVisiblyDifferent() {
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier.testTag("glyph-proof"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        listOf(
                            R.drawable.ic_shipments_status_dispatch,
                            R.drawable.ic_shipments_status_out_for_delivery,
                            R.drawable.ic_shipments_status_in_transit_counter,
                        ).forEach {
                            Image(
                                painter = painterResource(it),
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        listOf(
                            R.drawable.ic_shipments_status_processing_customs,
                            R.drawable.ic_shipments_status_detained_customs,
                            R.drawable.ic_shipments_status_uncollected,
                        ).forEach {
                            Image(
                                painter = painterResource(it),
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "timeline_glyph_proof.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AirdropProof")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        )
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { stream ->
            check(
                compose.onNodeWithTag("glyph-proof").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream)
            )
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }
}
