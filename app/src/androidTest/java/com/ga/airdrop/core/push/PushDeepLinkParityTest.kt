package com.ga.airdrop.core.push

import android.content.Intent
import com.ga.airdrop.core.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PushDeepLinkParityTest {

    @Before
    fun clearPendingRoute() {
        PushDeepLink.consume()
    }

    @Test
    fun referAndInviteRoutesResolveToSwiftDestinations() {
        assertRoute("ReferView", Routes.REFER_A_FRIEND)
        assertRoute("Refer", Routes.REFER_A_FRIEND)
        assertRoute("ReferredFriendsView", Routes.REFER_A_FRIEND)
        assertRoute("InviteFriendView", Routes.INVITE_FRIEND)
        assertRoute("InviteFriend", Routes.INVITE_FRIEND)
    }

    @Test
    fun shipmentDetailRoutesResolveToSwiftDestinations() {
        assertRoute("PackageDetailsView", Routes.packageDetails("101"), referenceId = "101")
        assertRoute("PaymentPackageDetailsView", Routes.paymentPackageDetails("201"), referenceId = "201")
        assertRoute("ProductPaymentDetailsView", Routes.productPaymentDetails("202"), referenceId = "202")
        assertRoute("OrderDetailsView", Routes.orderDetails("301"), referenceId = "301")
    }

    @Test
    fun invoiceViewerRouteCarriesEncodedSwiftInvoiceUrl() {
        val invoiceUrl = "https://pre-staging.airdropja.com/storage/invoices/Invoice 100.pdf"
        assertRoute(
            "InvoiceViewerScreen",
            Routes.invoiceViewer(invoiceUrl, "Invoice"),
            referenceId = invoiceUrl,
        )
    }

    private fun assertRoute(route: String, expected: String, referenceId: String? = null) {
        PushDeepLink.capture(
            Intent().putExtra(AirdropMessagingService.EXTRA_ROUTE, route)
                .apply {
                    referenceId?.let {
                        putExtra(AirdropMessagingService.EXTRA_REFERENCE_ID, it)
                    }
                }
        )
        assertEquals(expected, PushDeepLink.consume())
    }
}
