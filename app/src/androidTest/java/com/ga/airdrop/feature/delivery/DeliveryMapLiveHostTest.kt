package com.ga.airdrop.feature.delivery

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.PixelCopy
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliveryMapLiveHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mapCardLoadsTheLivePickerFromTheFlavorApiOrigin() {
        loadPickerAndWaitForMap()

        val (bitmap, _) = waitForRenderedContent()
        saveProofScreenshot(bitmap, "${BuildConfig.ENV_NAME.lowercase()}_picker.png")
    }

    @Test
    fun productionPickerRendersWithoutGoogleMapsProviderError() {
        assumeTrue(
            "Google Maps provider acceptance is production-only",
            BuildConfig.ENV_NAME == "Production",
        )
        loadPickerAndWaitForMap()

        val (bitmap, mapBounds) = waitForRenderedContent()
        assertFalse(
            "Google Maps rejected the production picker host or API key",
            hasGoogleMapsProviderError(),
        )
        assertTrue(
            "The production picker loaded but rendered a blank map card",
            bitmap.sampledColorCount(mapBounds) > 20,
        )
        saveProofScreenshot(bitmap, "production_picker.png")
    }

    private fun loadPickerAndWaitForMap() {
        compose.setContent {
            AirdropTheme {
                DeliveryMapView(
                    center = null,
                    marker = null,
                    addressLabel = null,
                    onPointPicked = null,
                )
            }
        }
        compose.onNodeWithTag("delivery-map").assertIsDisplayed()

        var loadedUrl: String? = null
        var loadedTitle: String? = null
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            compose.runOnIdle {
                val webView = compose.activity.window.decorView.findWebView()
                loadedUrl = webView?.url
                loadedTitle = webView?.title
            }
            if (loadedTitle == "Select Delivery Location") break
            Thread.sleep(100)
        }

        assertNotNull("Delivery picker URL was never loaded", loadedUrl)
        assertEquals(deliveryPickerBaseUrl(BuildConfig.API_BASE_URL), loadedUrl)
        assertEquals("Select Delivery Location", loadedTitle)
        assertTrue("Google Maps never initialized inside the picker", waitForMapReady())
    }

    private fun waitForMapReady(): Boolean {
        var mapReady = false
        val deadline = System.currentTimeMillis() + 20_000
        while (!mapReady && System.currentTimeMillis() < deadline) {
            val callback = CountDownLatch(1)
            compose.runOnIdle {
                val webView = compose.activity.window.decorView.findWebView()
                if (webView == null) {
                    callback.countDown()
                } else {
                    webView.evaluateJavascript(
                        "Boolean(document.getElementById('map') && window.google && " +
                            "window.google.maps && window.map && " +
                            "document.querySelector('#map .gm-style')).toString()"
                    ) { value ->
                        mapReady = value == "\"true\"" || value == "true"
                        callback.countDown()
                    }
                }
            }
            callback.await(2, TimeUnit.SECONDS)
            if (!mapReady) Thread.sleep(100)
        }
        return mapReady
    }

    private fun hasGoogleMapsProviderError(): Boolean {
        var hasError = false
        val callback = CountDownLatch(1)
        compose.runOnIdle {
            val webView = compose.activity.window.decorView.findWebView()
            if (webView == null) {
                callback.countDown()
            } else {
                webView.evaluateJavascript(
                    "Boolean((document.body && document.body.innerText.includes(" +
                        "'This page can\\'t load Google Maps correctly')) || " +
                        "document.querySelector('.gm-err-container, .gm-err-message')).toString()"
                ) { value ->
                    hasError = value == "\"true\"" || value == "true"
                    callback.countDown()
                }
            }
        }
        assertTrue("Timed out checking Google Maps provider state", callback.await(2, TimeUnit.SECONDS))
        return hasError
    }

    private fun waitForRenderedContent(): Pair<Bitmap, ViewBounds> {
        lateinit var latest: Pair<Bitmap, ViewBounds>
        val deadline = System.currentTimeMillis() + 20_000
        do {
            latest = captureWindowAndMapBounds()
            if (latest.first.sampledColorCount(latest.second) > 20) return latest
            Thread.sleep(250)
        } while (System.currentTimeMillis() < deadline)
        return latest
    }

    private fun captureWindowAndMapBounds(): Pair<Bitmap, ViewBounds> {
        lateinit var bitmap: Bitmap
        lateinit var bounds: ViewBounds
        compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val map = requireNotNull(decor.findWebView())
            val location = IntArray(2)
            map.getLocationInWindow(location)
            bounds = ViewBounds(
                left = location[0],
                top = location[1],
                right = location[0] + map.width,
                bottom = location[1] + map.height,
            )
            bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        }

        val callback = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(
            compose.activity.window,
            bitmap,
            { code -> result = code; callback.countDown() },
            Handler(Looper.getMainLooper()),
        )
        assertTrue("Timed out capturing the rendered map", callback.await(5, TimeUnit.SECONDS))
        assertEquals("PixelCopy could not capture the rendered map", PixelCopy.SUCCESS, result)
        return bitmap to bounds
    }

    @Suppress("InlinedApi")
    private fun saveProofScreenshot(bitmap: Bitmap, filename: String) {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PROOF_SCREENSHOT_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Unable to create proof screenshot $filename" }
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Unable to open proof screenshot $filename" }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private companion object {
        const val PROOF_SCREENSHOT_DIR = "Pictures/kotlin_ui_proof/delivery_picker"
    }
}

private data class ViewBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun Bitmap.sampledColorCount(bounds: ViewBounds): Int {
    val colors = mutableSetOf<Int>()
    val left = bounds.left.coerceIn(0, width)
    val right = bounds.right.coerceIn(left, width)
    val top = bounds.top.coerceIn(0, height)
    val bottom = bounds.bottom.coerceIn(top, height)
    for (y in top until bottom step 4) {
        for (x in left until right step 4) {
            val color = getPixel(x, y)
            if (Color.alpha(color) == 0) continue
            val quantized =
                (Color.red(color) / 16 shl 8) or
                    (Color.green(color) / 16 shl 4) or
                    (Color.blue(color) / 16)
            colors += quantized
            if (colors.size > 20) return colors.size
        }
    }
    return colors.size
}

private fun View.findWebView(): WebView? = when (this) {
    is WebView -> this
    is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
        getChildAt(index).findWebView()
    }
    else -> null
}
