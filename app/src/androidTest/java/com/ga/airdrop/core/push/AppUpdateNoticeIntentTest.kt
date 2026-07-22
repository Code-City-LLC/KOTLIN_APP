package com.ga.airdrop.core.push

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateNoticeIntentTest {
    @Test
    fun updateIntentUsesOnlyFixedPlayListing() {
        val intent = googlePlayUpdateIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(GOOGLE_PLAY_UPDATE_URL, intent.data.toString())
    }
}
