package com.only.human.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.GroupedRow
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
import com.only.human.world.Persona
import com.only.human.world.World

/** The room's own settings: what it's called, who's in it. */
@Composable
fun GroupSettingsScreen(groupId: String, onBack: () -> Unit) {
    val group = World.groups.firstOrNull { it.id == groupId }
    if (group == null) {
        LaunchedEffect(groupId) { onBack() }
        return
    }

    val c = colors
    val t = type
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val focus = LocalFocusManager.current

    var name by rememberSaveable(group.id) { mutableStateOf(group.name) }
    var topic by rememberSaveable(group.id) { mutableStateOf(group.topic) }
    val inRoom = group.members.mapNotNull { id -> Cast.people.firstOrNull { it.id == id }?.let { it to id } }
    val candidates = Cast.people
        .filterNot { it.id.startsWith("org-") || group.members.contains(it.id) }
        .sortedBy { it.name.lowercase() }

    LargeTitleScaffold(
        title = "Group",
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
            item(key = "title") { LargeTitleHeader("Group") }

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

            item(key = "save") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    IOSFilledButton(
                        text = "Save",
                        enabled = name.isNotBlank(),
                        onClick = {
                            focus.clearFocus()
                            World.updateGroup(groupId, name, topic)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "members") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    header = "In the room",
                ) {
                    if (inRoom.isEmpty()) {
                        GroupedRow(title = "No one yet", subtitle = "Add someone below")
                    } else {
                        inRoom.forEachIndexed { index, (persona, id) ->
                            MemberActionRow(
                                persona = persona,
                                action = "Remove",
                                showDivider = index != inRoom.lastIndex,
                                onAction = { World.removeGroupMember(groupId, id) },
                            )
                        }
                    }
                }
            }

            item(key = "add") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    header = "Add people",
                ) {
                    if (candidates.isEmpty()) {
                        GroupedRow(title = "Everyone's already here")
                    } else {
                        candidates.forEachIndexed { index, persona ->
                            MemberActionRow(
                                persona = persona,
                                action = "Add",
                                showDivider = index != candidates.lastIndex,
                                onAction = { World.addGroupMembers(groupId, listOf(persona.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One person in a settings list, with the action that moves them. */
@Composable
private fun MemberActionRow(
    persona: Persona,
    action: String,
    showDivider: Boolean,
    onAction: () -> Unit,
) {
    val c = colors
    val t = type
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonogramAvatar(persona.gradient, persona.initials, 34.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                persona.name,
                style = t.body,
                color = c.label,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                action,
                style = t.footnote,
                color = c.accent,
                maxLines = 1,
                modifier = Modifier.iosClickable(scaleTo = 0.94f, onClick = onAction),
            )
        }
        if (showDivider) Hairline(startInset = 62.dp)
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
