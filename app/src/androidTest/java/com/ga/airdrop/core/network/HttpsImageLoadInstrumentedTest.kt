package com.ga.airdrop.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof for the P1 "Shop auction/featured images blank on prod" fix.
 *
 * The prod backend (APP_URL=http://app.airdropja.com) hands the app cleartext
 * http:// storage URLs. Android blocks cleartext HTTP by default (targetSdk 35,
 * no override in the manifest) so Coil silently drops those loads and the cards
 * render blank. [HttpsImageInterceptor] on the app-wide ImageLoader upgrades the
 * scheme to https:// before the socket opens.
 *
 * Auth-free: /products is public and /storage assets need no session, so this
 * reproduces the exact failing load without a prod account. Requires the
 * emulator to reach app.airdropja.com; if the live feed ever returns only https
 * URLs the tests self-skip (nothing cleartext left to prove).
 */
@RunWith(AndroidJUnit4::class)
class HttpsImageLoadInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var cleartextProdImageUrl: String

    @Before
    fun fetchLiveCleartextProdImageUrl() {
        val response = OkHttpClient().newCall(
            Request.Builder()
                .url("https://app.airdropja.com/api/v1/products?per_page=12")
                .header("Accept", "application/json")
                .build(),
        ).execute().use { it.body?.string().orEmpty() }

        // Laravel json_encode escapes forward slashes as \/ — unescape first.
        val unescaped = response.replace("\\/", "/")
        val match = Regex(
            "http://app\\.airdropja\\.com/storage/[A-Za-z0-9/_.-]+?\\.(?:png|jpg|jpeg|webp)",
        ).find(unescaped)?.value

        assumeTrue("prod feed served no cleartext http:// product image to test", match != null)
        cleartextProdImageUrl = match!!
        assertTrue(cleartextProdImageUrl.startsWith("http://app.airdropja.com/storage/"))
    }

    @Test
    fun appImageLoader_upgradesAndLoadsCleartextProdImage() = runBlocking {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(cleartextProdImageUrl).build(),
        )
        assertTrue(
            "app ImageLoader must upgrade+load $cleartextProdImageUrl — got ${result::class.simpleName}",
            result is SuccessResult,
        )
    }

    @Test
    fun vanillaLoaderWithoutUpgrade_isBlockedOnCleartextProdImage() = runBlocking {
        // Reproduces the bug: a default loader (no https upgrade) is dropped by
        // Android's cleartext block, which is why the prod cards were blank.
        val result = ImageLoader.Builder(context).build().execute(
            ImageRequest.Builder(context).data(cleartextProdImageUrl).build(),
        )
        assertTrue(
            "cleartext load should fail without the upgrade — got ${result::class.simpleName}",
            result is ErrorResult,
        )
    }
}
