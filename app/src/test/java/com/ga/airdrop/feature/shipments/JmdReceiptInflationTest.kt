package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 162x receipt.
 *
 * `dual` / `usdJmdPlain` MULTIPLY by the exchange rate — they assume USD input,
 * and the KDoc above `dual` says so: *"Never pass an already-converted JMD
 * figure in here or it is multiplied twice."*
 *
 * The NCB rail hardcodes `currency = "JMD"`, so `Payment.totalAmount` on that
 * rail is already Jamaican dollars. Both payment-detail screens passed it into
 * the USD helpers and never once read `Payment.currency`, which sits right
 * beside the amount.
 *
 * A customer who paid **JMD 50,000** was shown **JMD 8,100,000**. The payments
 * LIST rendered it correctly, so the list and the detail screen disagreed about
 * the same payment.
 *
 * Found on iOS by SwiftHawk (fixed there at 8da77cc), confirmed independently in
 * Kotlin, and verified by BrightHarbor before any edit.
 */
class JmdReceiptInflationTest {

    private val rate = 162.0

    // ── The defect itself ───────────────────────────────────────────────────

    @Test
    fun `a JMD payment is NOT multiplied — the 162x receipt`() {
        val shown = ShipmentsFormat.dualForCurrency(50_000.0, "JMD", rate)

        assertTrue(
            "JMD 50,000 must render as itself, not 8,100,000. Got: $shown",
            shown.contains("JMD 50000.00"),
        )
        assertTrue(
            "and must show the real USD equivalent, 50000/162. Got: $shown",
            shown.contains("USD 308.64"),
        )
        assertTrue(
            "the inflated figure must not appear anywhere. Got: $shown",
            !shown.contains("8100000"),
        )
    }

    @Test
    fun `a USD payment still passes through and multiplies as before`() {
        val shown = ShipmentsFormat.dualForCurrency(308.64, "USD", rate)
        assertTrue("USD must be unchanged. Got: $shown", shown.contains("USD 308.64"))
        assertTrue("and gain its JMD companion. Got: $shown", shown.contains("JMD 49999.68"))
    }

    @Test
    fun `the SAME purchase renders identically whichever currency the server reports it in`() {
        // JMD 50,000 and USD 308.64 are the same money at this rate. A customer
        // must not be able to tell which rail they used from the number shown.
        val fromJmd = ShipmentsFormat.dualForCurrency(50_000.0, "JMD", rate)
        val fromUsd = ShipmentsFormat.dualForCurrency(50_000.0 / rate, "USD", rate)
        assertEquals("the same money must render the same way", fromUsd, fromJmd)
    }

    // ── Absent currency: the wire default ───────────────────────────────────

    @Test
    fun `a null or blank currency is treated as USD — the wire default`() {
        val expected = ShipmentsFormat.dualForCurrency(308.64, "USD", rate)
        assertEquals(expected, ShipmentsFormat.dualForCurrency(308.64, null, rate))
        assertEquals(expected, ShipmentsFormat.dualForCurrency(308.64, "", rate))
        assertEquals(expected, ShipmentsFormat.dualForCurrency(308.64, "  usd  ", rate))
    }

    @Test
    fun `jmd matching is case and whitespace insensitive`() {
        val expected = ShipmentsFormat.dualForCurrency(50_000.0, "JMD", rate)
        assertEquals(expected, ShipmentsFormat.dualForCurrency(50_000.0, "jmd", rate))
        assertEquals(expected, ShipmentsFormat.dualForCurrency(50_000.0, " Jmd ", rate))
    }

    // ── A broken rate must not invent a figure ──────────────────────────────

    @Test
    fun `a zero, negative, NaN or infinite rate shows ONE honest amount, never a fabricated pair`() {
        listOf(0.0, -162.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { bad ->
            val shown = ShipmentsFormat.dualForCurrency(50_000.0, "JMD", bad)
            assertTrue(
                "rate=$bad cannot convert anything; showing a companion figure " +
                    "derived from it would be fiction. Got: $shown",
                shown == "JMD 50000.00",
            )
        }
    }

    // ── An unknown currency must not be guessed ─────────────────────────────

    @Test
    fun `a third currency is shown as itself rather than converted with the wrong rate`() {
        val shown = ShipmentsFormat.dualForCurrency(100.0, "GBP", rate)
        assertEquals(
            "we hold no GBP rate; a fabricated companion amount is worse than " +
                "one honest one",
            "GBP 100.00",
            shown,
        )
    }

    // ── Missing amounts ─────────────────────────────────────────────────────

    @Test
    fun `a missing amount renders the placeholder, exactly as the helpers it replaced did`() {
        // The ONLY delta at the migrated call sites must be currency handling.
        // usdJmdPlain: null -> "-", zero -> a real 0.00 pair.
        assertEquals("-", ShipmentsFormat.dualForCurrency(null, "JMD", rate))
        assertEquals(
            ShipmentsFormat.usdJmdPlain(null, rate),
            ShipmentsFormat.dualForCurrency(null, "USD", rate),
        )
        assertEquals(
            ShipmentsFormat.usdJmdPlain(0.0, rate),
            ShipmentsFormat.dualForCurrency(0.0, "USD", rate),
        )
        // usdJmdPlainPositive: null AND zero -> "-".
        assertEquals("-", ShipmentsFormat.dualForCurrency(null, "JMD", rate, positiveOnly = true))
        assertEquals("-", ShipmentsFormat.dualForCurrency(0.0, "JMD", rate, positiveOnly = true))
        assertEquals(
            ShipmentsFormat.usdJmdPlainPositive(0.0, rate),
            ShipmentsFormat.dualForCurrency(0.0, "USD", rate, positiveOnly = true),
        )
        assertEquals(
            ShipmentsFormat.usdJmdPlainPositive(308.64, rate),
            ShipmentsFormat.dualForCurrency(308.64, "USD", rate, positiveOnly = true),
        )
    }

    // ── Nothing but currency handling changed ───────────────────────────────

    @Test
    fun `for USD input the new helper is byte-identical to both helpers it replaced`() {
        // The migration must be a no-op for every existing USD payment. If this
        // ever drifts, the change was wider than "handle JMD".
        val amounts = listOf(0.01, 1.0, 308.64, 403.35, 64_841.58, 999_999.99)
        amounts.forEach { a ->
            assertEquals(
                "usdJmdPlain parity at $a",
                ShipmentsFormat.usdJmdPlain(a, rate),
                ShipmentsFormat.dualForCurrency(a, "USD", rate),
            )
            assertEquals(
                "usdJmdPlainPositive parity at $a",
                ShipmentsFormat.usdJmdPlainPositive(a, rate),
                ShipmentsFormat.dualForCurrency(a, "USD", rate, positiveOnly = true),
            )
        }
    }

    // ── The regression guard that would have caught this ────────────────────

    @Test
    fun `passing a JMD amount to the USD-only helper still inflates — which is why the currency-aware one exists`() {
        // Not a bug report: this documents WHY the old call sites were wrong, so
        // nobody "simplifies" dualForCurrency back into usdJmdPlain later.
        val naive = ShipmentsFormat.usdJmdPlain(50_000.0, rate)
        assertTrue(
            "usdJmdPlain multiplies by design — it is correct ONLY for USD input",
            naive.contains("8100000"),
        )
    }
}

/**
 * The other half of the same defect: the Product (auction) payment screen picks
 * its total from three different fields, and only ONE of them carries a
 * currency off the wire. Labelling the wrong one "JMD" re-creates the 162x bug
 * from the opposite direction.
 */
class ProductPaymentTotalPrecedenceTest {

    private val rate = 162.0

    private fun payment(amount: Double?, currency: String?) =
        ShipmentPayment(id = 1, totalAmount = amount, currency = currency)

    private fun order(sale: Double? = null, invoice: Double? = null) =
        ShipmentOrder(id = 1, salePriceUsd = sale, invoiceAmountUsd = invoice)

    private fun render(state: ProductPaymentDetailsUiState) =
        ShipmentsFormat.dualForCurrency(state.totalAmount, state.totalCurrency, state.effectiveRate)

    @Test
    fun `an NCB payment with no sale price keeps its own JMD currency`() {
        val state = ProductPaymentDetailsUiState(
            payment = payment(50_000.0, "JMD"),
            order = order(),
            exchangeRate = rate,
        )
        assertEquals(50_000.0, state.totalAmount!!, 0.001)
        assertEquals("JMD", state.totalCurrency)
        assertEquals("USD 308.64 / JMD 50000.00", render(state))
    }

    @Test
    fun `a sale price is USD by contract even when the payment beside it says JMD`() {
        // The trap: `totalCurrency = payment.currency` unconditionally would tag a
        // USD sale price as Jamaican and divide it by 162.
        val state = ProductPaymentDetailsUiState(
            payment = payment(50_000.0, "JMD"),
            order = order(sale = 308.64),
            exchangeRate = rate,
        )
        assertEquals(308.64, state.totalAmount!!, 0.001)
        assertEquals(
            "salePriceUsd is USD by contract — the field name says so",
            "USD",
            state.totalCurrency,
        )
        assertEquals("USD 308.64 / JMD 49999.68", render(state))
    }

    @Test
    fun `the invoice fallback is USD too`() {
        val state = ProductPaymentDetailsUiState(
            payment = payment(null, "JMD"),
            order = order(invoice = 400.0),
            exchangeRate = rate,
        )
        assertEquals(400.0, state.totalAmount!!, 0.001)
        assertEquals("USD", state.totalCurrency)
        assertEquals("USD 400.00 / JMD 64800.00", render(state))
    }

    @Test
    fun `Amount Paid and Total read one precedence, so the two rows cannot disagree`() {
        // The screen used to inline `payment.totalAmount` first for one row while
        // the state put `salePriceUsd` first for the other — same purchase, two
        // numbers, nothing flagging it.
        val state = ProductPaymentDetailsUiState(
            payment = payment(50_000.0, "JMD"),
            order = order(sale = 308.64, invoice = 400.0),
            exchangeRate = rate,
        )
        assertEquals(state.totalAmount, state.totalUsd)
    }

    @Test
    fun `the payment's own rate wins over the order's and the cached fallback`() {
        val state = ProductPaymentDetailsUiState(
            payment = ShipmentPayment(id = 1, totalAmount = 50_000.0, currency = "JMD", exchangeRate = 162.0),
            order = ShipmentOrder(id = 1, exchangeRate = 155.0),
            exchangeRate = 160.0,
        )
        assertEquals(162.0, state.effectiveRate, 0.001)
        assertEquals("USD 308.64 / JMD 50000.00", render(state))
    }
}

/**
 * #218 fixed the two payment-DETAIL screens and I reported that the payments
 * LIST was already correct. **It was not.** Both were wrong, in the same way.
 *
 * What I actually saw was two screens disagreeing — and I concluded the one I
 * had not read must be the right one. `ShipmentsUi.kt:1013` was calling
 * `dual()`, which multiplies, and never read `payment.currency`. They differed
 * only because their exchange-rate fallbacks differed, not because one of them
 * was correct.
 *
 * Worse, #218 left a SECOND site on a screen it did edit: "Amount Paid" became
 * currency-aware while the "Total" box two rows below kept calling
 * `usdJmdPlain`. One screen, one payment, two different numbers.
 *
 * These tests pin the exact precedence both remaining sites use, so a third
 * pass cannot miss a fourth site.
 */
class PaymentPackageTotalPrecedenceTest {

    private val rate = 162.0

    private fun state(
        paid: Double? = null,
        currency: String? = null,
        chargesTotal: Double? = null,
    ) = PaymentPackageDetailsUiState(
        payment = paid?.let { ShipmentPayment(id = 1, totalAmount = it, currency = currency) },
        detail = chargesTotal?.let {
            ShipmentPackageDetail(id = 1, additionalChargesTotal = it)
        },
        exchangeRate = rate,
    )

    private fun render(s: PaymentPackageDetailsUiState) =
        ShipmentsFormat.dualForCurrency(s.totalAmount, s.totalCurrency, s.effectiveRate)

    @Test
    fun `an NCB total keeps its JMD currency and is NOT multiplied`() {
        val s = state(paid = 50_000.0, currency = "JMD")
        assertEquals("JMD", s.totalCurrency)
        assertEquals("USD 308.64 / JMD 50000.00", render(s))
    }

    @Test
    fun `the package charges fallback is USD by contract`() {
        // additionalCharges* feed the Breakdown table, which renders them under
        // an explicit "USD" header and multiplies for its JMD column. Tagging
        // them with a payment's JMD currency would divide them by 162.
        val s = state(chargesTotal = 400.0)
        assertEquals(400.0, s.totalAmount!!, 0.001)
        assertEquals("USD", s.totalCurrency)
        assertEquals("USD 400.00 / JMD 64800.00", render(s))
    }

    @Test
    fun `a JMD payment does not leak its currency onto the USD charges fallback`() {
        // The payment wins the precedence, so the currency must follow the
        // payment — but if the payment amount is absent, the currency must NOT
        // carry over to the charges figure.
        val s = state(paid = null, currency = "JMD", chargesTotal = 400.0)
        assertEquals("USD", s.totalCurrency)
        assertEquals("USD 400.00 / JMD 64800.00", render(s))
    }

    @Test
    fun `Amount Paid and Total render the SAME string for the same payment`() {
        // The defect this class exists for: one screen, one payment, two numbers.
        val s = state(paid = 50_000.0, currency = "JMD")
        val amountPaidRow = ShipmentsFormat.dualForCurrency(
            s.payment!!.totalAmount, s.payment!!.currency, s.effectiveRate,
        )
        assertEquals("the two rows must agree", amountPaidRow, render(s))
    }

    @Test
    fun `the payments LIST renders a JMD payment identically to the detail screen`() {
        // ShipmentsUi.kt:1013 — the site I wrongly reported as already correct.
        val payment = ShipmentPayment(
            id = 1, totalAmount = 50_000.0, currency = "JMD", exchangeRate = rate,
        )
        val listCell = ShipmentsFormat.dualForCurrency(
            payment.totalAmount, payment.currency, payment.exchangeRate ?: rate,
        )
        assertEquals(
            "list and detail must not disagree about the same payment",
            render(state(paid = 50_000.0, currency = "JMD")),
            listCell,
        )
        assertTrue("must not be inflated. Got: $listCell", !listCell.contains("8100000"))
    }
}
