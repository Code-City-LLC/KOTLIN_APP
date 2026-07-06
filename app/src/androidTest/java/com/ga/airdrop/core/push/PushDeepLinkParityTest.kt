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
        assertRoute("ReferredFriendsView", Routes.REFERRED_FRIENDS)
        assertRoute("ReferredFriends", Routes.REFERRED_FRIENDS)
        assertRoute("referredFriends", Routes.REFERRED_FRIENDS)
        assertRoute("InviteFriendView", Routes.INVITE_FRIEND)
        assertRoute("InviteFriend", Routes.INVITE_FRIEND)
    }

    private fun assertRoute(route: String, expected: String) {
        PushDeepLink.capture(
            Intent().putExtra(AirdropMessagingService.EXTRA_ROUTE, route)
        )
        assertEquals(expected, PushDeepLink.consume())
    }
}
