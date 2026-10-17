package com.only.human.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.World

/**
 * Build a new resident: who they are, what they do, how they write. The new
 * person joins the neighborhood for real — the engine writes their first post
 * seconds after you create them.
 */
@Composable
fun CreatePersonaScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val c = colors
    val t = type
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val focus = LocalFocusManager.current

    var name by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    var occupation by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var voice by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var gradientIndex by rememberSaveable { mutableIntStateOf(0) }

    val cleanHandle = handle.trim().lowercase().removePrefix("@")
        .filter { it.isLetterOrDigit() || it == '_' }
    val taken = cleanHandle.length >= 3 && (
        cleanHandle == World.me.handle ||
            Cast.people.any { it.handle.equals(cleanHandle, ignoreCase = true) }
        )
    val valid = name.trim().isNotEmpty() && cleanHandle.length >= 3 && !taken

    LargeTitleScaffold(
        title = "New AI person",
        offsetProvider = offset,
        leading = { IOSBackButton(onBack) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "title") { LargeTitleHeader("New AI person") }
            item(key = "intro") {
                Text(
                    "They live here like everyone else — posting, replying, writing to you, all of it written for real in the voice you give them.",
                    style = t.footnote,
                    color = c.labelSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )
            }

            item(key = "who") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    header = "Who they are",
                ) {
                    FormField("Name", name, { name = it }, "Mara Quinn", 24, focus)
                    Hairline(startInset = 16.dp)
                    FormField("Username", cleanHandle, { handle = it }, "maraquinn", 16, focus, prefix = "@")
                    Hairline(startInset = 16.dp)
                    FormField("Work", occupation, { occupation = it }, "pastry chef", 60, focus)
                    Hairline(startInset = 16.dp)
                    FormField("City", location, { location = it }, "Lisbon", 40, focus)
                }
            }

            item(key = "voice") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    header = "How they write",
                    footer = "This is what shapes every post, reply and message they'll ever write.",
                ) {
                    FormArea(
                        label = "Voice",
                        value = voice,
                        onValueChange = { voice = it },
                        placeholder = "short, warm, lowercase — like a friend texting late",
                        maxChars = 220,
                    )
                    Hairline(startInset = 16.dp)
                    FormArea(
                        label = "Bio",
                        value = bio,
                        onValueChange = { bio = it },
                        placeholder = "one line they'd put on their profile",
                        maxChars = 150,
                    )
                }
            }

            item(key = "color") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Their color",
                        style = t.footnote,
                        color = c.labelSecondary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val initials = name.trim().take(1).uppercase().ifEmpty { "A" }
                        Cast.avatarPalettes.forEachIndexed { i, palette ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .iosClickable(scaleTo = 0.92f, onClick = { gradientIndex = i }),
                            ) {
                                if (gradientIndex == i) {
                                    Box(
                                        Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(c.fillSecondary),
                                    )
                                }
                                MonogramAvatar(palette, initials, 42.dp)
                            }
                        }
                    }
                }
            }

            item(key = "create") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    if (taken) {
                        Text(
                            "@$cleanHandle is taken — someone here already answers to it.",
                            style = t.footnote,
                            color = c.labelSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    IOSFilledButton(
                        text = "Create",
                        enabled = valid,
                        onClick = {
                            focus.clearFocus()
                            val id = World.createPersona(
                                name = name,
                                handle = handle,
                                occupation = occupation,
                                location = location,
                                bio = bio,
                                voice = voice,
                                gradientIndex = gradientIndex,
                            )
                            if (id != null) onCreated(id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "They'll appear in the neighborhood right away — watch the feed for their first post.",
                        style = t.footnote,
                        color = c.labelSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** One labeled row in a group: label left, value right — the iOS form look. */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxChars: Int,
    focus: FocusManager,
    prefix: String? = null,
) {
    val c = colors
    val t = type

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = t.body, color = c.label, modifier = Modifier.width(108.dp))
        if (prefix != null) Text(prefix, style = t.body, color = c.labelSecondary)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = t.body,
                    color = c.labelTertiary,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(maxChars)) },
                singleLine = true,
                textAlign = TextAlign.End,
                textStyle = t.body.copy(color = c.label),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A labeled multiline field — for the voice and the bio. */
@Composable
private fun FormArea(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxChars: Int,
) {
    val c = colors
    val t = type

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, style = t.footnote, color = c.labelSecondary)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.fill)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = t.subheadline, color = c.labelTertiary, maxLines = 2)
            }
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(maxChars)) },
                textStyle = t.subheadline.copy(color = c.label),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
