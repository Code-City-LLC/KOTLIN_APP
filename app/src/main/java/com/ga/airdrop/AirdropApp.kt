package com.ga.airdrop

import android.app.Application
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.ThemeController

class AirdropApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AuthTokenStore.init(this)
        ThemeController.init(this)
    }
}
