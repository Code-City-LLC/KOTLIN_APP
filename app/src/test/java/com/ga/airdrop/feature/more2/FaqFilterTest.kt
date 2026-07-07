package com.ga.airdrop.feature.more2

import com.ga.airdrop.data.model.FaqItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [filterFaqs] parity with Swift FigmaFAQViewController.filteredList(for:):
 * blank query returns everything; otherwise question OR answer contains the
 * trimmed, lowercased query.
 */
class FaqFilterTest {

    private val faqs = listOf(
        FaqItem(id = "1", question = "How do I sign up?", answer = "Click the SignUp button."),
        FaqItem(id = "2", question = "Weight limits?", answer = "We carry almost anything."),
        FaqItem(id = "3", question = "Mailing address?", answer = "Fort Lauderdale, Florida."),
    )

    @Test
    fun `blank query returns the full list`() {
        assertEquals(faqs, filterFaqs(faqs, ""))
        assertEquals(faqs, filterFaqs(faqs, "   "))
    }

    @Test
    fun `matches on the question text, case-insensitive`() {
        val out = filterFaqs(faqs, "SIGN")
        assertEquals(1, out.size)
        assertEquals("1", out.first().id)
    }

    @Test
    fun `matches on the answer text too`() {
        val out = filterFaqs(faqs, "florida")
        assertEquals(1, out.size)
        assertEquals("3", out.first().id)
    }

    @Test
    fun `query is trimmed before matching`() {
        assertEquals("2", filterFaqs(faqs, "  weight  ").first().id)
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(filterFaqs(faqs, "zzz-nothing").isEmpty())
    }
}
