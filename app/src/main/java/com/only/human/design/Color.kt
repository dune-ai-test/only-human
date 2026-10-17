package com.only.human.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * iOS system palette, translated 1:1 (light + dark).
 * Values match Apple's Human Interface Guidelines system colors.
 */
data class OnlyColors(
    val isDark: Boolean,
    val accent: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    // Backgrounds
    val background: Color,      // systemBackground
    val grouped: Color,         // systemGroupedBackground
    val card: Color,            // secondarySystemGroupedBackground (rows, cards)
    val cardSecondary: Color,   // tertiary fill inside cards
    // Labels
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    // Lines & fills
    val separator: Color,
    val separatorOpaque: Color,
    val fill: Color,
    val fillSecondary: Color,
    // Interaction
    val press: Color,
    val iconGray: Color,
)

val LightColors = OnlyColors(
    isDark = false,
    accent = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    background = Color(0xFFFFFFFF),
    grouped = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    cardSecondary = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    labelSecondary = Color(0x993C3C43),
    labelTertiary = Color(0x4D3C3C43),
    separator = Color(0x293C3C43),
    separatorOpaque = Color(0xFFC6C6C8),
    fill = Color(0x1F767680),
    fillSecondary = Color(0x3E767680),
    press = Color(0x0F000000),
    iconGray = Color(0xFF8E8E93),
)

val DarkColors = OnlyColors(
    isDark = true,
    accent = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    background = Color(0xFF000000),
    grouped = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    cardSecondary = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    labelSecondary = Color(0x99EBEBF5),
    labelTertiary = Color(0x4DEBEBF5),
    separator = Color(0x59545458),
    separatorOpaque = Color(0xFF38383A),
    fill = Color(0x1F767680),
    fillSecondary = Color(0x3E767680),
    press = Color(0x14FFFFFF),
    iconGray = Color(0xFF8E8E93),
)

val LocalOnlyColors = staticCompositionLocalOf { LightColors }
