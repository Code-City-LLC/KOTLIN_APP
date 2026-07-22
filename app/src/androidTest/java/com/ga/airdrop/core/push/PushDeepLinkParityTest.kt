package com.ga.airdrop.core.push

import android.content.Intent
import android.net.Uri
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.auth.AuthTokenStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PushDeepLinkParityTest {

    @Before
    fun clearPendingRoute() {
        AuthTokenStore.init(InstrumentationRegistry.getInstrumentation().targetContext)
        AuthTokenStore.save("push-route-test-token")
        PushDeepLink.clear()
        com.ga.airdrop.feature.shop.ShopCheckoutStore.pendingRef = null
    }

    @Test
    fun referAndInviteRoutesResolveToSwiftDestinations() {
        assertRoute("ReferView", Routes.REFER_A_FRIEND)
        assertRoute("Refer", Routes.REFER_A_FRIEND)
        assertRoute("ReferredFriendsView", Routes.REFERRED_FRIENDS)
        assertRoute("ReferredFriends", Routes.REFERRED_FRIENDS)
        assertRoute("referredFriends", Routes.REFERRED_FRIENDS)
        assertRoute("InviteFriendView", Routes.INVITE_FRIEND)
        assertRoute("InviteFriend", Routes.INVITE_FRIEND)
    }

    @Test
    fun paymentAndOrderDetailRoutesCarrySwiftReferenceIds() {
        assertRoute("PaymentPackageDetailsView", Routes.paymentPackageDetails("42"), referenceId = "42")
        assertRoute("ProductPaymentDetailsView", Routes.productPaymentDetails("43"), referenceId = "43")
        assertRoute("OrderDetailsView", Routes.orderDetails("44"), referenceId = "44")
        assertRoute("PaymentPackageDetailsView", Routes.PAYMENTS)
        assertRoute("ProductPaymentDetailsView", Routes.PAYMENTS)
        assertRoute("OrderDetailsView", Routes.ORDERS)
    }

    @Test
    fun packagePushPrefersRealPositiveIdOverPreviewTrackingReference() {
        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_ROUTE, "PackageDetailsView")
                .putExtra("package_id", "0042")
                .putExtra("tracking_code", "TRACK-PREVIEW-42"),
        )

        assertEquals(Routes.packageDetails("42"), PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun packagePushFallsBackToExactAliasAndRejectsInvalidOnlyIds() {
        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_ROUTE, "PackageDetailsView")
                .putExtra("package_id", "0")
                .putExtra("tracking_code", "TRACK-42"),
        )
        assertEquals(
            Routes.packageDetails("TRACK-42"),
            PushDeepLink.consume(AuthTokenStore.snapshot()),
        )

        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_ROUTE, "PackageDetailsView")
                .putExtra("package_id", "-7"),
        )
        assertEquals(Routes.PACKAGES, PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun foregroundDeepLinkExtraUsesThePackageDeepLinkRail() {
        PushDeepLink.capture(
            Intent().putExtra(
                AirdropMessagingService.EXTRA_DEEP_LINK,
                "airdrop://package/TRACK-42",
            ),
        )

        assertEquals(
            Routes.packageDetails("TRACK-42"),
            PushDeepLink.consume(AuthTokenStore.snapshot()),
        )
    }

    @Test
    fun packageDeepLinkWithoutPathUsesSiblingPositivePackageIdBeforePreviewAlias() {
        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_DEEP_LINK, "airdrop://package")
                .putExtra("package_id", "0042")
                .putExtra("tracking_code", "TRACK-PREVIEW-42"),
        )

        assertEquals(Routes.packageDetails("42"), PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun packageDeepLinkWithoutPathFallsBackToSiblingExactAlias() {
        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_DEEP_LINK, "airdrop://package")
                .putExtra("package_id", "0")
                .putExtra("tracking_code", "TRACK-ALIAS-42"),
        )

        assertEquals(
            Routes.packageDetails("TRACK-ALIAS-42"),
            PushDeepLink.consume(AuthTokenStore.snapshot()),
        )
    }

    @Test
    fun ownedHttpsPackageLinksAcceptExactHostAndSubdomainButRejectLookalikes() {
        PushDeepLink.capture(
            Intent().putExtra(
                AirdropMessagingService.EXTRA_DEEP_LINK,
                "https://airdropja.com/package/0042",
            ),
        )
        assertEquals(Routes.packageDetails("42"), PushDeepLink.consume(AuthTokenStore.snapshot()))

        PushDeepLink.capture(
            Intent().putExtra(
                AirdropMessagingService.EXTRA_DEEP_LINK,
                "https://app.airdropja.com/packages/TRACK-42",
            ),
        )
        assertEquals(
            Routes.packageDetails("TRACK-42"),
            PushDeepLink.consume(AuthTokenStore.snapshot()),
        )

        PushDeepLink.capture(
            Intent()
                .putExtra(
                    AirdropMessagingService.EXTRA_DEEP_LINK,
                    "https://notifications.airdropja.com/package",
                )
                .putExtra("package_id", "0043"),
        )
        assertEquals(Routes.packageDetails("43"), PushDeepLink.consume(AuthTokenStore.snapshot()))

        listOf(
            "https://airdropja.com.evil.example/package/42",
            "https://evilairdropja.com/packages/42",
            "https://example.com/package/42",
            "http://airdropja.com/package/42",
        ).forEach { externalLink ->
            PushDeepLink.clear()
            PushDeepLink.capture(
                Intent().putExtra(AirdropMessagingService.EXTRA_DEEP_LINK, externalLink),
            )
            assertNull(externalLink, PushDeepLink.consume(AuthTokenStore.snapshot()))
        }
    }

    @Test
    fun authenticatedPackageIntentRejectsDifferentKnownOwnerWithoutBlockingPublicRoutes() {
        AuthTokenStore.save("push-route-account-101", authenticatedAccountId = 101)

        listOf("user_id", "userId").forEach { ownerKey ->
            PushDeepLink.clear()
            PushDeepLink.capture(
                Intent()
                    .putExtra(AirdropMessagingService.EXTRA_ROUTE, "PackageDetailsView")
                    .putExtra("package_id", "42")
                    .putExtra(ownerKey, "202"),
            )
            assertNull(ownerKey, PushDeepLink.consume(AuthTokenStore.snapshot()))
        }

        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_ROUTE, "PackagesView")
                .putExtra("user_id", "202"),
        )
        assertEquals(Routes.PACKAGES, PushDeepLink.consume(AuthTokenStore.snapshot()))

        PushDeepLink.capture(
            Intent()
                .putExtra(AirdropMessagingService.EXTRA_ROUTE, "PackageDetailsView")
                .putExtra("package_id", "42")
                .putExtra("user_id", "not-an-id"),
        )
        assertEquals(Routes.packageDetails("42"), PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun auctionCheckoutRoutePreservesSwiftReferenceIdForCheckoutLoader() {
        assertRoute("AuctionProductCheckoutView", Routes.AUCTION_CHECKOUT, referenceId = "auction-22")
        assertEquals("auction-22", com.ga.airdrop.feature.shop.ShopCheckoutStore.pendingRef)
        com.ga.airdrop.feature.shop.ShopCheckoutStore.pendingRef = null
    }

    @Test
    fun stripeHostedCheckoutAliasesResolveToTheSinglePaymentOwners() {
        listOf("payment-success", "payment_success", "payment-complete", "payment_complete")
            .forEach { host ->
                assertEquals(
                    Routes.paymentReturn("cs_test_42"),
                    PushDeepLink.resolveUri(Uri.parse("airdrop://$host?session_id=cs_test_42")),
                )
            }

        listOf(
            "payment-cancelled", "payment_cancelled", "payment-cancel", "payment_cancel",
            "payment-cancelled-by-user", "payment_cancelled_by_user",
        ).forEach { host ->
            assertEquals(
                Routes.PAYMENT_CANCELLED,
                PushDeepLink.resolveUri(Uri.parse("airdrop://$host?session_id=cs_test_42")),
            )
        }
    }

    private fun assertRoute(route: String, expected: String, referenceId: String? = null) {
        PushDeepLink.capture(
            Intent().putExtra(AirdropMessagingService.EXTRA_ROUTE, route)
                .putExtra(AirdropMessagingService.EXTRA_REFERENCE_ID, referenceId)
        )
        assertEquals(expected, PushDeepLink.consume(AuthTokenStore.snapshot()))
    }
}
