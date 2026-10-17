package com.only.human.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.OhIcons
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.World

/**
 * A story, full screen: their gradient behind it, the words on top, and a
 * progress bar that moves on by itself — tap right for the next person,
 * left to go back. Marked seen the moment it's opened.
 */
@Composable
fun StoryViewer(startId: String, onClose: () -> Unit) {
    // Snapshot at open: watching flips the seen-flags and reorders the row.
    val sequence = remember { World.freshStories() }
    var index by remember {
        mutableIntStateOf(sequence.indexOfFirst { it.personaId == startId }.coerceAtLeast(0))
    }
    val story = sequence.getOrNull(index)
    if (story == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val t = type
    val persona = Cast.byId(story.personaId)
    val progress = remember(story.id) { Animatable(0f) }

    LaunchedEffect(story.id) {
        World.markStorySeen(story.id)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 12_000, easing = LinearEasing))
        if (index < sequence.lastIndex) index++ else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(persona.gradient)),
    ) {
        // Invisible tap zones: left third back, right two-thirds on.
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(story.id) {
                        detectTapGestures { if (index > 0) index-- }
                    },
            )
            Box(
                Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .pointerInput(story.id) {
                        detectTapGestures { if (index < sequence.lastIndex) index++ else onClose() }
                    },
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sequence.forEachIndexed { i, _ ->
                    val fill = when {
                        i < index -> 1f
                        i > index -> 0f
                        else -> progress.value
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                    ) {
                        if (fill > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fill)
                                    .fillMaxHeight()
                                    .background(Color.White),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonogramAvatar(persona.gradient, persona.initials, 32.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    persona.name,
                    style = t.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Spacer(Modifier.width(6.dp))
                Text("@${persona.handle}", style = t.footnote, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1f))
                Icon(
                    OhIcons.Xmark,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .iosClickable(scaleTo = 0.9f, onClick = onClose),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                story.text,
                style = t.title2.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Spacer(Modifier.height(120.dp))
        }
    }
}
