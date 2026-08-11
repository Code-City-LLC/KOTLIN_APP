package com.ga.airdrop.core.push

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ActivityScenario
import com.ga.airdrop.MainActivity
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
    fun auctionCheckoutRoutePreservesSwiftReferenceIdForCheckoutLoader() {
        assertRoute("AuctionProductCheckoutView", Routes.AUCTION_CHECKOUT, referenceId = "auction-22")
        assertEquals("auction-22", com.ga.airdrop.feature.shop.ShopCheckoutStore.pendingRef)
        com.ga.airdrop.feature.shop.ShopCheckoutStore.pendingRef = null
    }

    @Test
    fun appUpdatePushTappedLoggedOutSurvivesUntilAuthentication() {
        AuthTokenStore.clear()
        PushDeepLink.capture(
            Intent().putExtra(AirdropMessagingService.EXTRA_ROUTE, "AppUpdateView"),
        )

        assertNull("a logged-out account cannot consume the route yet", PushDeepLink.consume())

        AuthTokenStore.save("push-route-app-update-token")
        assertEquals(Routes.APP_UPDATE, PushDeepLink.consume(AuthTokenStore.snapshot()))
    }

    @Test
    fun mainActivityRecreationDoesNotReplayConsumedLaunchIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        AuthTokenStore.clear()
        PushDeepLink.clear()
        val launchIntent = Intent(context, MainActivity::class.java)
            .putExtra(AirdropMessagingService.EXTRA_ROUTE, "AppUpdateView")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val scenario = ActivityScenario.launch<MainActivity>(launchIntent)
        try {
            assertEquals(Routes.APP_UPDATE, PushDeepLink.pending.value?.route)
            PushDeepLink.clear()

            scenario.recreate()

            assertNull("configuration recreation replayed the launch route", PushDeepLink.pending.value)
        } finally {
            scenario.close()
            PushDeepLink.clear()
            AuthTokenStore.save("push-route-test-token")
        }
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
