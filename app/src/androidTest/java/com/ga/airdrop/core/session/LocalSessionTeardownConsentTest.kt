package com.ga.airdrop.core.session

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.push.PushDeepLink
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSessionTeardownConsentTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        AuthTokenStore.init(context)
        PushDeepLink.init(context)
        LiveAgentChatConsentStore.clear(context)
    }

    @After
    fun tearDown() {
        LiveAgentChatConsentStore.clear(context)
    }

    @Test
    fun liveAgentConsentDoesNotCrossAccountSessionBoundary() {
        LiveAgentChatConsentStore.accept(context)
        assertTrue(LiveAgentChatConsentStore.isAccepted(context))

        clearLocalUserSession(context)

        assertFalse(LiveAgentChatConsentStore.isAccepted(context))
    }
}
