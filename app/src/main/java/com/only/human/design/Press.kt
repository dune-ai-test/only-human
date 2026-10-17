package com.only.human.design

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** A soft iOS-style tick. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
}

/** A firmer confirmation tap. */
@Composable
fun rememberThump(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    }
}

/**
 * iOS-style interaction: dim or shrink on press, no ripple, optional haptic.
 * Everything pressable in the app goes through this so the feel is uniform.
 */
@Composable
fun Modifier.iosClickable(
    enabled: Boolean = true,
    scaleTo: Float = 1f,
    dimTo: Float? = null,
    highlightShape: Shape? = null,
    centeredScale: Boolean = false,
    haptic: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isPressed = pressed && enabled

    val scale by animateFloatAsState(
        targetValue = if (isPressed && scaleTo != 1f) scaleTo else 1f,
        animationSpec = Motion.press,
        label = "press-scale",
    )
    val dim by animateFloatAsState(
        targetValue = if (isPressed && dimTo != null) dimTo else 1f,
        animationSpec = Motion.press,
        label = "press-dim",
    )

    var base = this
        .graphicsLayer {
            if (scaleTo != 1f) {
                scaleX = scale
                scaleY = scale
                if (centeredScale) transformOrigin = TransformOrigin.Center
            }
            if (dimTo != null) alpha = dim
        }

    if (highlightShape != null) {
        val c = LocalOnlyColors.current
        base = base.background(if (isPressed) c.press else androidx.compose.ui.graphics.Color.Transparent, highlightShape)
    }

    return base.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = {
            haptic?.invoke()
            onClick()
        },
    )
}

val RowHighlight = RoundedCornerShape(10.dp)
