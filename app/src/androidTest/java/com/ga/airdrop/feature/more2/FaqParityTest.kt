package com.ga.airdrop.feature.more2

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsersEnvelope
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.DeactivateAccountRequest
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.FaqItem
import com.ga.airdrop.data.model.LoginRequest
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.data.model.ShippingRates
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaqParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heroMatchesSwiftCopyInLight() {
        setFaq(ThemeController.Mode.LIGHT)
        assertSwiftHero()
        saveHeroScreenshot("faq_hero_light.png")
    }

    @Test
    fun heroMatchesSwiftCopyInDark() {
        setFaq(ThemeController.Mode.DARK)
        assertSwiftHero()
        saveHeroScreenshot("faq_hero_dark.png")
    }

    @Test
    fun noMatchKeepsSwiftHintAndContactSupportRoute() {
        var route: String? = null
        setFaq(ThemeController.Mode.LIGHT, onNavigate = { route = it })

        compose.onNode(hasSetTextAction(), useUnmergedTree = true)
            .performTextInput("zzz-no-faq-match")
        compose.onNodeWithText("No matches for “zzz-no-faq-match”")
            .assertIsDisplayed()
        compose.onNodeWithText("Try different keywords, or tap Contact Support below.")
            .assertIsDisplayed()

        compose.onNodeWithText("Contact Support").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Routes.CONTACTS, route) }
    }

    // ─── The three states that had no coverage at all ──────────────────────
    //
    // FALLBACK_FAQS was deleted on 2026-07-26. It was the INITIAL value of
    // FaqUiState.faqs and the screen painted it with no loading gate, so a
    // failed /faqs was indistinguishable from a successful one. Entry #5
    // answered "What is the mailing address?" with the OLD Fort Lauderdale
    // warehouse — 1905 NW 51st Street 43A, Fort Lauderdale FL 33309 — against
    // the Hialeah address of record. A customer who followed it shipped to the
    // wrong US state.
    //
    // Nothing asserted what this screen does while the fetch is in flight or
    // after it fails, which is the same gap that let a sibling deletion ship a
    // view-model error no composable read. These tests close it.

    /**
     * Failed: the shared LegalLoadFailed error + Try Again, and — the part that
     * matters — NOT ONE question row. Not a partial list, not a placeholder
     * one, not a client-authored one.
     */
    @Test
    fun failedFaqLoadShowsErrorAndRetryAndNoQuestionRows() {
        val api = FakeMore2Api(failure = IOException("faqs fetch failed"))
        val viewModel = setFaq(api, ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("legal-load-failed").assertIsDisplayed()
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("We couldn't load the FAQs.").assertIsDisplayed()

        // A failure is not a spinner.
        compose.onNode(indeterminateSpinner()).assertDoesNotExist()
        // A failure is not the "No matches" search empty state either — that
        // one tells the customer their QUERY found nothing, which is a lie
        // when the list never loaded.
        assertNoTextExists("No matches for", substring = true)

        // No rows, in the tree or in the model.
        assertNoFaqRowsRendered()
        assertTrue(
            "A failed /faqs must leave the list empty — no client-authored answers",
            viewModel.state.value.faqs.isEmpty(),
        )
        assertEquals("We couldn't load the FAQs.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)

        assertDeletedFortLauderdaleFallbackIsAbsent("failed")
        saveRootScreenshot("faq_load_failed_retry.png")
    }

    /**
     * Try Again must genuinely re-hit GET /faqs — a re-render that shows the
     * same error is not a retry — and the server's list is what then renders.
     */
    @Test
    fun retryRefetchesFaqsAndThenRendersTheServerList() {
        val api = FakeMore2Api(failure = IOException("faqs fetch failed"))
        val viewModel = setFaq(api, ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("legal-load-failed").assertIsDisplayed()
        val callsBeforeRetry = api.faqCalls.get()
        assertEquals("Entering FAQs should make exactly one /faqs request", 1, callsBeforeRetry)

        api.serve(LIVE_FAQS)
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            api.faqCalls.get() == callsBeforeRetry + 1 && !viewModel.state.value.loading
        }
        compose.waitForIdle()

        assertEquals(
            "Try Again must actually re-fetch /faqs, not just recompose",
            callsBeforeRetry + 1,
            api.faqCalls.get(),
        )
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        compose.onNode(indeterminateSpinner()).assertDoesNotExist()
        assertNull(viewModel.state.value.error)
        assertEquals(LIVE_FAQS, viewModel.state.value.faqs)

        LIVE_FAQS.forEach { faq ->
            compose.onNodeWithText(faq.question).performScrollTo().assertIsDisplayed()
        }
        assertDeletedFortLauderdaleFallbackIsAbsent("recovered")
        saveRootScreenshot("faq_retry_recovered.png")
    }

    /**
     * In flight: a spinner, and neither of the other two states. A spinner is
     * not an error and it is not content — the whole reason the fallback was
     * dangerous is that those three were the same picture.
     *
     * `mainClock.autoAdvance = false` because the indeterminate spinner
     * animates forever; with auto-advance on, `waitForIdle` would pump frames
     * until Espresso gave up.
     */
    @Test
    fun loadInFlightShowsSpinnerThatIsNeitherErrorNorContent() {
        compose.mainClock.autoAdvance = false
        val gate = CompletableDeferred<Unit>()
        val api = FakeMore2Api(gate = gate)
        val viewModel = setFaq(api, ThemeController.Mode.LIGHT, awaitFirstLoad = false)

        compose.onNode(indeterminateSpinner()).assertIsDisplayed()

        // Loading is NOT failure.
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).assertDoesNotExist()
        assertNoTextExists("We couldn't load the FAQs.")
        assertNoTextExists("Try Again")
        assertNull(viewModel.state.value.error)

        // Loading is NOT content.
        assertNoFaqRowsRendered()
        assertNoTextExists("No matches for", substring = true)
        assertTrue(
            "Nothing may be listed before the fetch lands",
            viewModel.state.value.faqs.isEmpty(),
        )
        assertTrue(viewModel.state.value.loading)

        assertDeletedFortLauderdaleFallbackIsAbsent("loading")
        saveRootScreenshot("faq_loading_no_answers.png")

        // Let the fetch land: the spinner gives way to the server's list.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { gate.complete(Unit) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        compose.onNode(indeterminateSpinner()).assertDoesNotExist()
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        LIVE_FAQS.forEach { compose.onNodeWithText(it.question).assertExists() }
        assertFalse(viewModel.state.value.loading)
    }

    /**
     * The live endpoint currently returns TWO entries. A short list is the
     * correct answer, not a bug to paper over: two server questions render as
     * two cards, with no error, no spinner and no invented padding. The
     * expanded answers must carry the server's address, not the deleted one.
     */
    @Test
    fun twoEntryServerListRendersInFullWithNoInventedEntries() {
        val api = FakeMore2Api(items = LIVE_FAQS)
        val viewModel = setFaq(api, ThemeController.Mode.LIGHT)

        assertEquals("Entering FAQs should make exactly one /faqs request", 1, api.faqCalls.get())
        assertEquals(
            "The rendered list must be exactly what the server sent — nothing appended",
            LIVE_FAQS,
            viewModel.state.value.faqs,
        )
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        compose.onNode(indeterminateSpinner()).assertDoesNotExist()
        assertNoTextExists("No matches for", substring = true)

        LIVE_FAQS.forEach { faq ->
            compose.onNodeWithTag("faq-${faq.id}-title", useUnmergedTree = true).assertExists()
        }
        // A third card would mean the client invented one.
        compose.onNodeWithTag("faq-3-title", useUnmergedTree = true).assertDoesNotExist()

        // Expand every answer: the mailing-address answer is the one that was
        // wrong for years, so read it in the state a customer actually reads it.
        LIVE_FAQS.forEach { faq ->
            compose.onNodeWithText(faq.question).performScrollTo().performClick()
            compose.waitForIdle()
        }
        LIVE_FAQS.forEach { faq ->
            compose.onNodeWithText(faq.answer).performScrollTo().assertIsDisplayed()
        }
        assertDeletedFortLauderdaleFallbackIsAbsent("loaded, all answers expanded")
        saveRootScreenshot("faq_server_two_entries.png")
    }

    /**
     * The deleted FALLBACK_FAQS mailing address, spelled out, so a
     * reintroduction fails here instead of at a customer's shipping counter.
     * Asserted in every state the screen has.
     */
    private fun assertDeletedFortLauderdaleFallbackIsAbsent(state: String) {
        listOf(
            "1905 NW 51st Street",
            "Fort Lauderdale",
            "33309",
            "954-692-6659",
        ).forEach { forbidden ->
            assertTrue(
                "“$forbidden” is the deleted Fort Lauderdale fallback and must never " +
                    "render — it shipped a customer to the wrong US state (state: $state)",
                compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    private fun assertNoFaqRowsRendered() {
        LIVE_FAQS.forEach { faq ->
            assertNoTextExists(faq.question)
            assertNoTextExists(faq.answer)
            assertTrue(
                "No accordion card may render for FAQ ${faq.id} without server data",
                compose.onAllNodesWithTag("faq-${faq.id}-title", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    private fun assertNoTextExists(text: String, substring: Boolean = false) {
        assertTrue(
            "“$text” should not render in this state",
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun indeterminateSpinner() = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate,
    )

    private fun assertSwiftHero() {
        compose.onNodeWithText("Frequently Asked").assertIsDisplayed()
        compose.onNodeWithText(
            "Quick answers to the questions our customers ask most. " +
                "Tap any card to see the full answer.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("faq-search").assertIsDisplayed()

        val card = bounds("faq-hero")
        val accent = bounds("faq-hero-accent")
        val monogram = bounds("faq-hero-monogram")
        val title = bounds("faq-hero-title")
        val subtitle = bounds("faq-hero-subtitle")

        assertClose(140f, (accent.right - accent.left).value, "Swift accent width")
        assertClose(140f, (accent.bottom - accent.top).value, "Swift accent height")
        assertClose(64f, (monogram.right - monogram.left).value, "Swift monogram width")
        assertClose(64f, (monogram.bottom - monogram.top).value, "Swift monogram height")
        assertClose(20f, (title.left - card.left).value, "Swift title leading inset")
        assertClose(22f, (title.top - card.top).value, "Swift title top inset")
        assertClose(20f, (subtitle.left - card.left).value, "Swift subtitle leading inset")
        assertClose(20f, (card.right - subtitle.right).value, "Swift subtitle trailing inset")
        assertClose(22f, (card.bottom - subtitle.bottom).value, "Swift subtitle bottom inset")
        assertTrue("Title must remain left of the monogram", title.right <= monogram.left)
        assertClose(0f, (accent.right - card.right).value, "Clipped accent trailing edge")
        assertClose(0f, (accent.top - card.top).value, "Clipped accent top edge")
    }

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun saveHeroScreenshot(name: String) {
        val bitmap = compose.onNodeWithTag("faq-hero").captureToImage().asAndroidBitmap()
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/faq/$name",
        )
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun saveRootScreenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/faq/$name",
        )
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun setFaq(
        mode: ThemeController.Mode,
        onNavigate: (String) -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                FaqScreen(onBack = {}, onNavigate = onNavigate)
            }
        }
    }

    /**
     * The same harness with GET /faqs served by [api] instead of the network —
     * without it the loading and failure states are not reachable at all, which
     * is precisely why they had no tests. The view model is built on the main
     * thread so its init load runs on Dispatchers.Main.immediate and the first
     * composition already sees the right state.
     */
    private fun setFaq(
        api: FakeMore2Api,
        mode: ThemeController.Mode,
        awaitFirstLoad: Boolean = true,
        onNavigate: (String) -> Unit = {},
    ): FaqViewModel {
        lateinit var viewModel: FaqViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = FaqViewModel(More2Repository(api))
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    FaqScreen(onBack = {}, onNavigate = onNavigate, viewModel = viewModel)
                }
            }
        }
        if (awaitFirstLoad) {
            compose.waitUntil(timeoutMillis = 5_000) {
                api.faqCalls.get() == 1 && !viewModel.state.value.loading
            }
            compose.waitForIdle()
        } else {
            // The in-flight test pauses the clock; nudge a single frame so the
            // first composition is laid out before anything is asserted.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        return viewModel
    }

    /**
     * GET /faqs on demand: [gate] holds a request in flight, [failure] makes it
     * fail, and [faqCalls] makes "the retry actually re-fetched" provable
     * instead of assumed.
     */
    private class FakeMore2Api(
        private val gate: CompletableDeferred<Unit>? = null,
        items: List<FaqItem> = LIVE_FAQS,
        failure: Throwable? = null,
    ) : More2Api {
        val faqCalls = AtomicInteger()

        @Volatile
        private var servedItems: List<FaqItem> = items

        @Volatile
        private var servedFailure: Throwable? = failure

        /** Bring the endpoint back before exercising Try Again. */
        fun serve(items: List<FaqItem>) {
            servedItems = items
            servedFailure = null
        }

        override suspend fun faqs(): Paginated<FaqItem> {
            faqCalls.incrementAndGet()
            gate?.await()
            servedFailure?.let { throw it }
            return Paginated(items = servedItems)
        }

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in FaqParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in FaqParityTest")
    }
}

// ─── Fixture ───────────────────────────────────────────────────────────────

/**
 * Stands in for the live `GET /faqs` body.
 *
 * The endpoint currently returns TWO entries. That short list is the correct
 * state of the world, not a bug to pad out — padding it was exactly what the
 * deleted 14-entry FALLBACK_FAQS did.
 *
 * Entry 2 is deliberately the question whose deleted hardcoded answer named the
 * old Fort Lauderdale warehouse; the address here is the one the rest of the
 * app and Laravel's seeder give. Only the SHAPE is claimed to match production
 * (id / question / answer, ids being numeric strings as the live rows are) —
 * these tests assert that whatever the server sends is what renders, never that
 * this exact copy is what the server sends.
 */
private val LIVE_FAQS = listOf(
    FaqItem(
        id = "1",
        question = "How do I get my AirDrop mailbox?",
        answer = "Sign up in the app and your account number is issued immediately.",
    ),
    FaqItem(
        id = "2",
        question = "What is the mailing address?",
        answer = "6175 NW 167th Street, Unit G36, Hialeah, Florida 33015.",
    ),
)
