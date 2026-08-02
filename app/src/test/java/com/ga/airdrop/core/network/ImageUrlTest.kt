package com.ga.airdrop.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [secureImageUrl] parity: prod (APP_URL=http://airdropja.com) hands the app
 * cleartext image URLs that Android's default cleartext block drops, so
 * Shop/Home/banner/order images go blank. The upgrade forces https on our own
 * host only, and never on look-alike or third-party hosts.
 *
 * ⚠️ Fixtures use the apex and `pre-staging.airdropja.com` ONLY. The retired
 * `app.` subdomain must not appear anywhere per Kemar's ruling, fixtures
 * included — a grep for it has to come back empty. The subdomain branch of
 * [secureImageUrl] is still covered, by a subdomain that actually exists.
 */
class ImageUrlTest {

    @Test
    fun `upgrades cleartext prod storage url to https`() {
        assertEquals(
            "https://airdropja.com/storage/products/QWV7Is2.png",
            secureImageUrl("http://airdropja.com/storage/products/QWV7Is2.png"),
        )
    }

    @Test
    fun `upgrades any live airdrop subdomain`() {
        assertEquals(
            "https://pre-staging.airdropja.com/storage/a.jpg",
            secureImageUrl("http://pre-staging.airdropja.com/storage/a.jpg"),
        )
    }

    @Test
    fun `upgrades cleartext host with explicit port`() {
        assertEquals(
            "https://airdropja.com:80/storage/a.jpg",
            secureImageUrl("http://airdropja.com:80/storage/a.jpg"),
        )
    }

    @Test
    fun `leaves already-secure https url untouched`() {
        val secure = "https://airdropja.com/storage/products/x.png"
        assertEquals(secure, secureImageUrl(secure))
    }

    @Test
    fun `leaves third-party cleartext host untouched`() {
        val other = "http://example.com/x.png"
        assertEquals(other, secureImageUrl(other))
    }

    @Test
    fun `does not match look-alike suffix host`() {
        val spoof = "http://airdropja.com.evil.com/x.png"
        assertEquals(spoof, secureImageUrl(spoof))
    }

    @Test
    fun `null and blank pass through`() {
        assertNull(secureImageUrl(null))
        assertEquals("", secureImageUrl(""))
    }

    @Test
    fun `relative path untouched`() {
        assertEquals("/storage/x.png", secureImageUrl("/storage/x.png"))
    }
}
