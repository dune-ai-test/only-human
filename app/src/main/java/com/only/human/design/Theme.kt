package com.only.human.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun OnlyHumanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    // MaterialTheme is only provided so Material components we borrow
    // (bottom sheets, text fields) inherit sane defaults. Everything
    // user-facing is styled by hand from OnlyColors / OnlyType.
    val material = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.card,
            onBackground = colors.label,
            onSurface = colors.label,
            outline = colors.separatorOpaque,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.card,
            onBackground = colors.label,
            onSurface = colors.label,
            outline = colors.separatorOpaque,
        )
    }

    CompositionLocalProvider(
        LocalOnlyColors provides colors,
        LocalOnlyType provides OnlyTypography,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

/** Shorthand accessors used across every screen. */
val colors: OnlyColors
    @Composable get() = LocalOnlyColors.current

val type: OnlyType
    @Composable get() = LocalOnlyType.current

@Suppress("unused")
val OnlyWhite = Color.White
