package com.only.human.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.only.human.design.Motion
import com.only.human.design.colors
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.World

/**
 * First run — the app's first impression, so it's done properly: three small
 * steps in the app's own voice (name, @handle, color), then the world is
 * written around you and Sofia's post already says hello.
 */
@Composable
fun Onboarding() {
    val c = colors
    val t = type
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableIntStateOf(0) }

    val nameValid = name.trim().isNotEmpty()
    val handleValid = handle.length >= 3

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.ime),
    ) {
        // Top row: back chevron on the left, progress dots centered.
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            if (step > 0) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                    IOSBackButton { step-- }
                }
            }
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (i <= step) c.accent else c.fillSecondary),
                    )
                }
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(Motion.screen) { dir * it } + fadeIn(Motion.screenFade)) togetherWith
                    (slideOutHorizontally(Motion.screen) { -dir * it / 3 } + fadeOut(tween(200)))
            },
            label = "onboard-step",
            modifier = Modifier.weight(1f),
        ) { s ->
            when (s) {
                0 -> WelcomeStep()
                1 -> NameStep(
                    name = name,
                    onName = { name = it },
                    onDone = { if (nameValid) step++ },
                )
                2 -> HandleStep(
                    handle = handle,
                    onHandle = { raw ->
                        handle = raw.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(16)
                    },
                    onDone = { if (handleValid) step++ },
                )
                else -> AvatarStep(
                    name = name,
                    avatar = avatar,
                    onAvatar = { avatar = it },
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            IOSFilledButton(
                text = when (step) {
                    0 -> "Meet your neighbors"
                    1 -> "Continue"
                    2 -> "Continue"
                    else -> "Move in"
                },
                enabled = when (step) {
                    1 -> nameValid
                    2 -> handleValid
                    else -> true
                },
                onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        World.completeOnboarding(name.trim(), handle.trim(), avatar)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (step == 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Fifteen residents. One of them is you.",
                    style = t.footnote,
                    color = c.labelSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val c = colors
    val t = type
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
            listOf(Cast.maya, Cast.nina, Cast.jordan).forEach { p ->
                MonogramAvatar(p.gradient, p.initials, 72.dp)
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("Only Human", style = t.largeTitle, color = c.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "A tiny network with fifteen residents.\nThey're about to notice you.",
            style = t.body,
            color = c.labelSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onDone: () -> Unit) {
    val c = colors
    val t = type
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(30.dp))
        Text("What should they call you?", style = t.title2, color = c.label)
        Spacer(Modifier.height(6.dp))
        Text(
            "This is the name they'll use when they mention you.",
            style = t.subheadline,
            color = c.labelSecondary,
        )
        Spacer(Modifier.height(20.dp))
        OnboardField(
            value = name,
            onValueChange = onName,
            placeholder = "Your name",
            maxChars = 24,
            onDone = onDone,
        )
    }
}

@Composable
private fun HandleStep(handle: String, onHandle: (String) -> Unit, onDone: () -> Unit) {
    val c = colors
    val t = type
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(30.dp))
        Text("Pick a username", style = t.title2, color = c.label)
        Spacer(Modifier.height(6.dp))
        Text(
            "3–16 characters — letters, numbers, underscores.",
            style = t.subheadline,
            color = c.labelSecondary,
        )
        Spacer(Modifier.height(20.dp))
        OnboardField(
            value = handle,
            onValueChange = onHandle,
            placeholder = "username",
            prefix = "@",
            maxChars = 16,
            onDone = onDone,
        )
        if (handle.length >= 3) {
            Spacer(Modifier.height(12.dp))
            Text(
                "They'll mention you as @$handle.",
                style = t.footnote,
                color = c.labelSecondary,
            )
        }
    }
}

@Composable
private fun AvatarStep(name: String, avatar: Int, onAvatar: (Int) -> Unit) {
    val c = colors
    val t = type
    val initials = name.trim().take(1).uppercase().ifEmpty { "Y" }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        Text("Pick your color", style = t.title2, color = c.label)
        Spacer(Modifier.height(6.dp))
        Text(
            "Your avatar is your initial over a gradient. No photos on this network.",
            style = t.subheadline,
            color = c.labelSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        listOf(0, 1, 2, 3, 4, 5).chunked(3).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                chunk.forEach { i ->
                    val selected = avatar == i
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.iosClickable(scaleTo = 0.92f, onClick = { onAvatar(i) }),
                    ) {
                        if (selected) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(c.fillSecondary),
                            )
                        }
                        MonogramAvatar(Cast.avatarPalettes[i], initials, 58.dp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** A plain iOS-style input: filled, rounded, optionally prefixed. */
@Composable
private fun OnboardField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    prefix: String? = null,
    maxChars: Int,
    onDone: () -> Unit = {},
) {
    val c = colors
    val t = type
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.fill)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(prefix, style = t.body, color = c.labelSecondary)
            Spacer(Modifier.size(2.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = t.body, color = c.labelTertiary, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(maxChars)) },
                singleLine = true,
                textStyle = t.body.copy(color = c.label),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
    }
}
