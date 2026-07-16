package com.ga.airdrop.feature.cart

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ga.airdrop.core.designsystem.theme.Cairo
import org.junit.Assert.assertEquals
import org.junit.Test

class CartHeaderStyleTest {

    @Test
    fun myCartUsesLiveFigmaSubtitle1Typography() {
        assertEquals(Cairo, CartHeaderTitleStyle.fontFamily)
        assertEquals(FontWeight.SemiBold, CartHeaderTitleStyle.fontWeight)
        assertEquals(16.sp, CartHeaderTitleStyle.fontSize)
        assertEquals(26.sp, CartHeaderTitleStyle.lineHeight)
    }
}
