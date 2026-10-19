package com.only.human.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.LocalTabBarInset
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.OhIcons
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.GroupMsg
import com.only.human.world.Persona
import com.only.human.world.World

/** One group conversation. */
@Composable
fun GroupChatScreen(groupId: String, onBack: () -> Unit, onOpenInfo: () -> Unit) {
    val group = World.groups.firstOrNull { it.id == groupId }
    if (group == null) {
        LaunchedEffect(groupId) { onBack() }
        return
    }

    val c = colors
    val t = type
    val messages = World.groupMsgsWith(groupId)
    val isTyping = World.groupTyping[groupId] == true
    val members = group.members.mapNotNull { id -> Cast.people.firstOrNull { it.id == id } }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sendDraft = {
        if (draft.isNotBlank()) {
            World.sendGroupMsg(groupId, draft)
            draft = ""
        }
    }
    val showMentions = draft.contains('@') && !draft.substringAfterLast('@').contains(' ')
    val memberNames = group.members.mapNotNull { id -> Cast.people.firstOrNull { it.id == id }?.name?.split(" ")?.first() }
    val subtitle = when {
        isTyping -> "typing…"
        group.topic.isNotEmpty() -> group.topic
        memberNames.isNotEmpty() -> memberNames.joinToString(", ")
        else -> "${group.members.size} people"
    }

    // Opening the room reads it; arrivals while you're here stay read.
    LaunchedEffect(messages.size, isTyping) {
        World.markGroupSeen(groupId)
        if (messages.isNotEmpty() || isTyping) {
            val target = if (isTyping) messages.size else messages.lastIndex
            listState.animateScrollToItem(target)
        }
    }

    // While the room is open, arrivals are read on the spot — no Activity row.
    DisposableEffect(groupId) {
        World.openGroup = groupId
        onDispose { if (World.openGroup == groupId) World.openGroup = null }
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
                    Text(group.name, style = t.headline, color = c.label, maxLines = 1)
                    Text(subtitle, style = t.caption2, color = c.labelSecondary, maxLines = 1)
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                    Text(
                        "${group.members.size} · People",
                        style = t.footnote,
                        color = c.accent,
                        maxLines = 1,
                        modifier = Modifier.iosClickable(scaleTo = 0.94f, onClick = onOpenInfo),
                    )
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
            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                val prev = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                GroupBubble(
                    msg = msg,
                    runStart = prev == null || prev.authorId != msg.authorId,
                    runEnd = next == null || next.authorId != msg.authorId,
                )
            }
            if (isTyping) {
                item(key = "typing") { GroupTypingBubble() }
            }
            if (messages.isEmpty() && !isTyping) {
                item(key = "empty") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Say something — the room writes back.",
                            style = t.footnote,
                            color = c.labelSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Hairline()
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.background),
        ) {
            if (showMentions && members.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    members.forEach { member ->
                        MemberChip(
                            persona = member,
                            onClick = {
                                val cut = draft.lastIndexOf('@')
                                draft = (if (cut >= 0) draft.take(cut) else draft) + "@${member.handle} "
                            },
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
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
}

@Composable
private fun GroupBubble(msg: GroupMsg, runStart: Boolean, runEnd: Boolean) {
    val c = colors
    val t = type
    val fromMe = msg.authorId == "you"
    val persona = Cast.byId(msg.authorId)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!fromMe) {
            if (runEnd) {
                MonogramAvatar(persona.gradient, persona.initials, 26.dp)
            } else {
                Spacer(Modifier.size(26.dp))
            }
            Spacer(Modifier.width(7.dp))
        }
        Column(horizontalAlignment = if (fromMe) Alignment.End else Alignment.Start) {
            if (!fromMe && runStart) {
                Text(
                    persona.name.split(" ").first(),
                    style = t.caption2,
                    color = c.labelSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                )
            }
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomEnd = if (fromMe) 5.dp else 18.dp,
                            bottomStart = if (fromMe) 18.dp else 5.dp,
                        ),
                    )
                    .background(if (fromMe) c.accent else c.cardSecondary)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                if (fromMe) {
                    Text(msg.text, style = t.callout, color = Color.White)
                } else {
                    Text(mentions(msg.text, c.accent), style = t.callout, color = c.label)
                }
            }
        }
    }
}

private val MentionPattern = Regex("@[a-zA-Z0-9_]+")

/** Marks @handles in the accent color — only inside incoming bubbles. */
private fun mentions(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    MentionPattern.findAll(text).forEach { match ->
        append(text.substring(index, match.range.first))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(match.value) }
        index = match.range.last + 1
    }
    append(text.substring(index))
}

@Composable
private fun GroupTypingBubble() {
    val c = colors
    val transition = rememberInfiniteTransition(label = "group-typing")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "group-typing-pulse",
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
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

@Composable
private fun MemberChip(persona: Persona, onClick: () -> Unit) {
    val c = colors
    val t = type
    Row(
        Modifier
            .clip(CircleShape)
            .background(c.fill)
            .iosClickable(scaleTo = 0.94f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "@${persona.handle}",
            style = t.caption1.copy(fontWeight = FontWeight.SemiBold),
            color = c.accent,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(persona.name, style = t.caption1, color = c.labelSecondary, maxLines = 1)
    }
}
