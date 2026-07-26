package com.ga.airdrop

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.ga.airdrop.core.push.NotificationBadgeGateway
import com.ga.airdrop.core.push.NotificationBadgeSync

/**
 * Stops instrumentation tests from making real authenticated network calls
 * simply by saving a token.
 *
 * ⚠️ WHY THIS IS AT THE RUNNER LEVEL AND NOT IN A @Before.
 *
 * `AuthTokenStore.save()` calls
 * `NotificationBadgeSync.onAuthenticatedSessionChanged(...)`, so **saving a
 * token issues a live `GET /user/notifications`**. With the fake bearers tests
 * use, that 401s → the interceptor refreshes → the refresh 401s → the session
 * is torn down mid-test. The damage then surfaces somewhere unrelated, because
 * anything reading state behind `runWhileCurrentSession(...)` starts returning
 * null, and null reads as "off"/"absent" rather than "unreadable".
 *
 * I first fixed this in ONE test class. That was the wrong level: **eight**
 * androidTest classes call `AuthTokenStore.save(...)`, so seven were still
 * exposed and the next failure surfaced in `PushDeepLinkParityTest` — a class
 * nobody had touched, failing on a refer/invite route assertion, for reasons
 * entirely invisible from its own source.
 *
 * Fixing it per-class means every future test that saves a token is one more
 * chance to lose a day to this. The runner installs the no-op once, before any
 * test runs, so the trap cannot be stepped in again.
 *
 * This does NOT weaken any assertion: no test asserts on the unread badge
 * count, and any that wants to can install its own gateway. What it removes is
 * an unrequested network call that no test asked for and none can see.
 */
class AirdropTestRunner : AndroidJUnitRunner() {

    override fun callApplicationOnCreate(app: Application?) {
        // Before any test body runs, and before any AuthTokenStore.save().
        NotificationBadgeSync.gateway = NotificationBadgeGateway { null }
        super.callApplicationOnCreate(app)
    }

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        val application = super.newApplication(cl, className, context)
        NotificationBadgeSync.gateway = NotificationBadgeGateway { null }
        return application
    }
}
