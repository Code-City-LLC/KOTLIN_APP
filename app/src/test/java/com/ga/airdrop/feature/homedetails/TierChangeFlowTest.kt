package com.ga.airdrop.feature.homedetails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.TierChangeOption
import com.ga.airdrop.data.model.TierChangeResult
import com.ga.airdrop.data.repo.CustomerTierReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier change-flow + CTA contract (#22450 / issue #41): the FULL server
 * available_changes offers own the CTA; PATCH is provisional; only the
 * authoritative GET moves the page; blank/unknown identity fails closed;
 * Inactive/Corporate land via identity codes and show Kemar-ruled static rows.
 */
class TierChangeFlowTest {

    private fun rubyTier(canChange: Boolean = true) = CustomerTier(
        currentTier = "RUBY",
        displayName = "Ruby Starter",
        canChange = canChange,
        availableChanges = listOf(
            TierChangeOption(code = "GOLD", name = "Gold Standard", laneRank = 3, isCurrent = false, direction = "upgrade"),
            TierChangeOption(code = "SAVR", name = "Sapphire Saver", laneRank = 1, isCurrent = false, direction = "downgrade"),
            TierChangeOption(code = "RUBY", name = "Ruby Starter", laneRank = 2, isCurrent = true, direction = "same"),
        ),
    )

    private fun page(code: String) = tierPages.first { it.apiCode == code }

    // ── Offers own the CTA ──────────────────────────────────────────────────

    @Test
    fun `applyCustomerTier folds the authoritative GET into the page state`() {
        val s = applyCustomerTier(GoldPriorityUiState(), rubyTier())
        assertTrue(s.tierConfirmed)
        assertEquals("RUBY", s.currentTierCode)
        assertTrue(s.canChange)
        assertEquals(
            listOf(
                TierOffer("GOLD", "Gold Standard", 3, "upgrade"),
                TierOffer("SAVR", "Sapphire Saver", 1, "downgrade"),
                TierOffer("RUBY", "Ruby Starter", 2, "same"),
            ),
            s.offers,
        )
        assertEquals(tierPages.indexOfFirst { it.apiCode == "RUBY" }, s.resolvedTierIndex)
    }

    @Test
    fun `upgrade CTA exists only for a confirmed tier with a real server offer`() {
        val confirmed = applyCustomerTier(GoldPriorityUiState(), rubyTier())
        assertNull(upgradeOfferFor(confirmed, page("DIAM")))
        assertEquals("GOLD", upgradeOfferFor(confirmed, page("GOLD"))?.code)
        assertNull(upgradeOfferFor(applyCustomerTier(GoldPriorityUiState(), rubyTier(canChange = false)), page("GOLD")))
        val unconfirmed = GoldPriorityUiState(
            canChange = true,
            offers = listOf(TierOffer("GOLD", "Gold Standard", 3, "upgrade")),
        )
        assertNull(upgradeOfferFor(unconfirmed, page("GOLD")))
    }

    @Test
    fun `non-adjacent server offers surface exactly as sent`() {
        val skipTier = CustomerTier(
            currentTier = "RUBY", displayName = "Ruby Starter", canChange = true,
            availableChanges = listOf(
                TierChangeOption(code = "DIAM", name = "Diamond Elite", laneRank = 5, direction = "upgrade"),
            ),
        )
        val s = applyCustomerTier(GoldPriorityUiState(), skipTier)
        assertEquals("DIAM", breakdownUpgradeOffer(s)?.code)
        assertNull(breakdownDowngradeOffer(s))
        assertEquals("DIAM", upgradeOfferFor(s, page("DIAM"))?.code)
        assertNull(upgradeOfferFor(s, page("GOLD")))
    }

    @Test
    fun `server direction contradicting page order still governs`() {
        val contradiction = CustomerTier(
            currentTier = "RUBY", displayName = "Ruby Starter", canChange = true,
            availableChanges = listOf(
                TierChangeOption(code = "SAVR", name = "Sapphire Saver", laneRank = 9, direction = "upgrade"),
                TierChangeOption(code = "GOLD", name = "Gold Standard", laneRank = 0, direction = "downgrade"),
            ),
        )
        val s = applyCustomerTier(GoldPriorityUiState(), contradiction)
        assertEquals("SAVR", upgradeOfferFor(s, page("SAVR"))?.code)
        assertNull(upgradeOfferFor(s, page("GOLD")))
        assertEquals("SAVR", breakdownUpgradeOffer(s)?.code)
        assertEquals("GOLD", breakdownDowngradeOffer(s)?.code)
    }

    @Test
    fun `breakdown targets are the nearest OFFERED step by server lane_rank`() {
        val manyOffers = CustomerTier(
            currentTier = "GOLD", displayName = "Gold Standard", canChange = true,
            availableChanges = listOf(
                TierChangeOption(code = "DIAM", name = "Diamond Elite", laneRank = 5, direction = "upgrade"),
                TierChangeOption(code = "PLAT", name = "Platinum Priority", laneRank = 4, direction = "upgrade"),
                TierChangeOption(code = "SAVR", name = "Sapphire Saver", laneRank = 1, direction = "downgrade"),
                TierChangeOption(code = "RUBY", name = "Ruby Starter", laneRank = 2, direction = "downgrade"),
            ),
        )
        val s = applyCustomerTier(GoldPriorityUiState(), manyOffers)
        assertEquals("PLAT", breakdownUpgradeOffer(s)?.code)
        assertEquals("RUBY", breakdownDowngradeOffer(s)?.code)
    }

    // ── Identity codes + fail-closed ────────────────────────────────────────

    @Test
    fun `benefit rows use complete summary only and preserve server order`() {
        val summary = listOf(
            "Expedited 24-hour processing for all cleared packages.",
            "Free storage for up to 45 days.",
        )
        val rows = serverBenefitRows(
            listOf(
                ServiceTier(
                    code = "PLAT",
                    processingCopy = "24-hour target where capacity allows",
                    benefitsSummary = summary,
                ),
            ),
        )

        assertEquals(summary, rows["PLAT"])
    }

    @Test
    fun `confirmed INACTIVE and CORPORATE land on their own pages`() {
        val inactive = applyCustomerTier(
            GoldPriorityUiState(),
            CustomerTier(currentTier = "inactive", displayName = "Inactive", canChange = false),
        )
        assertTrue(inactive.tierConfirmed)
        assertEquals(tierPages.indexOfFirst { it.id == "inactive" }, inactive.resolvedTierIndex)

        val corporate = applyCustomerTier(
            GoldPriorityUiState(),
            CustomerTier(currentTier = "CORPORATE", displayName = "Corporate", canChange = false),
        )
        assertTrue(corporate.tierConfirmed)
        assertEquals(tierPages.indexOfFirst { it.id == "corporate" }, corporate.resolvedTierIndex)

        // Identity is separate from changeability: neither is a change target.
        assertNull(indexForTier(code = "INACTIVE", name = null))
        assertNull(indexForTier(code = "CORPORATE", name = null))
    }

    @Test
    fun `blank or unknown current code fails closed instead of confirming a guess`() {
        val fallback = GoldPriorityUiState(resolvedTierIndex = 2)
        val blank = applyCustomerTier(fallback, CustomerTier(currentTier = "  ", canChange = true))
        assertFalse(blank.tierConfirmed)
        assertTrue(blank.tierLoadFailed)
        assertEquals(2, blank.resolvedTierIndex)

        val unknown = applyCustomerTier(fallback, CustomerTier(currentTier = "MYSTERY", canChange = true))
        assertFalse(unknown.tierConfirmed)
        assertTrue(unknown.tierLoadFailed)
    }

    // ── Static rows + row states ────────────────────────────────────────────

    @Test
    fun `inactive and corporate render the Kemar-ruled static rows`() {
        val inactive = tierPages.first { it.id == "inactive" }
        val corporate = tierPages.first { it.id == "corporate" }
        val inactiveRows = benefitRowsFor(inactive, emptyMap(), TierCatalogStatus.Failed)
        val corporateRows = benefitRowsFor(corporate, emptyMap(), TierCatalogStatus.Loading)
        assertEquals(4, (inactiveRows as TierBenefitRows.Rows).rows.size)
        assertEquals(7, (corporateRows as TierBenefitRows.Rows).rows.size)
        assertTrue(inactiveRows.rows.first().startsWith("Access to account setup"))
        assertTrue(corporateRows.rows.contains("Dedicated account manager."))
    }

    @Test
    fun `coded tiers never render static copy - loading then failed with retry`() {
        val gold = page("GOLD")
        assertEquals(TierBenefitRows.Loading, benefitRowsFor(gold, emptyMap(), TierCatalogStatus.Loading))
        assertEquals(TierBenefitRows.Failed, benefitRowsFor(gold, emptyMap(), TierCatalogStatus.Failed))
        assertEquals(
            TierBenefitRows.Rows(listOf("Insurance required on every shipment")),
            benefitRowsFor(
                gold,
                mapOf("GOLD" to listOf("Insurance required on every shipment")),
                TierCatalogStatus.Ready,
            ),
        )
    }

    @Test
    fun `tierRelation drives the page-relative CTA state machine`() {
        val gold = tierPages.indexOfFirst { it.id == "gold" }
        val diamond = tierPages.indexOfFirst { it.id == "diamond" }
        val ruby = tierPages.indexOfFirst { it.id == "ruby" }
        val inactive = tierPages.indexOfFirst { it.id == "inactive" }
        val corporate = tierPages.indexOfFirst { it.id == "corporate" }

        assertEquals(TierRelation.Upgrade, tierRelation(diamond, gold))
        assertEquals(TierRelation.Current, tierRelation(gold, gold))
        assertEquals(TierRelation.Downgrade, tierRelation(ruby, gold))
        assertEquals(TierRelation.Preview, tierRelation(corporate, gold))
        assertEquals(TierRelation.Downgrade, tierRelation(inactive, gold))
        assertEquals(TierRelation.Activation, tierRelation(inactive, inactive))
        assertEquals(TierRelation.Preview, tierRelation(gold, null))
    }

    @Test
    fun `tick tint is deterministic per tier and never stark white`() {
        tierPages.forEach { p ->
            assertEquals(lerp(p.gradientTop, Color.White, 0.35f), tierTickColor(p))
            assertNotEquals(Color.White, tierTickColor(p))
        }
    }

    // ── PATCH provisional -> GET authoritative ──────────────────────────────

    private class FakeReader(
        private val changeResult: () -> Result<TierChangeResult>,
        private val tierResult: () -> Result<CustomerTier>,
    ) : CustomerTierReader {
        var patchCalls = 0
        var getCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun serviceTiers() = Result.success(emptyList<ServiceTier>())
        override suspend fun customerTier(): Result<CustomerTier> {
            getCalls++
            return tierResult()
        }
        override suspend fun changeTier(code: String): Result<TierChangeResult> {
            patchCalls++
            gate?.await()
            return changeResult()
        }
    }

    @Test
    fun `repeated tier-change taps produce exactly one PATCH`() = runBlocking {
        val gateway = FakeReader(
            changeResult = { Result.success(TierChangeResult("GOLD")) },
            tierResult = { Result.success(CustomerTier(currentTier = "GOLD", displayName = "Gold Standard", canChange = true)) },
        )
        gateway.gate = CompletableDeferred()
        val state = MutableStateFlow(applyCustomerTier(GoldPriorityUiState(), rubyTier()))

        val first = launch { executeTierChange(state, gateway, "GOLD") }
        yield()
        val second = launch { executeTierChange(state, gateway, "GOLD") }
        second.join()
        gateway.gate?.complete(Unit)
        first.join()

        assertEquals(1, gateway.patchCalls)
        assertEquals("GOLD", state.value.currentTierCode)
    }

    @Test
    fun `awaitingConfirmation rejects any new PATCH - retry GET is the only path`() = runBlocking {
        val gateway = FakeReader(
            changeResult = { Result.success(TierChangeResult("SAVR")) },
            tierResult = { Result.failure(IllegalStateException("still down")) },
        )
        val state = MutableStateFlow(
            applyCustomerTier(GoldPriorityUiState(), rubyTier())
                .copy(
                    awaitingConfirmation = true,
                    awaitingCode = "GOLD",
                    changeError = "unconfirmed",
                ),
        )

        executeTierChange(state, gateway, "SAVR")

        assertEquals(0, gateway.patchCalls)
        assertNull(state.value.changingToCode)
        assertTrue(state.value.awaitingConfirmation)
        assertEquals("GOLD", state.value.awaitingCode)
    }

    @Test
    fun `PATCH success plus GET success commits the confirmed state`() = runBlocking {
        val gateway = FakeReader(
            changeResult = { Result.success(TierChangeResult("GOLD")) },
            tierResult = { Result.success(CustomerTier(currentTier = "GOLD", displayName = "Gold Standard", canChange = true)) },
        )
        val state = MutableStateFlow(applyCustomerTier(GoldPriorityUiState(), rubyTier()))

        executeTierChange(state, gateway, "GOLD")

        assertEquals(1, gateway.patchCalls)
        assertEquals(1, gateway.getCalls)
        assertEquals("GOLD", state.value.currentTierCode)
        assertEquals("Gold Standard", state.value.changeSuccessName)
        assertNull(state.value.changingToCode)
        assertFalse(state.value.awaitingConfirmation)
    }

    @Test
    fun `PATCH success plus GET failure preserves prior state and reports it`() = runBlocking {
        val gateway = FakeReader(
            changeResult = { Result.success(TierChangeResult("GOLD")) },
            tierResult = { Result.failure(IllegalStateException("network down")) },
        )
        val state = MutableStateFlow(applyCustomerTier(GoldPriorityUiState(), rubyTier()))

        executeTierChange(state, gateway, "GOLD")

        assertEquals("RUBY", state.value.currentTierCode)
        assertNull(state.value.changeSuccessName)
        assertTrue(state.value.awaitingConfirmation)
        assertEquals("network down", state.value.changeError)
        assertNull(state.value.changingToCode)

        // Retry heals: the confirmation GET succeeds now and commits.
        val healed = FakeReader(
            changeResult = { error("not used") },
            tierResult = {
                Result.success(
                    CustomerTier(
                        currentTier = "GOLD",
                        displayName = "Server Gold Plus",
                        canChange = true,
                    )
                )
            },
        )
        confirmTierState(state, healed)
        assertEquals("GOLD", state.value.currentTierCode)
        assertFalse(state.value.awaitingConfirmation)
        assertEquals("Server Gold Plus", state.value.changeSuccessName)
    }

    @Test
    fun `a confirmation GET that reports a different tier stays unresolved`() {
        val during = applyCustomerTier(GoldPriorityUiState(), rubyTier())
            .copy(changingToCode = "GOLD")
        val unchanged = applyConfirmedChange(during, rubyTier())
        assertEquals("RUBY", unchanged.currentTierCode)
        assertNull(unchanged.changingToCode)
        assertTrue(unchanged.awaitingConfirmation)
        assertEquals("GOLD", unchanged.awaitingCode)
        assertTrue(unchanged.changeError!!.contains("requested tier"))
        assertNull(unchanged.changeSuccessName)
    }

    @Test
    fun `a confirmation GET with an unknown code keeps the change unresolved`() {
        val during = applyCustomerTier(GoldPriorityUiState(), rubyTier())
            .copy(changingToCode = "GOLD")
        val s = applyConfirmedChange(during, CustomerTier(currentTier = "???", canChange = true))
        assertTrue(s.tierConfirmed)
        assertEquals("RUBY", s.currentTierCode)
        assertTrue(s.awaitingConfirmation)
        assertEquals("GOLD", s.awaitingCode)
        assertNull(s.changingToCode)
        assertNull(s.changeSuccessName)
    }

}
