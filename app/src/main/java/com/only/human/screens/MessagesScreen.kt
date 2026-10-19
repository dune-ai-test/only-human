package com.only.human.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.LocalTabBarInset
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.OhIcons
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.Dm
import com.only.human.world.DmThread
import com.only.human.world.GroupThread
import com.only.human.world.Persona
import com.only.human.world.World

/** The Messages list — every private thread and group, newest first. */
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    onOpenDm: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onNewGroup: () -> Unit,
) {
    val c = colors
    val t = type
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val entries = (
        World.dmThreads().map { MessagesEntry.Dm(it) } +
            World.groupThreads().map { MessagesEntry.Group(it) }
        ).sortedByDescending { it.at }

    LargeTitleScaffold(
        title = "Messages",
        offsetProvider = offset,
        leading = { IOSBackButton(onBack) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item(key = "title") { LargeTitleHeader("Messages") }
            item(key = "newGroup") {
                InsetGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    NewGroupRow(onClick = onNewGroup)
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Quiet in here", style = t.headline, color = c.label)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No messages yet. Open someone's profile and say something — they always answer.",
                            style = t.footnote,
                            color = c.labelSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                item(key = "threads") {
                    InsetGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        entries.forEachIndexed { index, entry ->
                            when (entry) {
                                is MessagesEntry.Dm -> ThreadRow(
                                    thread = entry.thread,
                                    showDivider = index != entries.lastIndex,
                                    onClick = { onOpenDm(entry.thread.personaId) },
                                )
                                is MessagesEntry.Group -> GroupRow(
                                    thread = entry.thread,
                                    showDivider = index != entries.lastIndex,
                                    onClick = { onOpenGroup(entry.thread.group.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row in the merged list — a DM thread or a group. */
private sealed interface MessagesEntry {
    val at: Long

    data class Dm(val thread: DmThread) : MessagesEntry {
        override val at: Long get() = thread.last.at
    }

    data class Group(val thread: GroupThread) : MessagesEntry {
        override val at: Long get() = thread.last.at
    }
}

@Composable
private fun NewGroupRow(onClick: () -> Unit) {
    val c = colors
    val t = type

    Row(
        Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(c.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = OhIcons.Plus,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "New Group",
            style = t.body.copy(fontWeight = FontWeight.Medium),
            color = c.label,
            maxLines = 1,
        )
    }
}

@Composable
private fun ThreadRow(thread: DmThread, showDivider: Boolean, onClick: () -> Unit) {
    val c = colors
    val t = type
    val persona = Cast.byId(thread.personaId)
    val last = thread.last
    val unread = thread.unread > 0

    Row(
        Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramAvatar(persona.gradient, persona.initials, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                persona.name,
                style = t.body.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium),
                color = c.label,
                maxLines = 1,
            )
            Text(
                text = (if (last.fromMe) "You: " else "") + last.text.replace('\n', ' '),
                style = t.subheadline,
                color = c.labelSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(last.timeLabel, style = t.footnote, color = c.labelSecondary)
            if (unread) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(9.dp).clip(CircleShape).background(c.accent))
            }
        }
    }
    if (showDivider) Hairline(startInset = 74.dp)
}

@Composable
private fun GroupRow(thread: GroupThread, showDivider: Boolean, onClick: () -> Unit) {
    val c = colors
    val t = type
    val group = thread.group
    val last = thread.last
    val unread = thread.unread > 0
    val lead = group.members.firstOrNull()?.let { Cast.byId(it) }
    val parts = group.name.trim().split(" ").filter { it.isNotEmpty() }
    val initials = when {
        parts.isEmpty() -> "#"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
    val sender = if (last.authorId == "you") "You" else Cast.byId(last.authorId).name.split(" ").first()

    Row(
        Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramAvatar(lead?.gradient ?: listOf(c.fillSecondary, c.fillSecondary), initials, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.name,
                style = t.body.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium),
                color = c.label,
                maxLines = 1,
            )
            Text(
                text = "$sender: " + last.text.replace('\n', ' '),
                style = t.subheadline,
                color = c.labelSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(last.timeLabel, style = t.footnote, color = c.labelSecondary)
            if (unread) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(9.dp).clip(CircleShape).background(c.accent))
            }
        }
    }
    if (showDivider) Hairline(startInset = 74.dp)
}

/** One private conversation, iMessage-style. */
@Composable
fun DmScreen(personaId: String, onBack: () -> Unit) {
    val c = colors
    val t = type
    val persona = Cast.byId(personaId)
    val messages = World.dmWith(personaId)
    val isTyping = World.typing[personaId] == true
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sendDraft = {
        if (draft.isNotBlank()) {
            World.sendDm(personaId, draft)
            draft = ""
        }
    }

    // Opening the thread reads it; arrivals while you're here stay read.
    LaunchedEffect(messages.size, isTyping) {
        World.markDmSeen(personaId)
        if (messages.isNotEmpty() || isTyping) {
            val target = if (isTyping) messages.size else messages.lastIndex
            listState.animateScrollToItem(target)
        }
    }

    // While the thread is open, arrivals are read on the spot — no Activity row.
    DisposableEffect(personaId) {
        World.openDm = personaId
        onDispose { if (World.openDm == personaId) World.openDm = null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .imePadding(),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Box(Modifier.fillMaxWidth().height(46.dp)) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                    IOSBackButton(onBack)
                }
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(persona.name, style = t.headline, color = c.label, maxLines = 1)
                    if (isTyping) {
                        Text("typing…", style = t.caption2, color = c.labelSecondary)
                    }
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                    MonogramAvatar(persona.gradient, persona.initials, 28.dp)
                }
            }
            Hairline()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { dm -> DmBubble(dm, persona) }
            if (isTyping) {
                item(key = "typing") { TypingBubble(persona) }
            }
            if (messages.isEmpty() && !isTyping) {
                item(key = "empty") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Say hi — ${persona.name.split(" ").first()} always writes back.",
                            style = t.footnote,
                            color = c.labelSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.background)
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp, bottom = if (imeOpen) 8.dp else LocalTabBarInset.current),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.fill)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text("Message", style = t.subheadline, color = c.labelTertiary, maxLines = 1)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = t.subheadline.copy(color = c.label),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val canSend = draft.isNotBlank()
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (canSend) c.accent else c.fill)
                    .iosClickable(
                        enabled = canSend,
                        scaleTo = 0.9f,
                        onClick = sendDraft,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = OhIcons.ArrowUp,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else c.labelTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DmBubble(dm: Dm, persona: Persona) {
    val c = colors
    val t = type
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (dm.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!dm.fromMe) {
            MonogramAvatar(persona.gradient, persona.initials, 26.dp)
            Spacer(Modifier.width(7.dp))
        }
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = if (dm.fromMe) 5.dp else 18.dp,
                        bottomStart = if (dm.fromMe) 18.dp else 5.dp,
                    ),
                )
                .background(if (dm.fromMe) c.accent else c.cardSecondary)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Text(
                dm.text,
                style = t.callout,
                color = if (dm.fromMe) Color.White else c.label,
            )
        }
    }
}

@Composable
private fun TypingBubble(persona: Persona) {
    val c = colors
    val transition = rememberInfiniteTransition(label = "typing")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "typing-pulse",
    )
    Row(verticalAlignment = Alignment.Bottom) {
        MonogramAvatar(persona.gradient, persona.initials, 26.dp)
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 5.dp))
                .background(c.cardSecondary)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                "• • •",
                style = type.caption1,
                color = c.labelSecondary,
                modifier = Modifier.alpha(pulse),
            )
        }
    }
}
