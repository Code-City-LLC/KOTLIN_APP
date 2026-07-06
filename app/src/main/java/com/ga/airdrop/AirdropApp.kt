package com.ga.airdrop

import android.app.Application
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.prefs.DeliveryDefaultsStore
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.shipments.ShipmentsRepoBinding
import com.ga.airdrop.feature.shop.ShopRepoBinding

class AirdropApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AuthTokenStore.init(this)
        ThemeController.init(this)
        CartStore.init(this)
        DeliveryDefaultsStore.init(this)
        ShopRepoBinding.install()
        ShipmentsRepoBinding.install(cacheDir)
    }
}
