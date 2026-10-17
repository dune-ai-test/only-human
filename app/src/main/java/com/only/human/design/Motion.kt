package com.only.human.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Motion specs tuned to feel like iOS, not Material. */
object Motion {
    /** Press / release of buttons and rows. */
    val press = spring<Float>(dampingRatio = 0.82f, stiffness = 900f)

    /** Likes, follows — small overshoot. */
    val bouncyPop = spring<Float>(dampingRatio = 0.45f, stiffness = 700f)

    /** Screen pushes / pops. */
    val screen = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 340, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))

    val screenFade = tween<Float>(durationMillis = 260, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))

    /** Tab switches — quick, barely-there. */
    val tab = tween<Float>(durationMillis = 140)

    /** Segmented control sliding knob. */
    val segment = spring<Float>(dampingRatio = 1f, stiffness = 520f)

    /** Sheets. */
    val sheet = spring<Float>(stiffness = Spring.StiffnessMediumLow)
}
