package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.config.AirdropFeatureFlags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * JMD checkout must not be reachable while Laravel's NCB completion is
 * non-durable.
 *
 * ## Why this gate exists at all
 *
 * v26 shipped pointing at the retired host, so it cannot reach the API — no
 * customer is completing an NCB payment today. The rail is dead in the field by
 * accident. **v27 fixes the host and re-enables it as a side effect**, so
 * shipping v27 without a gate turns a payment path back on rather than leaving
 * it as-is. BrightHarbor gated the release on exactly that (ORC 88698).
 *
 * Laravel's open P0s at the time of writing (ORC 88519/88520/88588): the replay
 * path checks an `invoice_id` column that does not exist, so the claimed
 * idempotency is dead; and `GET_LOCK` releases on provider timeout or process
 * death while status stays `3ds_complete`, so a retry can call `/payment`
 * again — an unknown-outcome double charge.
 *
 * ## The rule this pins
 *
 * ONE flag governs THREE things, and any two of them drifting apart is the bug:
 *  1. whether JMD is **offered**
 *  2. whether the rail can **initiate**
 *  3. what the customer is **told**
 *
 * Hiding the option alone is NOT sufficient — a JMD flow persisted by an
 * earlier build survives the upgrade and would walk straight into PowerTranz on
 * the next launch. That is why the selector itself fails closed.
 */
class JmdCheckoutGateTest {

    private var original = false

    @Before fun capture() { original = AirdropFeatureFlags.jmdNcbCheckout }

    @After fun restore() { AirdropFeatureFlags.jmdNcbCheckout = original }

    // ── Shipping state: OFF ─────────────────────────────────────────────────

    @Test
    fun `the shipped default is pinned — changing it must be deliberate`() {
        // ⚠️ NOW ASSERTS true. THE REASON IS MEASURED, NOT RELAYED — and the
        // full history is kept because this flag has moved THREE times and the
        // churn is the lesson.
        //
        //  1. ORC 89022 reported the submitted iOS build as shipping ON. I
        //     flipped Kotlin ON on that secondhand report.
        //  2. ORC 89219 contradicted it. Instead of flipping on a second
        //     relay I read Swift's source at the commit behind build
        //     202608020401 — `0446a14 AirdropFeatureFlags.swift:234-235`,
        //     `get { UserDefaults.standard.bool(forKey:) }`, which returns
        //     FALSE for an unset key. So iOS really did ship OFF. I flipped
        //     Kotlin back. 89219 was right, 89022 was wrong.
        //  3. iOS then genuinely CHANGED, and this time it is not a report.
        //     Read from SWIFT_APP `main` (38a11f4), same file, now lines
        //     246-247:
        //
        //       if UserDefaults.standard.object(forKey: Key.ncbJmdCheckout) == nil {
        //           return true
        //       }
        //
        //     Unset now means ON. The commit is `5932f70 feat(ncb): default
        //     JMD/NCB checkout ON — owner-directed override`, Kemar
        //     2026-08-02, explicit and fully-informed.
        //
        // So my step-2 verification was correct WHEN MADE and is now stale.
        // Kotlin matches iOS again — two platforms with opposite defaults on
        // one payment rail is worse than either default, which was the right
        // instinct in step 1 applied to a premise that was false at the time
        // and is true now.
        //
        // The point of this test is unchanged: the shipped default is a
        // DECISION, and flipping it without touching this line is not
        // possible. Whoever changes it has to come here and say why.
        //
        // The safety analysis that argued for OFF is NOT deleted — it lives on
        // AirdropFeatureFlags.jmdNcbCheckout. The owner was shown it and chose
        // to proceed; that is recorded, not erased.
        //
        // Turning it back OFF is one boolean plus this assertion, and every
        // boundary gate below still works in both directions — proven by the
        // gate-OFF cases in this class, which are NOT deleted.
        restore()
        assertEquals(
            "AirdropFeatureFlags.jmdNcbCheckout default changed. iOS ships ON " +
                "(verified in Swift source at main/38a11f4, AirdropFeatureFlags" +
                ".swift:246-247 `object(forKey:) == nil -> return true`, commit " +
                "5932f70 — read, not relayed). Do not diverge on a payment rail " +
                "— read the other platform's SOURCE first.",
            true,
            original,
        )
    }

    @Test
    fun `with the gate OFF a JMD flow cannot select a payment rail`() {
        AirdropFeatureFlags.jmdNcbCheckout = false

        assertNull(
            "a persisted JMD flow from an earlier build must NOT reach PowerTranz " +
                "after upgrading — hiding the popup option does not stop it",
            checkoutPaymentRail("JMD"),
        )
        assertNull(checkoutPaymentRail("jmd"))
        assertNull(checkoutPaymentRail(" Jmd "))
    }

    @Test
    fun `USD and Stripe are completely unaffected by the gate`() {
        // The outage fix must not be held hostage to the payment freeze. USD
        // checkout has to keep working in exactly the same way.
        AirdropFeatureFlags.jmdNcbCheckout = false
        assertEquals(CheckoutPaymentRail.STRIPE, checkoutPaymentRail("USD"))
        assertEquals(CheckoutPaymentRail.STRIPE, checkoutPaymentRail("usd"))
        assertEquals(CheckoutNextRoute.ORDER_SUMMARY, checkoutNextRoute("USD"))
    }

    @Test
    fun `an unknown currency is still rejected, gate or no gate`() {
        AirdropFeatureFlags.jmdNcbCheckout = false
        assertNull(checkoutPaymentRail("CAD"))
        assertNull(checkoutPaymentRail(""))
        assertNull(checkoutPaymentRail(null))
    }

    // ── The other side: turning it on must fully restore the rail ───────────

    @Test
    fun `with the gate ON the JMD rail works exactly as before`() {
        // The gate must be a switch, not a rewrite. When Laravel is green,
        // flipping this one boolean has to restore the previous behaviour
        // completely — otherwise re-enabling becomes its own risky change.
        AirdropFeatureFlags.jmdNcbCheckout = true

        assertEquals(CheckoutPaymentRail.NCB_POWERTRANZ, checkoutPaymentRail("JMD"))
        assertEquals(CheckoutPaymentRail.NCB_POWERTRANZ, checkoutPaymentRail("jmd"))
        assertEquals(CheckoutPaymentRail.STRIPE, checkoutPaymentRail("USD"))
    }

    @Test
    fun `the route is unchanged by the gate — only the RAIL is gated`() {
        // checkoutNextRoute drives navigation, not payment. Gating it too would
        // change screen flow for no safety benefit and make re-enabling riskier.
        AirdropFeatureFlags.jmdNcbCheckout = false
        assertEquals(CheckoutNextRoute.PROFILE_INFORMATION, checkoutNextRoute("JMD"))

        AirdropFeatureFlags.jmdNcbCheckout = true
        assertEquals(CheckoutNextRoute.PROFILE_INFORMATION, checkoutNextRoute("JMD"))
    }
}

/**
 * The gate must hold at EVERY money-movement boundary, not just the cart's.
 *
 * I shipped #227 asserting the gate governed "initiates", and told the room v27
 * "will not ship an open NCB rail". **That was false.**
 * `AuctionCheckoutViewModel` decides the rail itself in a `when` on the
 * currency and never calls `checkoutPaymentRail()`, so an auction checkout in
 * JMD walked straight to NCB card entry with the gate off. Caught by
 * BrightHarbor in read-only review (ORC 88806), not by me.
 *
 * The lesson is the same one as the persisted-flow case: **gating the decision
 * function only covers callers that ask it.** The enforcement has to sit at the
 * boundary where money actually moves — session creation and completion — so a
 * path that never consults the selector still cannot pay.
 *
 * This test pins the INVENTORY of gated boundaries by reading the source, so a
 * new NCB entry point added later without a gate fails the build.
 */
class JmdGateCoversEveryBoundaryTest {

    private fun repoRoot(): java.io.File {
        var d: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            if (java.io.File(d, "app/src/main").isDirectory) return d
            d = d.parentFile
        }
        throw AssertionError("cannot locate repo root — this guard cannot verify anything")
    }

    private fun source(rel: String) = java.io.File(repoRoot(), rel).readText()

    @Test
    fun `every NCB money-movement boundary consults the gate`() {
        val boundaries = listOf(
            "app/src/main/java/com/ga/airdrop/feature/cart/CartViewModel.kt" to
                listOf("createNcbSession", "completeNcbPayment"),
            "app/src/main/java/com/ga/airdrop/feature/shop/AuctionCheckoutViewModel.kt" to
                listOf("createNcbSession", "completeNcbPayment"),
        )

        boundaries.forEach { (path, fns) ->
            val src = source(path)
            fns.forEach { fn ->
                val start = src.indexOf("override fun $fn(")
                assertTrue("$path has no `override fun $fn(`", start >= 0)
                // The gate must appear within the first ~1200 chars of the body,
                // i.e. before any work is done — not buried after a network call.
                val head = src.substring(start, minOf(start + 1200, src.length))
                assertTrue(
                    "$path::$fn does NOT check AirdropFeatureFlags.jmdNcbCheckout " +
                        "before doing work. This is exactly the auction bypass " +
                        "(ORC 88806): money movement reachable without the gate.",
                    head.contains("jmdNcbCheckout"),
                )
            }
        }
    }

    @Test
    fun `the auction currency branch is gated too, not just the cart selector`() {
        val src = source("app/src/main/java/com/ga/airdrop/feature/shop/AuctionCheckoutViewModel.kt")
        val jmdArm = src.indexOf("CheckoutCurrency.JMD ->")
        assertTrue("auction has no JMD branch", jmdArm >= 0)
        val arm = src.substring(jmdArm, minOf(jmdArm + 1200, src.length))
        assertTrue(
            "the auction JMD branch navigates to NCB card entry without consulting " +
                "the gate — the bypass BrightHarbor found",
            arm.contains("jmdNcbCheckout"),
        )
    }
}
