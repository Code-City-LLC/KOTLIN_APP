package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.feature.shop.ShopBillingProfile
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartHostedCheckoutParityTest {

    private val owner = AuthenticatedSessionOwner("cart-hosted-test", 401)

    @get:Rule
    val compose = createComposeRule()

    @After
    fun cleanCart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.clear()
            SavedForLaterStore.clearAll()
            CheckoutFlowStore.clear()
            CartNoteStore.save(owner, "")
        }
    }

    @Test
    fun profileInformationUsesFieldOrderAndCanonicalCountryCatalog() {
        val continueCalls = AtomicInteger()
        val paymentMethodCalls = AtomicInteger()
        val loadedForm = CartBillingForm(
            firstName = "John",
            lastName = "Brown",
            currency = "JMD",
            address1 = "22 Paradise Ave",
            address2 = "Suite 2",
            state = "St James",
            city = "Montego Bay",
            country = "United States",
            postal = "33101",
        )
        val formSnapshot = AtomicReference(loadedForm)
        compose.setContent {
            AirdropThemeProvider {
                var form by remember { mutableStateOf(loadedForm) }
                var selectedProfile by remember { mutableStateOf("John Brown") }
                ProfileInformationScreen(
                    form = form,
                    profileOptions = listOf("John Brown", "Add new profile"),
                    selectedProfile = selectedProfile,
                    countryOptions = listOf(
                        CountryCatalog.displayNameFor("United States"),
                        CountryCatalog.displayNameFor("Jamaica"),
                        CountryCatalog.displayNameFor("Canada"),
                        CountryCatalog.displayNameFor("United Kingdom"),
                    ),
                    saving = false,
                    onBack = {},
                    onFormChange = {
                        form = it
                        formSnapshot.set(it)
                    },
                    onProfileSelected = { selected ->
                        selectedProfile = selected
                        if (selected == "Add new profile") {
                            form = CartBillingForm(currency = form.currency)
                        } else {
                            form = loadedForm
                        }
                        formSnapshot.set(form)
                    },
                    onPaymentMethodClick = { paymentMethodCalls.incrementAndGet() },
                    onContinue = { continueCalls.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithText("Profile information").assertIsDisplayed()
        compose.onNodeWithText("Select profile").assertIsDisplayed()
        compose.onNodeWithText("John Brown").assertIsDisplayed()
        compose.onNodeWithText("Selected Payment Currency").assertIsDisplayed()
        compose.onNodeWithText("JMD").assertIsDisplayed()
        compose.onNodeWithTag("checkout-profile-postal-card").performScrollTo()
        compose.onNodeWithText("Postal Code").assertIsDisplayed()
        compose.onNodeWithTag("checkout-profile-postal-required").assertIsDisplayed()
        compose.onNodeWithTag("checkout-profile-state-card").performScrollTo().performClick()
        compose.onNodeWithText("Florida").performClick()
        assertEquals("Florida", formSnapshot.get().state)
        compose.onNodeWithTag("checkout-profile-city-card").performScrollTo().performClick()
        compose.onNodeWithText("Houston").performClick()
        assertEquals("Houston", formSnapshot.get().city)

        val orderedFieldTops = listOf(
            "checkout-profile-select-card",
            "checkout-profile-first-name-card",
            "checkout-profile-last-name-card",
            "checkout-profile-currency",
            "checkout-profile-address-1-card",
            "checkout-profile-address-2-card",
            "checkout-profile-state-card",
            "checkout-profile-city-card",
            "checkout-profile-country-card",
            "checkout-profile-postal-card",
        ).map { tag -> compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value }
        assertEquals("Checkout fields must retain the frozen Swift order", orderedFieldTops.sorted(), orderedFieldTops)

        compose.onNodeWithTag("checkout-profile-select-card").performClick()
        compose.onNodeWithText("Add new profile").performClick()
        assertEquals("", formSnapshot.get().firstName)
        assertEquals("JMD", formSnapshot.get().currency)
        compose.onNodeWithTag("checkout-profile-select-card").performClick()
        compose.onAllNodesWithText("John Brown")[1].performClick()
        assertEquals("John", formSnapshot.get().firstName)
        assertEquals("JMD", formSnapshot.get().currency)

        val canadaDisplay = CountryCatalog.displayNameFor("Canada")
        compose.onNodeWithTag("checkout-profile-country-card").performScrollTo().performClick()
        compose.onNodeWithText(canadaDisplay).performClick()
        assertEquals("Canada", formSnapshot.get().country)
        compose.onNodeWithTag("checkout-profile-postal-card").performScrollTo()
        compose.onNodeWithText("Postal Code").assertIsDisplayed()
        compose.onNodeWithTag("checkout-profile-postal-required").assertDoesNotExist()

        val jamaicaDisplay = CountryCatalog.displayNameFor("Jamaica")
        compose.onNodeWithTag("checkout-profile-country-card").performScrollTo().performClick()
        compose.onNodeWithText(jamaicaDisplay).performClick()
        assertEquals("Decorated picker rows must persist a canonical API value", "Jamaica", formSnapshot.get().country)
        assertEquals("No-postal countries must clear stale postal data", "", formSnapshot.get().postal)
        compose.onNodeWithText(jamaicaDisplay).assertIsDisplayed()
        compose.onNodeWithText("Postal Code").assertDoesNotExist()

        compose.onNodeWithTag("checkout-profile-payment-divider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("checkout-profile-payment-method-row").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Payment Method").assertIsDisplayed()
        compose.onNodeWithTag("checkout-profile-payment-method-row").performClick()
        assertEquals(1, paymentMethodCalls.get())
        compose.onNodeWithTag("profile-information-continue").performScrollTo()
        val paymentTop = compose.onNodeWithTag("checkout-profile-payment-method-row")
            .getUnclippedBoundsInRoot().top.value
        val continueTop = compose.onNodeWithTag("profile-information-continue")
            .getUnclippedBoundsInRoot().top.value
        assertTrue("CTA must follow Payment Method inside the scroll content", continueTop > paymentTop)
        compose.onNodeWithTag("profile-information-continue").performClick()
        assertEquals(1, continueCalls.get())
        assertTrue(checkoutCountryRequiresPostalCode("United States"))
        assertTrue(!checkoutCountryRequiresPostalCode("Jamaica"))
        assertTrue(!checkoutCountryRequiresPostalCode("Canada"))
        assertTrue(!checkoutCountryRequiresPostalCode("United Kingdom"))
        assertTrue(checkoutCountryRequiresPostalCode(CountryCatalog.displayNameFor("United States")))
    }

    @Test
    fun checkoutPaymentMethodNoticeMatchesTheSelectedCurrency() {
        lateinit var viewModel: CartViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = CartViewModel(
                checkout = FakeCartCheckoutRepository(),
                cartServer = FakeCartServerGateway(emptyList()),
                sessionBoundary = FakeSessionBoundary(owner),
            )
            viewModel.updateForm { it.copy(currency = "USD") }
            viewModel.showCheckoutPaymentMethodNotice()
        }
        assertEquals("Payment method", viewModel.state.value.errorTitle)
        assertEquals(
            "USD orders use secure Stripe checkout after Order Summary. No payment was started.",
            viewModel.state.value.errorMessage,
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.dismissError()
            viewModel.updateForm { it.copy(currency = "JMD") }
            viewModel.showCheckoutPaymentMethodNotice()
        }
        assertEquals("JMD checkout unavailable", viewModel.state.value.errorTitle)
        assertEquals(
            "JMD payment is not available yet. No payment was started.",
            viewModel.state.value.errorMessage,
        )
    }

    @Test
    fun orderSummaryIsDistinctAndKeepsCartUntilVerifiedReturn() {
        val makePaymentCalls = AtomicInteger()
        val noteUpdates = AtomicReference<String?>()
        val removedItem = AtomicReference<CartStore.CartLine?>()
        val lines = listOf(
            CartStore.CartLine(
                id = 41,
                packageId = 7041,
                title = "Ready package",
                priceUsd = 12.0,
                kind = CartStore.CartLineKind.PACKAGE,
                statusCode = 7,
                serverConfirmed = true,
            ),
            CartStore.CartLine(
                id = 42,
                packageId = 9042,
                title = "Sale watch",
                priceUsd = 20.0,
                kind = CartStore.CartLineKind.AUCTION,
                isAuction = true,
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            CartStore.init(context)
            CartStore.clear()
            lines.forEach(CartStore::add)
        }
        compose.setContent {
            AirdropThemeProvider {
                var note by remember { mutableStateOf("Door code 7") }
                OrderSummaryScreen(
                    model = OrderSummaryUiModel(
                        lines = lines,
                        note = note,
                        currency = "USD",
                        exchangeUsdToJmd = 161.0,
                        totalCharges = 32.0,
                    ),
                    onBack = {},
                    onNoteChange = {
                        note = it
                        noteUpdates.set(it)
                    },
                    onRemoveItem = { removedItem.set(it) },
                    onMakePayment = { makePaymentCalls.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithText("Order Summary").assertIsDisplayed()
        compose.onNodeWithText("Packages (1)").assertIsDisplayed()
        compose.onNodeWithText("Sales (1)").assertIsDisplayed()
        compose.onNodeWithText("Special Instructions").assertIsDisplayed()
        compose.onNodeWithText("Charges").assertIsDisplayed()
        compose.onNodeWithText("Total Packages and Sales").assertIsDisplayed()
        compose.onNodeWithText("Make Payment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Ready package").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Sale watch").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-sale-image-fallback-42").assertIsDisplayed()
        compose.onNodeWithContentDescription("Product image unavailable").assertDoesNotExist()
        compose.onNodeWithText("ARD0000000042").assertDoesNotExist()
        compose.onNodeWithTag("order-summary-sale-title-42").assertTextEquals("Sale watch")
        compose.onNodeWithTag("order-summary-sale-price-42").assertTextEquals("20.00 USD")
        val saleCardBounds = compose.onNodeWithTag("order-summary-sale-42").getUnclippedBoundsInRoot()
        val saleImageBounds = compose.onNodeWithTag("order-summary-sale-image-42").getUnclippedBoundsInRoot()
        val saleTrashBounds = compose.onNodeWithTag("order-summary-remove-auction-42").getUnclippedBoundsInRoot()
        assertEquals(
            "Sale image width must remain fixed",
            84f,
            (saleImageBounds.right - saleImageBounds.left).value,
            0.5f,
        )
        assertEquals(
            "Sale image height must remain fixed",
            84f,
            (saleImageBounds.bottom - saleImageBounds.top).value,
            0.5f,
        )
        assertEquals(
            "Sale trash must share the image well's top edge",
            saleImageBounds.top.value,
            saleTrashBounds.top.value,
            0.5f,
        )
        assertTrue(
            "Sale trash must remain in the card's top-right region",
            saleTrashBounds.left.value > saleImageBounds.right.value &&
                saleTrashBounds.right.value <= saleCardBounds.right.value,
        )
        compose.onNodeWithTag("cart-macbook-hero").assertDoesNotExist()
        compose.onNodeWithText("Basket (2 Items)").assertDoesNotExist()
        compose.onNodeWithText("Your Note").assertDoesNotExist()

        compose.onNodeWithContentDescription("Remove Sale watch").performClick()
        assertEquals(lines[1].key, removedItem.get()?.key)

        compose.onNodeWithTag("order-summary-special-instructions").performClick()
        compose.onNodeWithTag("cart-note-input").performTextClearance()
        compose.onNodeWithTag("cart-note-input").performTextInput("Leave with security")
        compose.onNodeWithTag("cart-note-save").performClick()
        assertEquals("Leave with security", noteUpdates.get())

        compose.onNodeWithText("Make Payment").performClick()
        assertEquals(1, makePaymentCalls.get())
        assertEquals("Opening/confirming payment UI must not clear cart rows", 2, CartStore.count)
    }

    @Test
    fun orderSummaryDisablesAllTrashActionsWhilePaymentAuthorityIsLocked() {
        val removeCalls = AtomicInteger()
        val lines = listOf(
            CartStore.CartLine(
                id = 51,
                packageId = 7051,
                title = "Locked package",
                kind = CartStore.CartLineKind.PACKAGE,
                statusCode = 7,
                serverConfirmed = true,
            ),
            CartStore.CartLine(
                id = 52,
                packageId = 9052,
                title = "Locked sale",
                kind = CartStore.CartLineKind.AUCTION,
                isAuction = true,
            ),
        )
        compose.setContent {
            AirdropThemeProvider {
                OrderSummaryScreen(
                    model = OrderSummaryUiModel(
                        lines = lines,
                        removalLocked = true,
                    ),
                    onBack = {},
                    onNoteChange = {},
                    onRemoveItem = { removeCalls.incrementAndGet() },
                    onMakePayment = {},
                )
            }
        }

        compose.onNodeWithTag("order-summary-remove-package-51").assertIsNotEnabled()
        compose.onNodeWithTag("order-summary-remove-auction-52").assertIsNotEnabled()
        compose.onNodeWithTag("order-summary-remove-package-51").performClick()
        compose.onNodeWithTag("order-summary-remove-auction-52").performClick()
        compose.waitForIdle()
        assertEquals("Locked trash controls must dispatch zero callbacks", 0, removeCalls.get())
    }

    @Test
    fun orderSummaryRemovalShrinksCapturedFlowAndExitsAfterFinalItem() {
        val lines = listOf(
            CartStore.CartLine(
                id = 61,
                packageId = 7061,
                title = "Ready package",
                priceUsd = 12.0,
                kind = CartStore.CartLineKind.PACKAGE,
                statusCode = 7,
                serverConfirmed = true,
            ),
            CartStore.CartLine(
                id = 62,
                packageId = 9062,
                title = "Sale watch",
                priceUsd = 20.0,
                kind = CartStore.CartLineKind.AUCTION,
                isAuction = true,
            ),
        )
        val viewModel = prepareOrderSummaryViewModel(
            repo = FakeCartCheckoutRepository(),
            currency = "USD",
            note = "",
            lines = lines,
        )
        compose.setContent {
            Box(Modifier.width(1.dp).height(1.dp))
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.removeOrderSummaryItem(lines[0])
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            CartStore.count == 1 && viewModel.capturedCheckoutLines().map { it.key } == listOf(lines[1].key)
        }
        val reduced = requireNotNull(viewModel.currentCheckoutFlow())
        assertEquals(listOf(lines[1].key), reduced.cartKeys)
        assertEquals(listOf(9062), reduced.packageIds)
        assertTrue(reduced.isAuction)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.removeOrderSummaryItem(lines[1])
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            CartStore.count == 0 && viewModel.state.value.orderSummaryRestartNav
        }
        assertNull(viewModel.currentCheckoutFlow())
        assertTrue(viewModel.capturedCheckoutLines().isEmpty())
    }

    @Test
    fun orderSummaryNarrowLargestTextKeepsFooterAndScrollableTailReachable() {
        val saleLines = (1..4).map { index ->
            CartStore.CartLine(
                id = 5000 + index,
                packageId = 9000 + index,
                title = "Accessible sale product $index with a deliberately long title",
                priceUsd = 10.0 + index,
                kind = CartStore.CartLineKind.AUCTION,
                isAuction = true,
            )
        }

        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 2f),
            ) {
                AirdropThemeProvider {
                    Box(
                        Modifier
                            .width(320.dp)
                            .height(568.dp)
                            .background(AirdropTheme.colors.gray100),
                    ) {
                        OrderSummaryScreen(
                            model = OrderSummaryUiModel(
                                lines = saleLines,
                                currency = "USD",
                                totalCharges = 50.0,
                            ),
                            onBack = {},
                            onNoteChange = {},
                            onRemoveItem = {},
                            onMakePayment = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("order-summary-make-payment").assertIsDisplayed()
        compose.onNodeWithText("Make Payment").assertIsDisplayed()
        compose.onNodeWithTag("order-summary-charges").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("order-summary-sale-title-5004").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ARD0000005004").assertDoesNotExist()
    }

    @Test
    fun chooseDeliveryRoutesWithoutCreatingCheckout() {
        // Cart only captures the flow and routes. Hosted checkout belongs to
        // the final Order Summary action.
        val repo = FakeCartCheckoutRepository()
        val navigated = AtomicReference<String?>()

        setCartContent(
            repo = repo,
            lines = listOf(
                CartStore.CartLine(
                    id = 2002,
                    packageId = 7002,
                    title = "Beta Lamp",
                    priceUsd = 7.0,
                    kind = CartStore.CartLineKind.AUCTION,
                    isAuction = true,
                ),
                CartStore.CartLine(
                    id = 2001,
                    packageId = 7001,
                    title = "Alpha Radio",
                    priceUsd = 5.0,
                    kind = CartStore.CartLineKind.AUCTION,
                    isAuction = true,
                ),
            ),
            onNavigate = { navigated.set(it) },
        )

        waitForCart()
        compose.onNodeWithText("Choose Delivery").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            navigated.get() == Routes.DELIVERY_METHOD
        }

        assertEquals("Choose Delivery must route to Delivery Method", Routes.DELIVERY_METHOD, navigated.get())
        assertEquals("Cart must never create hosted checkout", 0, repo.checkoutCalls.get())
        assertEquals("Routing to Delivery Method must not touch the cart", 2, CartStore.count)
    }

    @Test
    fun orderSummaryCreatesHostedCheckoutWithCapturedPayloadAndKeepsCart() {
        val repo = FakeCartCheckoutRepository()
        val note = "Leave with security"
        val viewModel = prepareOrderSummaryViewModel(
            repo = repo,
            currency = "USD",
            note = note,
            lines = listOf(
                CartStore.CartLine(
                    id = 2001,
                    packageId = 7001,
                    title = "Alpha package",
                    priceUsd = 5.0,
                    kind = CartStore.CartLineKind.PACKAGE,
                    statusCode = 7,
                    serverConfirmed = true,
                ),
                CartStore.CartLine(
                    id = 2002,
                    packageId = 7002,
                    title = "Beta sale",
                    priceUsd = 7.0,
                    kind = CartStore.CartLineKind.AUCTION,
                    isAuction = true,
                ),
            ),
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync(viewModel::payOrderSummary)
        compose.waitUntil(timeoutMillis = 5_000) {
            repo.checkoutCalls.get() == 1 && viewModel.state.value.checkoutUrl == CheckoutUrl
        }

        assertEquals(listOf(7001, 7002), repo.lastPackageIds)
        assertEquals("USD", repo.lastCurrency)
        assertEquals(true, repo.lastIsAuction)
        assertEquals(note, repo.lastUserNote)
        assertNotNull(CheckoutFlowStore.pending("cs_cart_test", owner))
        assertEquals("Hosted launch must not clear cart rows", 2, CartStore.count)

        InstrumentationRegistry.getInstrumentation().runOnMainSync(viewModel::consumeCheckoutUrl)
        assertNull(viewModel.state.value.checkoutUrl)
        assertNotNull("Consuming transient URL must keep durable pending identity", CheckoutFlowStore.pending(owner))
        assertEquals(2, CartStore.count)
    }

    @Test
    fun orderSummaryJmdFailsClosedWithoutCreatingStripeCheckout() {
        val repo = FakeCartCheckoutRepository()
        val viewModel = prepareOrderSummaryViewModel(
            repo = repo,
            currency = "JMD",
            note = "No Stripe for JMD",
            lines = listOf(
                CartStore.CartLine(
                    id = 3001,
                    packageId = 8001,
                    title = "JMD sale",
                    priceUsd = 9.0,
                    kind = CartStore.CartLineKind.AUCTION,
                    isAuction = true,
                ),
            ),
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync(viewModel::payOrderSummary)

        assertEquals(0, repo.checkoutCalls.get())
        assertEquals("JMD checkout unavailable", viewModel.state.value.errorTitle)
        assertNull(viewModel.state.value.checkoutUrl)
        assertEquals(1, CartStore.count)
    }

    private fun prepareOrderSummaryViewModel(
        repo: FakeCartCheckoutRepository,
        currency: String,
        note: String,
        lines: List<CartStore.CartLine>,
    ): CartViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val boundary = FakeSessionBoundary(owner)
        lateinit var viewModel: CartViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CheckoutFlowStore.init(context)
            CartStore.onAuthenticatedSessionChanged(owner)
            CartStore.clear()
            CheckoutFlowStore.clear()
            lines.forEach { line -> check(CartStore.add(line)) }
            check(CartNoteStore.save(owner, note))
            val flow = requireNotNull(
                CheckoutFlowStore.start(owner, CartStore.items.value),
            )
            if (currency.equals("JMD", ignoreCase = true)) {
                requireNotNull(
                    CheckoutFlowStore.update(owner, flow.id) {
                        it.copy(currency = currency, phase = CheckoutPhase.PROFILE_INFORMATION)
                    },
                )
                requireNotNull(
                    CheckoutFlowStore.update(owner, flow.id) {
                        it.copy(phase = CheckoutPhase.ORDER_SUMMARY)
                    },
                )
            } else {
                requireNotNull(
                    CheckoutFlowStore.update(owner, flow.id) {
                        it.copy(currency = currency, phase = CheckoutPhase.ORDER_SUMMARY)
                    },
                )
            }
            viewModel = CartViewModel(
                checkout = repo,
                cartServer = FakeCartServerGateway(CartStore.items.value),
                sessionBoundary = boundary,
            )
        }
        return viewModel
    }

    private fun setCartContent(
        repo: FakeCartCheckoutRepository,
        lines: List<CartStore.CartLine>,
        onNavigate: (String) -> Unit = {},
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CheckoutFlowStore.init(context)
            CartStore.onAuthenticatedSessionChanged(owner)
            CartStore.clear()
            CheckoutFlowStore.clear()
            lines.forEach { CartStore.add(it) }
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    val viewModel = remember(repo) {
                        CartViewModel(
                            checkout = repo,
                            cartServer = FakeCartServerGateway(lines),
                            sessionBoundary = FakeSessionBoundary(owner),
                        )
                    }
                    CartScreen(
                        onBack = {},
                        onShopNow = {},
                        viewModel = viewModel,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }

    private fun waitForCart() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Choose Delivery").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class FakeCartCheckoutRepository(
        private val checkoutFailure: Throwable? = null,
    ) : ShopCheckoutRepository {
        val checkoutCalls = AtomicInteger()
        var lastPackageIds: List<Int>? = null
        var lastCurrency: String? = null
        var lastIsAuction: Boolean? = null
        var lastUserNote: String? = null

        override suspend fun createCheckout(
            packageIds: List<Int>,
            currency: String,
            isAuction: Boolean,
            userNote: String?,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<CheckoutResponse> {
            checkoutCalls.incrementAndGet()
            lastPackageIds = packageIds
            lastCurrency = currency
            lastIsAuction = isAuction
            lastUserNote = userNote
            return checkoutFailure?.let { Result.failure(it) }
                ?: Result.success(CheckoutResponse(checkoutUrl = CheckoutUrl, sessionId = "cs_cart_test"))
        }

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)

        override suspend fun billingProfile(): Result<ShopBillingProfile> =
            Result.success(ShopBillingProfile(firstName = "John"))
    }

    private class FakeCartServerGateway(
        private val lines: List<CartStore.CartLine>,
    ) : CartServerGateway {
        override suspend fun cart(
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<List<CartStore.CartLine>> = Result.success(lines)

        override suspend fun addPackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun removePackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeSessionBoundary(initial: AuthenticatedSessionOwner) : AuthenticatedSessionBoundary {
        private val current = MutableStateFlow<AuthenticatedSessionOwner?>(initial)
        override val changes = current

        override fun capture(): AuthenticatedSessionOwner? = current.value
        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = current.value == owner
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (!isCurrent(owner)) return false
            action()
            return true
        }

        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean =
            isCurrent(owner) && action()

        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? =
            owner.takeIf(::isCurrent)?.let {
                AuthenticatedRequestOwner(
                    session = it,
                    provenance = AuthTokenStore.RequestProvenance(
                        revision = 401,
                        sessionId = it.sessionId,
                        accountId = it.accountId,
                    ),
                )
            }

        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && (owner.accountId == null || owner.accountId == accountId)
    }

    private companion object {
        const val CheckoutUrl = "https://checkout.airdropja.test/cart-session"
    }
}
