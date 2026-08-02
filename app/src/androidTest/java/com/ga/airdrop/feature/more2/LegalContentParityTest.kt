package com.ga.airdrop.feature.more2

import android.graphics.Bitmap
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.ThemeController
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegalContentParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun liveLegalHtmlColorsHeadingsLikeSwift() {
        val headingColor = 0xFF292929.toInt()
        val styled = colorLegalHeadings(
            HtmlCompat.fromHtml(
                prepareLegalHtml(
                    """
                    # Shipping Terms

                    Body copy stays muted.
                    """.trimIndent(),
                ),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            ),
            headingColor,
        )

        val headingStart = styled.indexOf("Shipping Terms")
        val bodyStart = styled.indexOf("Body copy")
        assertTrue("sample heading should be present", headingStart >= 0)
        assertTrue("sample body should be present", bodyStart >= 0)

        val headingSpans = styled.getSpans(
            headingStart,
            headingStart + "Shipping Terms".length,
            ForegroundColorSpan::class.java,
        )
        val bodySpans = styled.getSpans(
            bodyStart,
            bodyStart + "Body copy".length,
            ForegroundColorSpan::class.java,
        )

        assertTrue(
            "Swift recolors parsed heading runs to textDarkTitle",
            headingSpans.any { it.foregroundColor == headingColor },
        )
        assertFalse(
            "Swift leaves body runs on the TextView body color/textDescription path",
            bodySpans.any { it.foregroundColor == headingColor },
        )
    }

    @Test
    fun liveLegalHtmlStripsFrozenCmsColorsBeforeThemeRecoloring() {
        val prepared = prepareLegalHtml(
            """
            <h2 style="color:#111111;background-color:#ffffff">Overview</h2>
            <p><font color="#ff0000" bgcolor="#00ff00">Body</font></p>
            """.trimIndent(),
        )

        assertFalse(prepared.contains("color:", ignoreCase = true))
        assertFalse(prepared.contains("background-color", ignoreCase = true))
        assertFalse(prepared.contains(" color=", ignoreCase = true))
        assertFalse(prepared.contains(" bgcolor=", ignoreCase = true))
    }

    @Test
    fun legalAccordionDefaultGapStaysSwiftTermsPrivacyFiveDp() {
        setAccordionCard(titleEndGap = Spacing.xs, tag = "legal-default")

        val gap = compose.onNodeWithTag(
            "legal-default-title-chevron-gap",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        assertClose(5f, boundsWidth(gap), "Terms/Privacy title-to-chevron gap")
    }

    /**
     * FIXTURE NOTE (2026-07-26): this used to render [FaqScreen] with its
     * default `viewModel()`, i.e. with no server data at all. It only ever
     * found a `faq-1-…` card because FaqUiState seeded a hardcoded
     * FALLBACK_FAQS list. That list was deleted — its mailing address was the
     * old Fort Lauderdale warehouse — so an unfetched FAQ screen is now a
     * spinner, not a card to measure. The question row now comes from the
     * server the way the app gets it, injected through [More2Repository], so
     * this geometry proof no longer depends on the emulator having a network.
     */
    @Test
    fun faqScreenUsesSwiftQuestionChevronGap() {
        val api = FakeLegalApi(faqItems = listOf(FIXTURE_FAQ))
        lateinit var viewModel: FaqViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            viewModel = FaqViewModel(More2Repository(api))
        }
        compose.setContent {
            AirdropTheme {
                FaqScreen(onBack = {}, onNavigate = {}, viewModel = viewModel)
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag(
                "faq-1-title-chevron-gap",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }

        val gap = compose.onNodeWithTag(
            "faq-1-title-chevron-gap",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        assertClose(10f, boundsWidth(gap), "FAQ question-to-chevron gap")
    }

    @Test
    fun captureLiveLegalHtmlLight() {
        setLegalHtmlContent(ThemeController.Mode.LIGHT)

        saveRootScreenshot("legal_live_html_light.png")
    }

    @Test
    fun captureLiveLegalHtmlDark() {
        setLegalHtmlContent(ThemeController.Mode.DARK)

        saveRootScreenshot("legal_live_html_dark.png")
    }

    // ─── The three states that had no coverage at all ──────────────────────
    //
    // The hardcoded TERMS_SECTIONS fallback was deleted on 2026-07-26. It was a
    // verbatim copy of ANOTHER PRODUCT's legal text — it named "the etoy app"
    // four times and described a platform for "the exchange or giving away of
    // toys" — and TermsScreen painted it under AirDrop's own header whenever
    // GET /content/terms-conditions failed, indistinguishable from a
    // successful load.
    //
    // Nothing asserted what this screen does while the fetch is in flight or
    // after it fails. That is the same gap that let a sibling deletion ship a
    // checkout dead end: the view model set an error no composable read, and
    // no test caught it. These tests close it for Terms & Conditions, and the
    // last two do what can be done for Privacy Policy (see the note there).

    /**
     * Failed: the shared [LegalLoadFailed] copy + Try Again, and — the part
     * that matters — NO document. Not a partial one, not a placeholder one,
     * and above all not somebody else's.
     */
    @Test
    fun failedTermsFetchShowsLegalLoadFailedAndNoDocument() {
        val api = FakeLegalApi()
        val viewModel = setTermsScreen(api)

        assertEquals("Entering Terms should make exactly one CMS request", 1, api.termsCalls.get())
        compose.onNodeWithTag("legal-load-failed").assertIsDisplayed()
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(TERMS_ERROR).assertIsDisplayed()
        compose.onNodeWithText("Try Again").assertIsDisplayed()

        // A failure is not a spinner.
        compose.onNode(indeterminateSpinner()).assertDoesNotExist()

        // A failure is not content, in the tree or in the model.
        assertNoLegalDocumentRendered("failed terms fetch")
        assertEquals(TERMS_ERROR, viewModel.state.value.error)
        assertNull(
            "A failed fetch must leave no document behind for the screen to paint",
            viewModel.state.value.liveContent,
        )
        assertFalse(viewModel.state.value.loading)

        assertDeletedEtoyFallbackIsAbsent("failed terms fetch")
        saveRootScreenshot("legal_terms_load_failed.png")
    }

    /**
     * Try Again must genuinely re-hit GET /content/terms-conditions — a
     * recomposition that re-renders the same error is not a retry — and the
     * server's document is what then renders in its place.
     */
    @Test
    fun legalRetryRefetchesTermsAndThenRendersTheServerDocument() {
        val api = FakeLegalApi()
        val viewModel = setTermsScreen(api)

        compose.onNodeWithTag("legal-load-failed").assertIsDisplayed()
        val callsBeforeRetry = api.termsCalls.get()
        assertEquals("Entering Terms should make exactly one CMS request", 1, callsBeforeRetry)

        api.serveTerms(LIVE_TERMS_DOCUMENT)
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals(
            "Try Again must actually re-fetch /content/terms-conditions, not just recompose",
            callsBeforeRetry + 1,
            api.termsCalls.get(),
        )
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(indeterminateSpinner()).assertDoesNotExist()
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertTrue(
            "The recovered state must hold the server's document",
            viewModel.state.value.liveContent.orEmpty().contains(LIVE_TERMS_MARKER),
        )
        assertLegalDocumentRendered(LIVE_TERMS_MARKER, "recovered terms")

        assertDeletedEtoyFallbackIsAbsent("recovered terms")
        saveRootScreenshot("legal_terms_retry_recovered.png")
    }

    /**
     * In flight: a spinner, and neither of the other two states. A spinner is
     * not an error and it is not content — the whole reason the deleted
     * fallback was dangerous is that all three were the same picture.
     *
     * `mainClock.autoAdvance = false` because the indeterminate spinner
     * animates forever; with auto-advance on, `waitForIdle` would pump frames
     * until the instrumentation gave up.
     */
    @Test
    fun termsLoadInFlightShowsTheSpinnerAndNeitherErrorNorDocument() {
        compose.mainClock.autoAdvance = false
        val gate = CompletableDeferred<Unit>()
        val api = FakeLegalApi(termsGate = gate)
        api.serveTerms(LIVE_TERMS_DOCUMENT)

        val viewModel = setTermsScreen(api)

        compose.onNode(indeterminateSpinner()).assertIsDisplayed()

        // Loading is NOT failure.
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).assertDoesNotExist()
        assertNoTextRendered(TERMS_ERROR)
        assertNoTextRendered("Try Again")
        assertNull(viewModel.state.value.error)

        // Loading is NOT content.
        assertNoLegalDocumentRendered("terms load in flight")
        assertNull(viewModel.state.value.liveContent)
        assertTrue(viewModel.state.value.loading)

        assertDeletedEtoyFallbackIsAbsent("terms load in flight")
        saveRootScreenshot("legal_terms_loading.png")

        // Let the fetch land: the spinner must give way to the real document.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { gate.complete(Unit) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        compose.onNode(indeterminateSpinner()).assertDoesNotExist()
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        assertLegalDocumentRendered(LIVE_TERMS_MARKER, "terms fetch landed")
        assertFalse(viewModel.state.value.loading)
    }

    /**
     * The deleted text, in every state the screen has, in one composition.
     * Two consecutive retries also prove the retry is a fetch each time and
     * not a one-shot latch.
     */
    @Test
    fun theDeletedEtoyTermsCanNeverRenderInAnyState() {
        val api = FakeLegalApi()
        val viewModel = setTermsScreen(api)

        // 1. failed
        compose.onNodeWithTag("legal-load-failed").assertIsDisplayed()
        assertDeletedEtoyFallbackIsAbsent("failed")

        // 2. failed again — a retry that still fails is still an error, never
        //    a quiet substitution.
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("The second attempt must also reach the network", 2, api.termsCalls.get())
        compose.onNodeWithTag("legal-load-failed").assertIsDisplayed()
        assertNoLegalDocumentRendered("second failure")
        assertDeletedEtoyFallbackIsAbsent("failed twice")

        // 3. recovered
        api.serveTerms(LIVE_TERMS_DOCUMENT)
        compose.onNodeWithTag("legal-retry", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("The third attempt must also reach the network", 3, api.termsCalls.get())
        assertLegalDocumentRendered(LIVE_TERMS_MARKER, "recovered")
        assertDeletedEtoyFallbackIsAbsent("recovered")
        assertNull(viewModel.state.value.error)
    }

    /**
     * Privacy Policy, at the view-model level.
     *
     * SCOPE NOTE: [PrivacyPolicyViewModel] gained `loading`/`error` and the
     * public `load()` retry alongside [TermsViewModel], and this proves both
     * work — including that `load()` genuinely re-issues the request. It is
     * asserted here rather than through [PrivacyPolicyScreen] on purpose:
     * that screen still renders its own PRIVACY_SECTIONS constant whenever
     * `liveContent` is null and reads neither `state.loading` nor
     * `state.error`, so no error or spinner can reach a customer today. That
     * is reported, not patched, from here — see the run report.
     */
    @Test
    fun privacyPolicyViewModelSurfacesTheFailureAndItsLoadRetryRefetches() {
        val api = FakeLegalApi()
        lateinit var viewModel: PrivacyPolicyViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Built on the main thread so the init load runs on
            // Dispatchers.Main.immediate and has already settled here.
            viewModel = PrivacyPolicyViewModel(More2Repository(api))
        }

        assertEquals("Constructing the view model should fetch once", 1, api.privacyCalls.get())
        assertEquals(PRIVACY_ERROR, viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertNull(
            "A failed fetch must leave no document behind",
            viewModel.state.value.liveContent,
        )

        api.servePrivacy(LIVE_PRIVACY_DOCUMENT)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.load() }

        assertEquals(
            "load() is the retry hook; it must re-issue GET /content/privacy-policy",
            2,
            api.privacyCalls.get(),
        )
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertTrue(
            "The recovered state must hold the server's document",
            viewModel.state.value.liveContent.orEmpty().contains(LIVE_PRIVACY_MARKER),
        )
    }

    /**
     * When the Privacy fetch succeeds, the server's document is what renders —
     * and not one line of the client-authored PRIVACY_SECTIONS constant.
     */
    @Test
    fun privacyPolicyRendersTheServerDocumentAndNotClientAuthoredSections() {
        val api = FakeLegalApi()
        api.servePrivacy(LIVE_PRIVACY_DOCUMENT)
        val viewModel = setPrivacyScreen(api)

        assertEquals("Entering Privacy should make exactly one CMS request", 1, api.privacyCalls.get())
        assertLegalDocumentRendered(LIVE_PRIVACY_MARKER, "live privacy document")
        compose.onNode(indeterminateSpinner()).assertDoesNotExist()
        compose.onNodeWithTag("legal-load-failed").assertDoesNotExist()
        assertNull(viewModel.state.value.error)

        // The client-authored accordion must be gone the moment the server
        // has spoken.
        assertNoTextRendered("Behavioral Targeting/Re-targeting")
        assertNoTextRendered("Tracking Technologies")
        assertNoTextRendered("Log Files")

        assertDeletedEtoyFallbackIsAbsent("live privacy document")
        saveRootScreenshot("legal_privacy_live_document.png")
    }

    // ─── Harness ───────────────────────────────────────────────────────────

    private fun setTermsScreen(
        api: FakeLegalApi,
        mode: ThemeController.Mode = ThemeController.Mode.LIGHT,
    ): TermsViewModel {
        lateinit var viewModel: TermsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            // Built on the main thread so the init load runs on
            // Dispatchers.Main.immediate and the first composition already
            // sees its result.
            viewModel = TermsViewModel(More2Repository(api))
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    TermsScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun setPrivacyScreen(
        api: FakeLegalApi,
        mode: ThemeController.Mode = ThemeController.Mode.LIGHT,
    ): PrivacyPolicyViewModel {
        lateinit var viewModel: PrivacyPolicyViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = PrivacyPolicyViewModel(More2Repository(api))
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    PrivacyPolicyScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun indeterminateSpinner() = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate,
    )

    /**
     * "etoy" — plus the toy-swapping copy it travelled with — spelled out, so
     * a reintroduction fails here instead of in front of a customer reading
     * what they believe are AirDrop's terms. Asserted in every state.
     *
     * Checked against BOTH trees on purpose: [LegalHtmlContent] renders the
     * CMS body into an interop `TextView`, whose text the Compose semantics
     * tree never carries, so a fallback that came back through the document
     * path would sail straight past `onNodeWithText`.
     */
    private fun assertDeletedEtoyFallbackIsAbsent(state: String) {
        val rendered = renderedAndroidViewText()
        DELETED_ETOY_STRINGS.forEach { forbidden ->
            assertTrue(
                "“$forbidden” belongs to the deleted other-product legal text and must " +
                    "never render as AirDrop's own (state: $state)",
                compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
            assertTrue(
                "“$forbidden” belongs to the deleted other-product legal text and must " +
                    "never reach the CMS TextView either (state: $state)",
                compose.onAllNodesWithText(
                    forbidden,
                    substring = true,
                    ignoreCase = true,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isEmpty(),
            )
            assertFalse(
                "“$forbidden” belongs to the deleted other-product legal text and must " +
                    "never render in the document TextView (state: $state)",
                rendered.contains(forbidden, ignoreCase = true),
            )
        }
    }

    private fun assertLegalDocumentRendered(marker: String, state: String) {
        val rendered = renderedAndroidViewText()
        assertTrue(
            "The server's document should be on screen in the $state state, " +
                "but the rendered TextView text was: “$rendered”",
            rendered.contains(marker),
        )
    }

    private fun assertNoLegalDocumentRendered(state: String) {
        val rendered = renderedAndroidViewText()
        listOf(LIVE_TERMS_MARKER, LIVE_PRIVACY_MARKER, "AirDrop Terms", "AirDrop Privacy")
            .forEach { fragment ->
                assertFalse(
                    "No CMS document may render in the $state state, but “$fragment” did",
                    rendered.contains(fragment),
                )
            }
    }

    private fun assertNoTextRendered(text: String) {
        assertTrue(
            "“$text” should not render in this state",
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * Every classic-View text currently on screen, joined. The legal document
     * lives in an `AndroidView`/`TextView` (so HTML headings, lists and links
     * render), which the Compose semantics tree cannot see.
     */
    private fun renderedAndroidViewText(): String {
        val builder = StringBuilder()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .forEach { activity -> collectViewText(activity.window.decorView, builder) }
        }
        return builder.toString()
    }

    private fun collectViewText(view: View, out: StringBuilder) {
        if (view is TextView) out.append(view.text).append('\n')
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectViewText(view.getChildAt(index), out)
        }
    }

    private fun setAccordionCard(titleEndGap: androidx.compose.ui.unit.Dp, tag: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    AccordionCard(
                        title = "Overview",
                        expanded = false,
                        onToggle = {},
                        titleEndGap = titleEndGap,
                        testTagPrefix = tag,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun setLegalHtmlContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    More2OuterCard {
                        Column(Modifier.padding(Spacing.md)) {
                            LegalHtmlContent(
                                html = """
                                # Shipping Terms

                                Body copy stays muted while the parsed heading follows
                                Swift textDarkTitle in the active theme.
                                """.trimIndent(),
                                modifier = Modifier.testTag("legal-html"),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    /**
     * The CMS endpoints, faked at the [More2Api] seam so the production
     * [More2Repository] — including its raw-body `parseCmsBody` decode — is the
     * code under test. A null body means the request fails, which is what the
     * screens have to survive without inventing a document.
     */
    private class FakeLegalApi(
        private val faqItems: List<FaqItem> = emptyList(),
        private val termsGate: CompletableDeferred<Unit>? = null,
        private val privacyGate: CompletableDeferred<Unit>? = null,
    ) : More2Api {
        val termsCalls = AtomicInteger()
        val privacyCalls = AtomicInteger()
        val faqCalls = AtomicInteger()

        // CMS bodies come back as raw HTML/markdown, not JSON — the same shape
        // More2Repository.parseCmsBody has to cope with in production.
        private val cmsMediaType = "text/html; charset=utf-8".toMediaType()

        @Volatile
        private var termsBody: String? = null

        @Volatile
        private var privacyBody: String? = null

        fun serveTerms(body: String) {
            termsBody = body
        }

        fun servePrivacy(body: String) {
            privacyBody = body
        }

        override suspend fun termsContent(): ResponseBody {
            termsCalls.incrementAndGet()
            termsGate?.await()
            val body = termsBody ?: throw IOException("GET /content/terms-conditions failed")
            return body.toResponseBody(cmsMediaType)
        }

        override suspend fun privacyContent(): ResponseBody {
            privacyCalls.incrementAndGet()
            privacyGate?.await()
            val body = privacyBody ?: throw IOException("GET /content/privacy-policy failed")
            return body.toResponseBody(cmsMediaType)
        }

        override suspend fun faqs(): Paginated<FaqItem> {
            faqCalls.incrementAndGet()
            return Paginated(items = faqItems)
        }

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope = throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in LegalContentParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in LegalContentParityTest")
    }

    private companion object {
        const val TERMS_ERROR = "We couldn't load the Terms & Conditions."
        const val PRIVACY_ERROR = "We couldn't load the Privacy Policy."

        /**
         * Markers, not prose: they prove the bytes on screen came from the
         * fetch and could not have been authored by the client.
         */
        const val LIVE_TERMS_MARKER = "LIVE-TERMS-FROM-CMS"
        const val LIVE_PRIVACY_MARKER = "LIVE-PRIVACY-FROM-CMS"

        val LIVE_TERMS_DOCUMENT = """
            # AirDrop Terms

            LIVE-TERMS-FROM-CMS is the body GET /content/terms-conditions
            returned, rendered verbatim.
        """.trimIndent()

        val LIVE_PRIVACY_DOCUMENT = """
            # AirDrop Privacy

            LIVE-PRIVACY-FROM-CMS is the body GET /content/privacy-policy
            returned, rendered verbatim.
        """.trimIndent()

        /**
         * The deleted fallback, spelled out. "etoy" is the whole reason it was
         * deleted; the other two are the copy that came with it, so a partial
         * reintroduction fails here too.
         */
        val DELETED_ETOY_STRINGS = listOf(
            "etoy",
            "exchange or giving away of toys",
            "declutter your home",
        )

        val FIXTURE_FAQ = FaqItem(
            id = "1",
            question = "How long does shipping take?",
            answer = "Air packages clear within two business days of arrival.",
        )
    }
}
