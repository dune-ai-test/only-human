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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.ActionCount
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.LocalTabBarInset
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.OhIcons
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.CommentIntent
import com.only.human.world.Post
import com.only.human.world.Reply
import com.only.human.world.World

/**
 * One post, full size, with its replies below — and a reply bar that opens
 * the composer you already know.
 */
@Composable
fun PostPage(postId: String, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val c = colors
    val post = World.posts.firstOrNull { it.id == postId }

    LaunchedEffect(post == null) { if (post == null) onBack() }
    if (post == null) return

    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val replies = World.repliesFor(postId)
    val intents = World.commentIntents.filter { it.postId == postId }
    var replyOpen by remember { mutableStateOf(false) }
    val tabInset = LocalTabBarInset.current

    // While you're here, replies land in the thread without pinging Activity.
    DisposableEffect(postId) {
        World.openPost = postId
        onDispose { if (World.openPost == postId) World.openPost = null }
    }

    Box(Modifier.fillMaxSize().background(c.grouped)) {
        LargeTitleScaffold(
            title = "Post",
            offsetProvider = offset,
            leading = { IOSBackButton(onBack) },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 68.dp,
                ),
            ) {
                item(key = "post") { PostHeader(post, onOpenProfile) }
                items(intents, key = { it.id }) { intent ->
                    CommentIntentRow(intent)
                }
                if (replies.isEmpty() && post.replies == 0) {
                    item(key = "empty") {
                        Text(
                            "No replies yet. Yours would be first.",
                            style = type.subheadline,
                            color = c.labelTertiary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
                items(replies, key = { it.id }) { reply ->
                    ReplyRow(reply, onOpenProfile)
                }
            }
        }

        ReplyBar(
            onClick = { replyOpen = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = tabInset),
        )
    }

    if (replyOpen) {
        ComposerSheet(
            onDismiss = { replyOpen = false },
            onPost = { World.addReply(postId, it) },
            title = "Reply",
            placeholder = "Post your reply",
            buttonLabel = "Reply",
        )
    }
}

@Composable
private fun PostHeader(post: Post, onOpenProfile: (String) -> Unit) {
    val c = colors
    val t = type
    val persona = Cast.byId(post.authorId)

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonogramAvatar(
                persona.gradient,
                persona.initials,
                44.dp,
                modifier = Modifier.iosClickable(scaleTo = 0.92f, onClick = { onOpenProfile(persona.id) }),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    persona.name,
                    style = t.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = c.label,
                )
                Text("@${persona.handle}", style = t.subheadline, color = c.labelSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(post.text, style = t.postBody, color = c.label)
        Spacer(Modifier.height(12.dp))
        Text(post.timeLabel, style = t.footnote, color = c.labelSecondary)
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ActionCount(OhIcons.Bubble, post.replies, c.iconGray)
            LikeAction(post)
            ActionCount(OhIcons.Share, null, c.iconGray)
        }
    }
    Hairline()
}

/** Someone asking to comment on your post — you post it, or they move on. */
@Composable
private fun CommentIntentRow(intent: CommentIntent) {
    val c = colors
    val t = type
    val persona = Cast.byId(intent.personaId)

    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramAvatar(persona.gradient, persona.initials, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    intent.writing -> "${persona.name} is writing a comment…"
                    intent.failed -> "${persona.name} couldn't write it"
                    else -> "${persona.name} wants to comment"
                },
                style = t.subheadline,
                color = if (intent.failed) c.red else c.label,
                maxLines = 2,
            )
        }
        if (!intent.writing) {
            Spacer(Modifier.width(8.dp))
            Text(
                "Skip",
                style = t.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = c.labelSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .iosClickable(scaleTo = 0.95f, onClick = { World.skipComment(intent.id) })
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(6.dp))
            IOSFilledButton(
                text = if (intent.failed) "Try again" else "Post it",
                onClick = { World.postComment(intent.id) },
                compact = true,
            )
        }
    }
    Hairline(startInset = 60.dp)
}

@Composable
private fun ReplyRow(reply: Reply, onOpenProfile: (String) -> Unit) {
    val c = colors
    val t = type
    val persona = Cast.byId(reply.authorId)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MonogramAvatar(
            persona.gradient,
            persona.initials,
            32.dp,
            modifier = Modifier.iosClickable(scaleTo = 0.92f, onClick = { onOpenProfile(persona.id) }),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    persona.name,
                    style = t.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = c.label,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "@${persona.handle} · ${reply.timeLabel}",
                    style = t.subheadline,
                    color = c.labelSecondary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(reply.text, style = t.subheadline, color = c.label)
        }
    }
    Hairline(startInset = 60.dp)
}

@Composable
private fun ReplyBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    val t = type
    val me = World.mePersona

    Column(modifier.fillMaxWidth().background(c.card)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable(scaleTo = 1f, dimTo = 0.55f, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonogramAvatar(me.gradient, me.initials, 30.dp)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(19.dp))
                    .background(c.fillSecondary)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text("Post your reply", style = t.subheadline, color = c.labelSecondary)
            }
        }
    }
}
