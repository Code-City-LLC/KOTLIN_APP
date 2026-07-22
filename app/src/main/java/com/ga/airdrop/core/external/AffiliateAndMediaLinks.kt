package com.ga.airdrop.core.external

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * One credential-free boundary for backend media and Amazon affiliate links.
 *
 * The backend may return absolute HTTP media or web-root-relative media. Both
 * are safe to render only after they resolve to a credential-free HTTPS URL.
 * Amazon links additionally stay within a known marketplace (or an approved
 * Amazon short-link host), with attribution required on full marketplace URLs.
 */
object AffiliateAndMediaLinks {
    const val AMAZON_ASSOCIATE_DISCLOSURE =
        "As an Amazon Associate, AirDrop earns from qualifying purchases."

    private val amazonMarketplaceRoots = setOf(
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
    private val amazonShortLinkHosts = setOf("a.co", "amzn.to")

    fun normalizeMediaUrl(raw: String?, canonicalWebBase: String): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        val parsed = parseHttpReference(value) ?: return null
        val absolute = when {
            parsed.isAbsolute -> parsed
            parsed.rawAuthority != null -> return null
            else -> {
                val base = parseHttpReference(canonicalWebBase.trim()) ?: return null
                if (!base.isAbsolute || base.host.isNullOrBlank() || base.userInfo != null) return null
                base.resolve(parsed)
            }
        }

        if (absolute.host.isNullOrBlank() || absolute.userInfo != null) return null
        return forceHttps(absolute)
    }

    fun validateAmazonAffiliateUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        val withScheme = when {
            value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true) -> value
            URI_SCHEME.matchesAt(value, 0) -> return null
            else -> "https://$value"
        }
        val uri = parseHttpReference(withScheme) ?: return null
        if (!uri.isAbsolute || uri.host.isNullOrBlank() || uri.userInfo != null) return null

        val host = uri.host.lowercase()
        val isShortLink = host in amazonShortLinkHosts
        val marketplaceRoot = amazonMarketplaceRoots.firstOrNull { root ->
            host == root || host.endsWith(".$root")
        }
        if (!isShortLink && marketplaceRoot == null) return null
        if (!isShortLink && !hasNonEmptyAmazonTag(uri.rawQuery)) return null

        return forceHttps(uri)
    }

    private fun parseHttpReference(value: String): URI? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme
        if (scheme != null &&
            !scheme.equals("http", ignoreCase = true) &&
            !scheme.equals("https", ignoreCase = true)
        ) {
            return null
        }
        return uri
    }

    private fun forceHttps(uri: URI): String {
        val rendered = uri.toASCIIString()
        val scheme = uri.scheme.orEmpty()
        return if (scheme.equals("https", ignoreCase = true)) {
            if (rendered.startsWith("https://")) rendered
            else "https://${rendered.substringAfter("://")}"
        } else {
            "https://${rendered.substringAfter("://")}"
        }
    }

    private fun hasNonEmptyAmazonTag(rawQuery: String?): Boolean =
        rawQuery
            ?.split('&')
            ?.any { item ->
                val separator = item.indexOf('=')
                if (separator < 0) return@any false
                val key = decodeQueryComponent(item.substring(0, separator))
                val value = decodeQueryComponent(item.substring(separator + 1))
                key.equals("tag", ignoreCase = true) && value.isNotBlank()
            }
            ?: false

    private fun decodeQueryComponent(value: String): String =
        runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault("")

    private val URI_SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*:")
}
