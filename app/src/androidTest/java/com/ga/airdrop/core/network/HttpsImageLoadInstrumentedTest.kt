package com.ga.airdrop.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ga.airdrop.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/**
 * On-device proof for the P1 "Shop auction/featured images blank on prod" fix.
 *
 * Android blocks cleartext HTTP by default (targetSdk 35, no override in the
 * manifest), so a `http://` storage URL is silently dropped by Coil and the card
 * renders blank. [HttpsImageInterceptor] on the app-wide ImageLoader upgrades
 * the scheme to `https://` before the socket opens.
 *
 * ⚠️ THE HOST IS NEVER WRITTEN DOWN HERE. It is derived from
 * [BuildConfig.API_BASE_URL], so this file follows the flavor and cannot rot
 * into pointing at a retired host again — which is exactly what happened. This
 * file held the old `app.` subdomain in four places (a live request, a regex,
 * an assertion and two comments) after that host was permanently retired, and
 * nothing failed, because the connected gate is diff-selective: it runs only the
 * androidTest packages matching changed `main/java` files. Nothing had changed
 * under `core/network`, so this never executed. A green gate was not evidence
 * this file worked.
 *
 * ⚠️ AND IT NO LONGER WAITS FOR THE BACKEND TO SERVE A BUG. The previous version
 * scanned the live feed for a cleartext URL and called `assumeTrue` when it
 * found none. The backend now serves https for every storage asset (verified
 * 2026-08-02: 58 https URLs, 0 cleartext), so that version would have SKIPPED
 * silently and forever while reporting a green suite — the exact failure mode
 * this file was already burned by once.
 *
 * So the cleartext input is CONSTRUCTED: take a real, live storage asset and
 * downgrade its scheme. That is a genuine cleartext load of a genuine asset, it
 * proves the interceptor rather than the backend's current configuration, and it
 * cannot evaporate the next time ops fixes something.
 */
@RunWith(AndroidJUnit4::class)
class HttpsImageLoadInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A real live asset, with its scheme downgraded to http:// on purpose. */
    private lateinit var cleartextImageUrl: String

    @Before
    fun buildCleartextUrlFromLiveAsset() {
        val apiBase = BuildConfig.API_BASE_URL.trimEnd('/')
        val flavorHost = requireNotNull(URI(apiBase).host) { "API_BASE_URL has no host: $apiBase" }
        // Registrable domain: the last two labels of whatever host this flavor
        // uses. Still never writes a host down.
        val apex = flavorHost.split(".").takeLast(2).joinToString(".")

        // The flavor's own catalog first. If it advertises no asset on its own
        // host, fall back to the apex catalog — the interceptor under test is
        // app-wide and host-agnostic across the domain, so a real asset from
        // either is a valid proof.
        //
        // ⚠️ THE FALLBACK IS LOAD-BEARING TODAY, AND IT IS COVERING A BACKEND
        // DEFECT, NOT A TEST WEAKNESS. The pre-staging API currently serves
        // every product image on the RETIRED `app.` subdomain (54 of them,
        // checked 2026-08-02) while prod correctly serves the apex. Those
        // staging image links are dead. Reported to Laravel — see ORC. When
        // that is fixed the flavor branch will match directly and this fallback
        // stops firing on its own; it is not a permanent crutch.
        val secure = firstStorageImage(apiBase, flavorHost)
            ?: firstStorageImage("https://$apex/api/v1", apex)

        // Deliberately NOT assumeTrue. If NEITHER catalog serves a product
        // image, something is wrong with the API and this test says so out loud
        // rather than skipping into a green result.
        assertTrue(
            "no product image on $flavorHost or $apex — cannot prove the " +
                "cleartext upgrade, and a silent skip here would report a pass " +
                "this test did not earn",
            secure != null,
        )

        cleartextImageUrl = secure!!.replaceFirst("https://", "http://")
        assertTrue(cleartextImageUrl.startsWith("http://"))
    }

    /** A real https storage asset advertised by [host]'s catalog, or null. */
    private fun firstStorageImage(apiBase: String, host: String): String? {
        val response = runCatching {
            OkHttpClient().newCall(
                Request.Builder()
                    .url("${apiBase.trimEnd('/')}/products?per_page=12")
                    .header("Accept", "application/json")
                    .build(),
            ).execute().use { it.body?.string().orEmpty() }
        }.getOrNull() ?: return null

        // Laravel json_encode escapes forward slashes as \/ — unescape first.
        return Regex(
            "https://${Regex.escape(host)}/storage/[A-Za-z0-9/_.-]+?\\.(?:png|jpg|jpeg|webp)",
        ).find(response.replace("\\/", "/"))?.value
    }

    /**
     * ⚠️ CACHES OFF, ALWAYS. Both tests use the same `http://` URL as request
     * data, and Coil keys its caches on the request data — not on the URL the
     * interceptor actually dials. The disk cache is a process-wide singleton
     * shared by every default-built ImageLoader, so the upgraded load in the
     * first test populates it under the CLEARTEXT key and the second test gets a
     * cache hit and never opens a socket.
     *
     * That is not hypothetical. It is what happened here: run together, the
     * "cleartext must be blocked" test reported `SuccessResult` and failed; run
     * alone against cleared app data, it passed. An order-dependent test that
     * proves the cache works while claiming to prove the network is blocked.
     */
    private fun request(url: String) = ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()

    @Test
    fun appImageLoader_upgradesAndLoadsCleartextImage() = runBlocking {
        val result = context.imageLoader.execute(request(cleartextImageUrl))
        assertTrue(
            "app ImageLoader must upgrade+load $cleartextImageUrl — got ${result::class.simpleName}",
            result is SuccessResult,
        )
    }

    @Test
    fun vanillaLoaderWithoutUpgrade_isBlockedOnCleartextImage() = runBlocking {
        // Reproduces the bug: a default loader (no https upgrade) is dropped by
        // Android's cleartext block, which is why the prod cards were blank.
        val result = ImageLoader.Builder(context).build().execute(request(cleartextImageUrl))
        assertTrue(
            "cleartext load should fail without the upgrade — got ${result::class.simpleName}",
            result is ErrorResult,
        )
    }
}
