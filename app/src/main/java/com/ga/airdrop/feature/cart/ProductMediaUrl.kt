package com.ga.airdrop.feature.cart

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.external.AffiliateAndMediaLinks

/**
 * Package-level bridge intentionally shared by My Cart and Order Summary.
 * The normalization algorithm itself has one owner: [AffiliateAndMediaLinks].
 */
internal fun validatedProductImageUrl(
    raw: String?,
    canonicalWebBase: String = BuildConfig.WEB_BASE_URL,
): String? = AffiliateAndMediaLinks.normalizeMediaUrl(raw, canonicalWebBase)
