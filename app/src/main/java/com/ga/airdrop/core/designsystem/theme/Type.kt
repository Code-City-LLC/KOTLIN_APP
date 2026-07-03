package com.ga.airdrop.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R

/*
 * Typography — Cairo family, exact scale from DesignTokens.swift
 * (Figma "Text Style" section, node 22:2710). Sizes and line heights are
 * fixed per style; do not approximate.
 */

val Cairo = FontFamily(
    Font(R.font.cairo_extralight, FontWeight.ExtraLight),
    Font(R.font.cairo_light, FontWeight.Light),
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
    Font(R.font.cairo_extrabold, FontWeight.ExtraBold),
    Font(R.font.cairo_black, FontWeight.Black),
)

object AirdropType {
    private fun style(weight: FontWeight, size: Int, lineHeight: Int) = TextStyle(
        fontFamily = Cairo,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )

    // Heading scale — Cairo Bold
    val h1 = style(FontWeight.Bold, 56, 66)
    val h2 = style(FontWeight.Bold, 48, 58)
    val h3 = style(FontWeight.Bold, 40, 50)
    val h4 = style(FontWeight.Bold, 32, 42)
    val h5 = style(FontWeight.Bold, 24, 34)
    val h6 = style(FontWeight.Bold, 20, 30)

    // Body & Title scale
    val title1 = style(FontWeight.Bold, 18, 28)
    val title2 = style(FontWeight.Bold, 16, 26)
    val subtitle1 = style(FontWeight.SemiBold, 16, 26)
    val subtitle2 = style(FontWeight.SemiBold, 14, 24)
    val subtitle3 = style(FontWeight.SemiBold, 12, 22)
    val body1 = style(FontWeight.Normal, 16, 26)
    val body2 = style(FontWeight.Normal, 14, 22)
    val body3 = style(FontWeight.Normal, 12, 22)
    val button = style(FontWeight.SemiBold, 16, 16)
    val underlineLink = style(FontWeight.SemiBold, 14, 22)
}
