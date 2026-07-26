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
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
        // Report crashes on real builds only; debug crashes stay off the prod
        // Crashlytics dashboard (project airdrop-app-b9423).
        //
        // ⚠️ GUARDED BECAUSE onCreate RUNS IN EVERY PROCESS, NOT JUST THE MAIN ONE.
        //
        // FirebaseCrashlytics.getInstance() throws
        // "Default FirebaseApp is not initialized in this process" when the
        // ContentProvider that normally bootstraps Firebase has not run for
        // that process. `:session_restore_probe` is exactly such a process, and
        // this line crashed it on startup — taking
        // PushDeepLinkSessionBindingTest.processRestoreUnderDifferentAccountCannotReplay
        // down with it. Reproduced on main at e5f4bd3, so it predates the
        // branches it was blocking. Root cause identified by BrightHarbor.
        //
        // Checking for an initialised FirebaseApp rather than comparing process
        // names: the condition we actually depend on is Firebase being ready,
        // and a name check would silently rot the moment a process is renamed
        // or added.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
        // The HTTP layer has no Context; bind the canonical sweep here so a
        // rejected bearer can run it. See ForcedSignOutSweep for why this is a
        // seam and not a direct call.
        com.ga.airdrop.core.session.ForcedSignOutSweep.handler = {
            com.ga.airdrop.core.session.clearLocalUserSession(this)
        }
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
