package com.ga.airdrop

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.TextSizeController
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.network.HttpsImageInterceptor
import com.ga.airdrop.core.prefs.DeliveryDefaultsStore
import com.ga.airdrop.core.security.BiometricGate
import com.ga.airdrop.feature.calculator.CalculatorHistory
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.dropalert.DropAlertPreset
import com.ga.airdrop.feature.shipments.PackagesSortStore
import com.ga.airdrop.feature.shipments.ShipmentsRepoBinding
import com.ga.airdrop.feature.shop.ShopRecentSearches
import com.ga.airdrop.feature.shop.ShopRepoBinding
import okhttp3.OkHttpClient

class AirdropApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AuthTokenStore.init(this)
        ThemeController.init(this)
        TextSizeController.init(this)
        CartStore.init(this)
        DeliveryDefaultsStore.init(this)
        BiometricGate.init(this)
        CalculatorHistory.init(this)
        com.ga.airdrop.core.prefs.ExchangeRateStore.init(this)
        com.ga.airdrop.core.push.PushDeepLink.init(this)
        com.ga.airdrop.core.push.PushRegistrar.init(this)
        DropAlertPreset.init(this)
        ShopRecentSearches.init(this)
        PackagesSortStore.init(this)
        ShopRepoBinding.install()
        ShipmentsRepoBinding.install(cacheDir)
    }

    // Coil's app-wide image loader. Its OkHttpClient carries [HttpsImageInterceptor]
    // so every remote image (Shop cards, Home shortlist, Promotions banners, order
    // thumbnails) upgrades cleartext http:// AirDrop URLs to https:// before the
    // socket opens — otherwise the prod build, whose backend emits http:// storage
    // URLs, renders them blank under Android's default cleartext block.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(HttpsImageInterceptor())
                    .build()
            }
            .build()
}
