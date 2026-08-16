package com.ga.airdrop.core.analytics

import android.content.Context
import android.os.Bundle
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger

/**
 * Meta App Events (Kemar 2026-08-16, true-conversion app path): reports the
 * in-app signup back to the exact ad that drove the install, so app-only
 * journeys stop being invisible to attribution. The SDK auto-logs installs
 * and app-opens from the manifest config; this adds the one product event
 * that matters — completed registration — tagged with the self-reported
 * channel so paid-vs-organic can be compared server-side.
 */
object MetaEvents {

    fun logCompletedRegistration(context: Context, hearAboutUs: String) {
        runCatching {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_REGISTRATION_METHOD, hearAboutUs)
            }
            AppEventsLogger.newLogger(context.applicationContext)
                .logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION, params)
        }
    }
}
