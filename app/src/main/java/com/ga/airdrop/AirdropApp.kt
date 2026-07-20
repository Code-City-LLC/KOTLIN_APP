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
import com.ga.airdrop.feature.cart.CheckoutFlowStore
import com.ga.airdrop.feature.cart.SavedForLaterStore
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.feature.dropalert.DropAlertPreset
import com.ga.airdrop.feature.shipments.PackagesSortStore
import com.ga.airdrop.feature.shipments.ShipmentsRepoBinding
import com.ga.airdrop.feature.shop.ShopRecentSearches
import com.ga.airdrop.feature.shop.ShopRepoBinding
import com.ga.airdrop.feature.shop.ShopRepoProvider
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AirdropApp : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // First: crashes during the rest of startup must still be captured.
        com.ga.airdrop.core.diagnostics.CrashCapture.install(this)
        // Checkout funnel diagnostics (Swift 89fbb11): arm the buffer and
        // flush it whenever the last started activity stops (app background).
        com.ga.airdrop.core.analytics.AirdropFunnel.install()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities += 1
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    com.ga.airdrop.core.analytics.AirdropFunnel.flush()
                }
            }
            override fun onActivityCreated(
                activity: android.app.Activity,
                savedInstanceState: android.os.Bundle?,
            ) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(
                activity: android.app.Activity,
                outState: android.os.Bundle,
            ) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        AuthTokenStore.init(this)
        ThemeController.init(this)
        TextSizeController.init(this)
        CartStore.init(this)
        SavedForLaterStore.init(this)
        CheckoutFlowStore.init(this)
        applicationScope.launch {
            AuthTokenStore.snapshotFlow
                .map { snapshot ->
                    snapshot.sessionId?.takeIf { snapshot.token != null }
                        ?.let { AuthenticatedSessionOwner(it, snapshot.accountId) }
                }
                .distinctUntilChanged()
                .collect { owner ->
                    CartStore.onAuthenticatedSessionChanged(owner)
                    SavedForLaterStore.onAuthenticatedSessionChanged(owner)
                    CheckoutFlowStore.onAuthenticatedSessionChanged(owner)
                }
        }
        DeliveryDefaultsStore.init(this)
        com.ga.airdrop.core.prefs.DeliverySettingsCache.init(this)
        BiometricGate.init(this)
        com.ga.airdrop.core.security.BiometricLoginVault.init(this)
        CalculatorHistory.init(this)
        com.ga.airdrop.core.prefs.ExchangeRateStore.init(this)
        com.ga.airdrop.core.push.PushDeepLink.init(this)
        com.ga.airdrop.core.push.PushRegistrar.init(this)
        DropAlertPreset.init(this)
        ShopRecentSearches.init(this)
        PackagesSortStore.init(this)
        ShopRepoBinding.install()
        ShipmentsRepoBinding.install(cacheDir)
        com.ga.airdrop.feature.shop.NotifyInStockStore.init(this)
        // Ship any crash captured on a previous run (delete on acceptance).
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                com.ga.airdrop.core.diagnostics.CrashCapture.flush { payload ->
                    com.ga.airdrop.core.network.ApiClient.service.reportCrash(payload).code()
                }
            }
        }
        // Notify-when-in-stock poll (Swift pollSubscribedProducts): a watched
        // product back in stock fires a local notification, then unsubscribes.
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                com.ga.airdrop.feature.shop.NotifyInStockStore.poll(
                    snapshot = {
                        // One pass over the live product lists (Swift polls the
                        // same auctionProducts feed); build id → stock.
                        val products = ShopRepoProvider.products
                        val auction = products.auctionProducts(page = 1, perPage = 50)
                            .getOrDefault(emptyList())
                        val featured = products.featuredProducts(page = 1, perPage = 50)
                            .getOrDefault(emptyList())
                        (auction + featured).associate { p ->
                            p.id to com.ga.airdrop.feature.shop.NotifyInStockStore.StockSnapshot(
                                inventory = p.inventory,
                                title = p.title,
                            )
                        }
                    },
                    notify = { id, title ->
                        com.ga.airdrop.feature.shop.notifyBackInStock(this@AirdropApp, id, title)
                    },
                )
            }
        }
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
