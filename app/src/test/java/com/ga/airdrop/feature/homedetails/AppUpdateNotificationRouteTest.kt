package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.core.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "App update available" was the one broadcast whose entire purpose is to get a
 * customer onto a new build, and on Android it did nothing at all when tapped.
 *
 * Laravel sends it — `AppUpdateBroadcastCommand:118` (`'route' => 'AppUpdateView'`)
 * and `NotificationType::APP_UPDATE_AVAILABLE => 'AppUpdateView'`.
 * iOS acts on it — `FigmaNotificationsListViewController:839` →
 * `AppDelegate.swift:1736`, which opens the App Store.
 * Android had **zero** references to `AppUpdateView` anywhere in the source
 * tree, so the resolver returned null and the tap was swallowed silently.
 *
 * It resolves to a sentinel rather than a NavHost destination because the action
 * leaves the app. The sentinel is only safe while every consumer guards on it,
 * so these tests pin the guard as much as the mapping.
 */
class AppUpdateNotificationRouteTest {

    @Test
    fun `the route Laravel actually sends resolves to something`() {
        val route = resolveNotificationRoute("AppUpdateView", null)

        assertNotNull(
            "Laravel broadcasts route=AppUpdateView; resolving it to null is why " +
                "the notification did nothing when tapped",
            route,
        )
        // ⚠️ Target CHANGED on purpose (2026-08-01, Kemar: "build the
        // AppUpdateView screen the existing routes already point at").
        // It used to be EXTERNAL_APP_UPDATE, which jumped the customer straight
        // out to the Play listing with no version, no "What's New" and no way
        // to decline. It now lands on the in-app screen; that screen's
        // "Update Now" button is what fires EXTERNAL_APP_UPDATE.
        assertEquals(Routes.APP_UPDATE, route)
        assertFalse(
            "the app-update route must now NAVIGATE, not be intercepted as external",
            Routes.isExternalNotificationRoute(route),
        )
    }

    /** iOS accepts both spellings (AppDelegate.swift:979-980), so we must too. */
    @Test
    fun `the AppUpdateScreen spelling resolves identically`() {
        assertEquals(Routes.APP_UPDATE, resolveNotificationRoute("AppUpdateScreen", null))
    }

    /**
     * Some payloads carry only `type`, not `route`. Laravel's enum name is
     * app_update_available; the other two are the strings iOS already sniffs for.
     */
    @Test
    fun `the notification TYPE also resolves when no route is present`() {
        listOf("app_update_available", "app_update", "force_update").forEach { type ->
            assertEquals(
                "type=$type must resolve — not every payload carries an explicit route",
                "AppUpdateView",
                routeNameForNotificationType(type),
            )
        }
    }

    /**
     * ⚠️ THE LOAD-BEARING ASSERTION. The sentinel is not a NavHost destination;
     * `navigate()` on it throws. It is only safe because both tap handlers check
     * this first. If the guard is ever loosened, an app-update tap goes from
     * doing nothing to CRASHING, which is strictly worse.
     */
    @Test
    fun `the sentinel is recognised as external, not navigable`() {
        assertTrue(Routes.isExternalNotificationRoute(Routes.EXTERNAL_APP_UPDATE))
        assertTrue(Routes.EXTERNAL_APP_UPDATE.startsWith("external:"))
    }

    /** Real destinations must NOT be diverted into the external branch. */
    @Test
    fun `ordinary routes are not treated as external`() {
        listOf(Routes.HOME, Routes.SHIPMENTS, Routes.PAYMENTS, Routes.PROFILE, Routes.SETTINGS)
            .forEach { assertFalse("$it must still navigate normally", Routes.isExternalNotificationRoute(it)) }
        assertFalse(Routes.isExternalNotificationRoute(null))
        assertFalse(Routes.isExternalNotificationRoute(""))
    }

    /** The change must not have disturbed the routes that already worked. */
    @Test
    fun `existing notification routes still resolve as before`() {
        assertEquals(Routes.SETTINGS, resolveNotificationRoute("SettingsView", null))
        assertEquals(Routes.SHIPPING_RATES, resolveNotificationRoute("ShippingRatesView", null))
        assertEquals(Routes.PROFILE, resolveNotificationRoute("ProfileView", null))
        assertEquals(null, resolveNotificationRoute("NoSuchScreenView", null))
    }
}
