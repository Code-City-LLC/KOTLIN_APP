package com.ga.airdrop.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentsRepositoryNoteTest {

    @Test
    fun `checkout note trims and blank is omitted`() {
        assertEquals("Leave at reception", normalizeCheckoutUserNote("  Leave at reception\n"))
        assertNull(normalizeCheckoutUserNote(" \n\t "))
        assertNull(normalizeCheckoutUserNote(null))
    }
}
