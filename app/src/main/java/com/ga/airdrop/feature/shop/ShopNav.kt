package com.ga.airdrop.feature.shop

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartScreen
import com.ga.airdrop.feature.delivery.DeliveryMethodScreen

/**
 * SHOP + CART feature graph.
 *
 * ORCHESTRATOR WIRING (no new route constants needed — all exist in
 * Routes.kt):
 *  1. In core/navigation/AppRoot.kt `mainGraph`, replace the
 *     `composable(Routes.SHOP) { PlaceholderScreen("Shop") }` entry with a
 *     call to `shopGraph(navController)` (it registers Routes.SHOP itself).
 *  2. Call `CartStore.init(applicationContext)` during app startup and
 *     `CartStore.clear()` in the logout hygiene path.
 *  3. Bind [ShopRepoProvider.products] / [ShopRepoProvider.checkout] to the
 *     com.ga.airdrop.data implementations (see RECONCILE notes in
 *     ShopData.kt for exact endpoints/fields).
 */
fun NavGraphBuilder.shopGraph(navController: NavHostController) {

    // Shop tab root — Figma 40001846:53519.
    composable(Routes.SHOP) {
        ShopScreen(onNavigate = { navController.navigate(it) })
    }

    // Auction full list — Figma 40001846:54117.
    composable(Routes.AUCTION) {
        AuctionScreen(
            onNavigate = { navController.navigate(it) },
            onBack = { navController.popBackStack() },
        )
    }

    // Feature Products full list — Figma 40001846:54396.
    composable(Routes.FEATURED_PRODUCTS) {
        FeaturedProductsScreen(
            onNavigate = { navController.navigate(it) },
            onBack = { navController.popBackStack() },
        )
    }

    // Product details — Figma 40002072:24025 — args: slug + featured.
    composable(
        route = Routes.AUCTION_PRODUCT_DETAILS,
        arguments = listOf(
            navArgument("slug") { type = NavType.StringType },
            navArgument("featured") {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
    ) { entry ->
        val slug = entry.arguments?.getString("slug").orEmpty()
        val featured = entry.arguments?.getBoolean("featured") ?: false
        AuctionProductDetailsScreen(
            slug = slug,
            featured = featured,
            onNavigate = { navController.navigate(it) },
            onBack = { navController.popBackStack() },
        )
    }

    // Auction checkout — product handed over via ShopCheckoutStore
    // (route carries no argument, Swift FigmaRouteResolver parity).
    composable(Routes.AUCTION_CHECKOUT) {
        AuctionCheckoutScreen(
            onBack = { navController.popBackStack() },
            onCheckoutOpened = {
                // Swift pops checkout + the details screen that pushed it.
                navController.popBackStack()
                navController.popBackStack()
            },
        )
    }

    // My Cart — Figma 40008284:26547.
    composable(Routes.CART) {
        CartScreen(
            onBack = { navController.popBackStack() },
            onShopNow = {
                // RN parity: switch to the Shop tab root rather than pushing
                // a fresh ShopView (Swift bug-fix 2026-05-22).
                navController.navigate(Routes.SHOP) {
                    popUpTo(Routes.HOME)
                    launchSingleTop = true
                }
            },
            onNavigate = {
                // launchSingleTop: a rapid double-tap of Make Payment must not
                // push two Delivery Method entries (verify finding, 2026-07-06).
                navController.navigate(it) { launchSingleTop = true }
            },
        )
    }

    // Delivery Method — Figma 40008740:28263, Swift
    // FigmaDeliveryMethodViewController. Cart "Make Payment" lands here;
    // the currency popup then front-runs the cart's Stripe checkout
    // (docs/PARITY_GAP_SPECS.md §4 currency-branch deviation).
    composable(Routes.DELIVERY_METHOD) {
        DeliveryMethodScreen(onBack = { navController.popBackStack() })
    }
}
