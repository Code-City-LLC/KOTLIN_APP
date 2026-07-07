package com.ga.airdrop.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [secureImageUrl] parity: prod (APP_URL=http://app.airdropja.com) hands the
 * app cleartext image URLs that Android's default cleartext block drops, so
 * Shop/Home/banner/order images go blank. The upgrade forces https on our own
 * host only, and never on look-alike or third-party hosts.
 */
class ImageUrlTest {

    @Test
    fun `upgrades cleartext prod storage url to https`() {
        assertEquals(
            "https://app.airdropja.com/storage/products/QWV7Is2.png",
            secureImageUrl("http://app.airdropja.com/storage/products/QWV7Is2.png"),
        )
    }

    @Test
    fun `upgrades apex airdrop host`() {
        assertEquals(
            "https://airdropja.com/storage/a.jpg",
            secureImageUrl("http://airdropja.com/storage/a.jpg"),
        )
    }

    @Test
    fun `upgrades cleartext host with explicit port`() {
        assertEquals(
            "https://app.airdropja.com:80/storage/a.jpg",
            secureImageUrl("http://app.airdropja.com:80/storage/a.jpg"),
        )
    }

    @Test
    fun `leaves already-secure https url untouched`() {
        val secure = "https://app.airdropja.com/storage/products/x.png"
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
