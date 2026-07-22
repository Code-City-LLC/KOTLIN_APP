package com.ga.airdrop.core.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffiliateAndMediaLinksTest {

    @Test
    fun `media upgrades http and resolves root and path relative URLs`() {
        assertEquals(
            "https://cdn.airdropja.test/products/mac.png",
            AffiliateAndMediaLinks.normalizeMediaUrl(
                " http://cdn.airdropja.test/products/mac.png ",
                WEB_BASE,
            ),
        )
        assertEquals(
            "https://airdropja.test/storage/products/mac.png",
            AffiliateAndMediaLinks.normalizeMediaUrl("/storage/products/mac.png", WEB_BASE),
        )
        assertEquals(
            "https://airdropja.test/products/mac.png?size=2x",
            AffiliateAndMediaLinks.normalizeMediaUrl("products/mac.png?size=2x", WEB_BASE),
        )
    }

    @Test
    fun `media rejects credentials malformed hosts network paths and unsafe schemes`() {
        listOf(
            "https://user:secret@cdn.airdropja.test/mac.png",
            "https:///missing-host.png",
            "//outside.example/mac.png",
            "android.resource://com.ga.airdrop/drawable/mac",
            "javascript:alert(1)",
        ).forEach { raw ->
            assertNull(raw, AffiliateAndMediaLinks.normalizeMediaUrl(raw, WEB_BASE))
        }
        assertNull(
            AffiliateAndMediaLinks.normalizeMediaUrl(
                "/storage/mac.png",
                "https://user:secret@airdropja.test",
            ),
        )
    }

    @Test
    fun `full Amazon marketplace links require attribution and force https`() {
        assertEquals(
            "https://www.amazon.com/dp/B0TEST?tag=airdrop00-20",
            AffiliateAndMediaLinks.validateAmazonAffiliateUrl(
                " http://www.amazon.com/dp/B0TEST?tag=airdrop00-20 ",
            ),
        )
        assertEquals(
            "https://smile.amazon.co.uk/dp/B0TEST?TAG=partner-21",
            AffiliateAndMediaLinks.validateAmazonAffiliateUrl(
                "smile.amazon.co.uk/dp/B0TEST?TAG=partner-21",
            ),
        )
        assertEquals(
            "https://amazon.co.jp/dp/B0TEST?tag=partner%2D22",
            AffiliateAndMediaLinks.validateAmazonAffiliateUrl(
                "amazon.co.jp/dp/B0TEST?tag=partner%2D22",
            ),
        )

        assertNull(AffiliateAndMediaLinks.validateAmazonAffiliateUrl("amazon.com/dp/B0TEST"))
        assertNull(AffiliateAndMediaLinks.validateAmazonAffiliateUrl("amazon.com/dp/B0TEST?tag="))
        assertNull(AffiliateAndMediaLinks.validateAmazonAffiliateUrl("amazon.com/dp/B0TEST?tag=%20"))
    }

    @Test
    fun `all supported Amazon marketplace roots accept subdomains with a non-empty tag`() {
        SUPPORTED_MARKETPLACES.forEach { root ->
            val expected = "https://www.$root/dp/B0TEST?tag=partner"
            assertEquals(
                root,
                expected,
                AffiliateAndMediaLinks.validateAmazonAffiliateUrl(
                    "http://www.$root/dp/B0TEST?tag=partner",
                ),
            )
        }
    }

    @Test
    fun `Amazon short links allow encoded attribution without a tag query`() {
        assertEquals(
            "https://a.co/d/abc%2F123?ref_=abc%2Bdef",
            AffiliateAndMediaLinks.validateAmazonAffiliateUrl(
                "http://a.co/d/abc%2F123?ref_=abc%2Bdef",
            ),
        )
        assertEquals(
            "https://amzn.to/4TEST",
            AffiliateAndMediaLinks.validateAmazonAffiliateUrl("amzn.to/4TEST"),
        )
    }

    @Test
    fun `Amazon validation rejects userinfo lookalikes and non-web schemes`() {
        listOf(
            "https://user:secret@amazon.com/dp/B0TEST?tag=partner",
            "https://amazon.com@evil.example/dp/B0TEST?tag=partner",
            "https://amazon.com.evil.example/dp/B0TEST?tag=partner",
            "https://notamazon.com/dp/B0TEST?tag=partner",
            "https://sub.a.co/d/B0TEST",
            "ftp://amazon.com/dp/B0TEST?tag=partner",
            "javascript:amazon.com",
        ).forEach { raw ->
            assertNull(raw, AffiliateAndMediaLinks.validateAmazonAffiliateUrl(raw))
        }
    }

    private companion object {
        const val WEB_BASE = "https://airdropja.test"
        val SUPPORTED_MARKETPLACES = listOf(
            "amazon.com",
            "amazon.ca",
            "amazon.com.mx",
            "amazon.com.br",
            "amazon.co.uk",
            "amazon.de",
            "amazon.fr",
            "amazon.it",
            "amazon.es",
            "amazon.nl",
            "amazon.com.be",
            "amazon.se",
            "amazon.pl",
            "amazon.com.tr",
            "amazon.in",
            "amazon.co.jp",
            "amazon.sg",
            "amazon.com.au",
            "amazon.ae",
            "amazon.sa",
            "amazon.eg",
        )
    }
}
