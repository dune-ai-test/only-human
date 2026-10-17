package com.only.human.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Monogram avatar: initials over a soft two-tone gradient.
 * This is a text-only network — no photos, no generated faces.
 */
@Composable
fun MonogramAvatar(
    gradient: List<Color>,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { (size.toPx() * 0.40f).toSp() }
    val fontSizeLarge = with(LocalDensity.current) { (size.toPx() * 0.36f).toSp() }
    val weight = if (initials.length > 2) FontWeight.Medium else FontWeight.SemiBold

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = if (gradient.size >= 2) gradient else listOf(Color.Gray, Color.Gray),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    ) {
        // Soft top light — gives the flat gradient a bit of depth.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(0f, 160f),
                    ),
                ),
        )
        Text(
            text = initials,
            color = Color.White,
            fontSize = if (initials.length > 2) fontSizeLarge else fontSize,
            fontWeight = weight,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
