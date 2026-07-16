package com.ga.airdrop.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVisualContractTest {
    @Test
    fun warehouseSurfaceKeepsOnlySlightTransparency() {
        assertEquals(0.95f, HOME_WAREHOUSE_SURFACE_ALPHA, 0.001f)
        assertTrue(HOME_WAREHOUSE_SURFACE_ALPHA < 1f)
        assertTrue(HOME_WAREHOUSE_SURFACE_ALPHA >= 0.90f)
    }

    @Test
    fun referAFriendKeepsVisibleClearanceAboveBottomChrome() {
        assertTrue(HOME_BOTTOM_CLEARANCE_DP >= 140)
    }
}
