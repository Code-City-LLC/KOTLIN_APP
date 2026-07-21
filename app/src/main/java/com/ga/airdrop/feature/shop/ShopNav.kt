package com.ga.airdrop.feature.shop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartScreen
import com.ga.airdrop.feature.cart.CartViewModel
import com.ga.airdrop.feature.cart.OrderSummaryScreen
import com.ga.airdrop.feature.cart.OrderSummaryUiModel
import com.ga.airdrop.feature.cart.ProfileInformationScreen
import com.ga.airdrop.feature.delivery.DeliveryMethodScreen
import java.util.Locale

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
            onNavigateToNcb = {
                navController.navigate(Routes.AUCTION_NCB_CARD_ENTRY) { launchSingleTop = true }
            },
        )
    }

    // My Cart — Figma 40008284:26547.
    composable(Routes.CART) { entry ->
        val cartViewModel: CartViewModel = viewModel(entry)
        val context = LocalContext.current
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
            viewModel = cartViewModel,
            onNavigate = {
                // launchSingleTop: a rapid double-tap of Choose Delivery must not
                // push two Delivery Method entries (verify finding, 2026-07-06).
                navController.navigate(it) { launchSingleTop = true }
            },
            onOpenAmazon = { url -> launchExternalUrl(context, url) },
        )
    }

    // Delivery Method — Figma 40008740:28263, Swift
    // FigmaDeliveryMethodViewController. Cart "Choose Delivery" lands here;
    // delivery and currency choices capture the flow before Order Summary.
    composable(Routes.DELIVERY_METHOD) {
        DeliveryMethodScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { route ->
                navController.navigate(route) { launchSingleTop = true }
            },
        )
    }

    composable(Routes.PROFILE_INFORMATION) { entry ->
        val cartEntry = remember(entry) { navController.getBackStackEntry(Routes.CART) }
        val cartViewModel: CartViewModel = viewModel(cartEntry)
        val state by cartViewModel.state.collectAsState()

        LaunchedEffect(Unit) { cartViewModel.loadCheckoutProfile() }
        LaunchedEffect(state.profileSummaryNav) {
            if (state.profileSummaryNav) {
                navController.navigate(Routes.ORDER_SUMMARY) { launchSingleTop = true }
                cartViewModel.consumeProfileSummaryNav()
            }
        }

        ProfileInformationScreen(
            form = state.form,
            profileOptions = state.profileOptions,
            selectedProfile = state.selectedProfile,
            countryOptions = cartViewModel.countryOptions,
            saving = state.profileLoading || state.profileSaving,
            onBack = { navController.popBackStack() },
            onFormChange = { next -> cartViewModel.updateForm { next } },
            onProfileSelected = cartViewModel::selectCheckoutProfile,
            onPaymentMethodClick = cartViewModel::showCheckoutPaymentMethodNotice,
            onContinue = cartViewModel::saveProfileInformation,
            errorTitle = state.errorTitle,
            errorMessage = state.errorMessage,
            onDismissError = cartViewModel::dismissError,
        )
    }

    composable(Routes.ORDER_SUMMARY) { entry ->
        val cartEntry = remember(entry) { navController.getBackStackEntry(Routes.CART) }
        val cartViewModel: CartViewModel = viewModel(cartEntry)
        val state by cartViewModel.state.collectAsState()
        // Collect the live store only to recompose exact captured-line lookup
        // after an authoritative refresh; later additions are intentionally absent.
        cartViewModel.items.collectAsState().value
        val flow = cartViewModel.currentCheckoutFlow()
        val capturedLines = cartViewModel.capturedCheckoutLines()
        val currency = flow?.currency.orEmpty().uppercase(Locale.US)
        val subtotalUsd = capturedLines.sumOf { it.priceUsd * it.qty }
        val rate = state.exchangeUsdToJmd
        val fee = flow?.deliveryFee ?: 0.0
        val feeCurrency = flow?.deliveryFeeCurrency.orEmpty().uppercase(Locale.US)
        val totalCharges = if (currency == "JMD") {
            subtotalUsd * rate + if (feeCurrency == "JMD") fee else fee * rate
        } else {
            subtotalUsd + if (feeCurrency == "JMD" && rate > 0.0) fee / rate else fee
        }
        val context = LocalContext.current
        val checkoutUrl = state.checkoutUrl

        LaunchedEffect(state.orderSummaryRestartNav) {
            if (state.orderSummaryRestartNav) {
                navController.popBackStack(Routes.CART, inclusive = false)
                cartViewModel.consumeOrderSummaryRestartNav()
            }
        }

        LaunchedEffect(checkoutUrl, state.checkoutLaunchAttempt) {
            if (!checkoutUrl.isNullOrBlank() && launchExternalUrl(context, checkoutUrl)) {
                cartViewModel.consumeCheckoutUrl()
            }
        }

        // JMD → route into the NCB card-entry screen (Stripe stays hosted-checkout).
        LaunchedEffect(state.navToNcbCardEntry) {
            if (state.navToNcbCardEntry) {
                cartViewModel.consumeNcbCardEntryNav()
                navController.navigate(Routes.NCB_CARD_ENTRY) { launchSingleTop = true }
            }
        }

        OrderSummaryScreen(
            model = OrderSummaryUiModel(
                lines = capturedLines,
                note = state.note,
                currency = currency,
                exchangeUsdToJmd = rate,
                totalCharges = totalCharges,
                removingKeys = state.mutatingKeys,
                removalLocked = cartViewModel.isOrderSummaryRemovalLocked(),
                paying = state.orderPaying,
                errorTitle = state.errorTitle,
                errorMessage = state.errorMessage,
                errorShowShipments = state.errorTitle != null &&
                    cartViewModel.hasPendingPaymentAuthority(),
            ),
            onBack = {
                if (cartViewModel.onOrderSummaryBack()) navController.popBackStack()
            },
            onNoteChange = cartViewModel::updateNote,
            onRemoveItem = cartViewModel::removeOrderSummaryItem,
            onMakePayment = cartViewModel::payOrderSummary,
            onDismissError = cartViewModel::dismissError,
            onGoToShipments = {
                cartViewModel.dismissError()
                navController.navigate(Routes.SHIPMENTS) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    // NCB (JMD) card entry — shares the CART-scoped CartViewModel.
    composable(Routes.NCB_CARD_ENTRY) { entry ->
        val cartEntry = remember(entry) { navController.getBackStackEntry(Routes.CART) }
        val cartViewModel: CartViewModel = viewModel(cartEntry)
        com.ga.airdrop.feature.cart.NcbCardEntryScreen(
            onBack = { navController.popBackStack() },
            onNavigateTo3DS = {
                navController.navigate(Routes.NCB_3DS) { launchSingleTop = true }
            },
            host = cartViewModel,
        )
    }

    // NCB (JMD) 3-D Secure WebView → PaymentSuccess on completion.
    composable(Routes.NCB_3DS) { entry ->
        val cartEntry = remember(entry) { navController.getBackStackEntry(Routes.CART) }
        val cartViewModel: CartViewModel = viewModel(cartEntry)
        com.ga.airdrop.feature.cart.NcbThreeDSScreen(
            onBack = { navController.popBackStack() },
            onPaid = {
                val s = cartViewModel.state.value
                val ref = s.ncbInvoiceId?.let { "Invoice #$it" }
                navController.navigate(
                    Routes.paymentSuccess(
                        ref = ref,
                        amount = s.ncbPaidAmount,
                        fulfillment = s.ncbDeliveryMode,
                    ),
                ) {
                    popUpTo(Routes.CART) { inclusive = true }
                    launchSingleTop = true
                }
            },
            host = cartViewModel,
        )
    }

    // Auction "Buy Now" NCB (JMD) card entry — shares the AUCTION_CHECKOUT VM.
    composable(Routes.AUCTION_NCB_CARD_ENTRY) { entry ->
        val auctionEntry = remember(entry) { navController.getBackStackEntry(Routes.AUCTION_CHECKOUT) }
        val auctionViewModel: AuctionCheckoutViewModel = viewModel(auctionEntry)
        com.ga.airdrop.feature.cart.NcbCardEntryScreen(
            onBack = { navController.popBackStack() },
            onNavigateTo3DS = {
                navController.navigate(Routes.AUCTION_NCB_3DS) { launchSingleTop = true }
            },
            host = auctionViewModel,
        )
    }

    // Auction "Buy Now" NCB 3-D Secure → PaymentSuccess; clears the whole
    // auction-checkout stack back to the Shop tab on completion.
    composable(Routes.AUCTION_NCB_3DS) { entry ->
        val auctionEntry = remember(entry) { navController.getBackStackEntry(Routes.AUCTION_CHECKOUT) }
        val auctionViewModel: AuctionCheckoutViewModel = viewModel(auctionEntry)
        com.ga.airdrop.feature.cart.NcbThreeDSScreen(
            onBack = { navController.popBackStack() },
            onPaid = {
                val ref = auctionViewModel.ncbUi.value.invoiceId?.let { "Invoice #$it" }
                // Auction "Buy Now" is always pickup (no delivery step).
                val st = auctionViewModel.state.value
                val amt = st.product?.let {
                    "JMD " + String.format(java.util.Locale.US, "%,.2f", it.priceUsd * st.exchangeUsdToJmd)
                }
                navController.navigate(
                    Routes.paymentSuccess(ref = ref, amount = amt, fulfillment = "pickup"),
                ) {
                    popUpTo(Routes.SHOP) { inclusive = false }
                    launchSingleTop = true
                }
            },
            host = auctionViewModel,
        )
    }
}
