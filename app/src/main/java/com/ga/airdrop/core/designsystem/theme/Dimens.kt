package com.ga.airdrop.core.designsystem.theme

import androidx.compose.ui.unit.dp

/*
 * Spacing / radius scales — RN canonical (Utils.ts) == Figma spacing
 * variables. Use verbatim; do not approximate.
 */

object Spacing {
    val zero = 0.dp
    val xs = 5.dp    // Figma Spaceing-4xs
    val sm = 10.dp   // Figma Spaceing-3xs
    val sm1 = 15.dp  // Figma Spaceing-2xs
    val md = 20.dp   // Figma Spaceing-1xs
    val lg = 30.dp   // Figma Spaceing-s
    val xl = 40.dp   // Figma Spaceing-m
    val xxl = 50.dp
    val xxxl = 60.dp // Figma Spaceing-xl
}

object Radius {
    val zero = 0.dp
    val xxs = 5.dp   // RN radius_2xs
    val xs = 10.dp   // RN radius_xs
    val s = 15.dp    // RN radius_s — standard outer card
    val m = 20.dp    // RN radius_m — warehouse + activity cards
    val l = 25.dp    // RN radius_l
    val xl = 30.dp   // RN radius_xl
    val full = 999.dp
}
