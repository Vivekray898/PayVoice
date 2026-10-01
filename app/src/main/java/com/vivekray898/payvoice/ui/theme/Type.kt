package com.vivekray898.payvoice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale from DESIGN.md. Sohne is proprietary — SansSerif stands in at
 * the reference's weights; the Light display tiers and negative tracking are
 * the typographic signature and are preserved exactly.
 */
private val PvFamily = FontFamily.SansSerif

val PvTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 55.sp,
        letterSpacing = (-0.96).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.64).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 26.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.26).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.22).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 18.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Light,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.39).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PvFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)
