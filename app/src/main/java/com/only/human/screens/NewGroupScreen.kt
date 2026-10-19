package com.only.human.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
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
import com.only.human.design.components.OhIcons
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.Persona

/** Start a room: a name, what it's about, and who's in it. */
@Composable
fun NewGroupScreen(onBack: () -> Unit, onCreate: (String, String, List<String>) -> Unit) {
    val c = colors
    val t = type
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val focus = LocalFocusManager.current

    var name by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val candidates = Cast.people.filterNot { it.id.startsWith("org-") }.sortedBy { it.name.lowercase() }
    val valid = name.isNotBlank() && selected.isNotEmpty()

    LargeTitleScaffold(
        title = "New Group",
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
            item(key = "title") { LargeTitleHeader("New Group") }
            item(key = "intro") {
                Text(
                    "A few people, one conversation. Tag anyone with @ and they answer.",
                    style = t.footnote,
                    color = c.labelSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )
            }

            item(key = "about") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    header = "About the room",
                ) {
                    FormField("Name", name, { name = it }, "Weekend plans", 60, focus)
                    Hairline(startInset = 16.dp)
                    FormField("Topic", topic, { topic = it }, "what the room is about", 140, focus)
                }
            }

            item(key = "members") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    header = "Members",
                    footer = "Everyone here answers when the room talks.",
                ) {
                    candidates.forEachIndexed { index, persona ->
                        MemberRow(
                            persona = persona,
                            selected = selected.contains(persona.id),
                            showDivider = index != candidates.lastIndex,
                            onClick = {
                                if (selected.contains(persona.id)) {
                                    selected.remove(persona.id)
                                } else {
                                    selected.add(persona.id)
                                }
                            },
                        )
                    }
                }
            }

            item(key = "create") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    IOSFilledButton(
                        text = "Create Group",
                        enabled = valid,
                        onClick = {
                            focus.clearFocus()
                            onCreate(name.trim(), topic.trim(), selected.toList())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** One row in the members list — tap to bring them in or leave them out. */
@Composable
private fun MemberRow(
    persona: Persona,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val c = colors
    val t = type
    Column(
        Modifier
            .fillMaxWidth()
            .iosClickable(highlightShape = RoundedCornerShape(0.dp), onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonogramAvatar(persona.gradient, persona.initials, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(persona.name, style = t.body, color = c.label, maxLines = 1)
                Text("@${persona.handle}", style = t.footnote, color = c.labelSecondary, maxLines = 1)
            }
            if (selected) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = OhIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.dp, c.separatorOpaque, CircleShape),
                )
            }
        }
        if (showDivider) Hairline(startInset = 64.dp)
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
                textStyle = t.body.copy(color = c.label, textAlign = TextAlign.End),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
