package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A payment must be labelled with the currency the SERVER sent, never with an
 * assumed dollar sign.
 *
 * ⚠️ THE DEFECT THIS PINS. `ShipmentsFormat.price` hard-codes `"$"`, and the
 * Payments card used it for `payment.totalAmount`. An NCB card payment of
 * JA$64,841.58 (≈US$400) therefore rendered as **"$64,841.58"** — a customer
 * reading their payment history saw what looked like a US charge, wrong by a
 * factor of about 162. The currency was decoded and sitting right there in
 * `Payments.kt:33` the whole time.
 *
 * ⚠️ AND IT WAS ALREADY FIXED ON iOS — TWO MONTHS EARLIER.
 * `FigmaPaymentsViewController.formatPrice` carries the reason verbatim:
 * "Kemar 2026-05-23: this used to be hardcoded \"$\" via en_US currency format,
 * so a JMD payment rendered as \"$1234.56\". Use the payment's actual currency
 * code as the prefix." Android kept the bug because nothing compared the two
 * platforms until a cross-platform audit did.
 *
 * The shape here matches Swift exactly, including for USD: code, space, amount.
 */
class PaymentCurrencyFormatTest {

    @Test
    fun `a JMD payment is never labelled with a dollar sign`() {
        val rendered = ShipmentsFormat.priceIn(64841.58, "JMD")

        assertEquals("JMD 64,841.58", rendered)
        assertFalse(
            "an NCB payment wearing a '$' reads as ~162x its real value",
            rendered.contains("$"),
        )
    }

    /** Swift prefixes the code for USD too — parity means Android does as well. */
    @Test
    fun `a USD payment carries its code, matching Swift`() {
        assertEquals("USD 400.00", ShipmentsFormat.priceIn(400.0, "USD"))
    }

    /** Lower-case or padded codes come back from the server; normalise them. */
    @Test
    fun `the server's code is normalised, not trusted verbatim`() {
        assertEquals("JMD 1,000.00", ShipmentsFormat.priceIn(1000.0, " jmd "))
    }

    /**
     * Absent currency falls back to USD — which is what Swift does, and is the
     * ONE guess in this function. It is a default, not knowledge; if the server
     * ever starts omitting the field for JMD payments this fallback becomes the
     * bug again, so it is stated rather than hidden.
     */
    @Test
    fun `a missing or blank currency falls back to USD like Swift`() {
        assertEquals("USD 12.50", ShipmentsFormat.priceIn(12.5, null))
        assertEquals("USD 12.50", ShipmentsFormat.priceIn(12.5, "   "))
    }

    /** No amount is not an amount of zero. */
    @Test
    fun `a null amount stays a dash`() {
        assertEquals("-", ShipmentsFormat.priceIn(null, "JMD"))
    }

    /**
     * `price` keeps its dollar sign on purpose: its remaining callers are
     * explicitly USD fields (`regularPriceUsd`, `salePriceUsd`). Pinned so a
     * later "cleanup" does not unify them and silently reintroduce this bug on
     * the Payments card.
     */
    @Test
    fun `price still formats explicit USD fields with a dollar sign`() {
        assertEquals("$1,550.00", ShipmentsFormat.price(1550.0))
    }
}
