package com.ga.airdrop.core.network

/**
 * Returns [raw] with an `http://` scheme upgraded to `https://` when it points
 * at an AirDrop host (`airdropja.com` or any subdomain); otherwise returns
 * [raw] unchanged.
 *
 * The production backend (`APP_URL=http://app.airdropja.com`) emits cleartext
 * storage URLs for product / banner / order images. Android blocks cleartext
 * HTTP by default (targetSdk ≥ 28), so without this upgrade Coil silently drops
 * those loads and the Shop auction/featured cards, Home shortlist, Promotions
 * banners and order thumbnails render blank on the prod build. The https
 * endpoint serves the identical asset — staging already returns https URLs for
 * the same files.
 *
 * https URLs and hosts we don't control are returned untouched: we only force
 * the scheme on our own domain, and the suffix check requires a leading dot so
 * a look-alike host (`airdropja.com.evil.com`) is never matched.
 */
fun secureImageUrl(raw: String?): String? {
    if (raw == null) return null
    if (!raw.startsWith("http://", ignoreCase = true)) return raw
    val afterScheme = raw.substring("http://".length)
    val host = afterScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore(':')
        .lowercase()
    val isAirdropHost = host == "airdropja.com" || host.endsWith(".airdropja.com")
    return if (isAirdropHost) "https://$afterScheme" else raw
}
