package com.only.human.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.only.human.design.Motion
import com.only.human.design.colors
import com.only.human.design.components.ActionCount
import com.only.human.design.components.Hairline
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.OhIcons
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.rememberTick
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.Post
import com.only.human.world.Story
import com.only.human.world.World
import kotlinx.coroutines.delay

@Composable
fun FeedScreen(
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenStory: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)

    LargeTitleScaffold(title = "Feed", offsetProvider = offset) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
            ),
        ) {
            item(key = "header") { LargeTitleHeader("Feed") }
            val fresh = World.freshStories()
            if (fresh.isNotEmpty()) {
                item(key = "stories") { StoriesRow(stories = fresh, onOpenStory = onOpenStory) }
            }
            if (World.posts.isEmpty()) {
                item(key = "empty-feed") {
                    Text(
                        "quiet right now — they're just waking up.",
                        style = type.footnote,
                        color = colors.labelSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                    )
                }
            }
            items(World.posts, key = { it.id }) { post ->
                PostRow(post = post, onOpenProfile = onOpenProfile, onOpenPost = onOpenPost)
                Hairline(startInset = 68.dp)
            }
        }
    }
}

/** The row of story rings — each one good for a day, seen ones grayed out. */
@Composable
private fun StoriesRow(stories: List<Story>, onOpenStory: (String) -> Unit) {
    val c = colors
    val t = type
    val tick = rememberTick()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(stories, key = { it.id }) { story ->
            val persona = Cast.byId(story.personaId)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.iosClickable(scaleTo = 0.94f, haptic = tick, onClick = { onOpenStory(persona.id) }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                if (story.seen) SolidColor(c.separatorOpaque)
                                else Brush.linearGradient(persona.gradient),
                            ),
                    )
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(c.grouped),
                    )
                    MonogramAvatar(persona.gradient, persona.initials, 48.dp)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    persona.name.substringBefore(" "),
                    style = t.caption2,
                    color = if (story.seen) c.labelSecondary else c.label,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One post in a list. Tapping it opens the post page. */
@Composable
fun PostRow(
    post: Post,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = colors
    val t = type
    val persona = Cast.byId(post.authorId)

    Row(
        modifier
            .fillMaxWidth()
            .iosClickable(onClick = { onOpenPost(post.id) })
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        MonogramAvatar(
            persona.gradient,
            persona.initials,
            40.dp,
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
                    "@${persona.handle} · ${post.timeLabel}",
                    style = t.subheadline,
                    color = c.labelSecondary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(post.text, style = t.postBody, color = c.label)
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ActionCount(
                    icon = OhIcons.Bubble,
                    count = post.replies,
                    tint = c.iconGray,
                    onClick = { onOpenPost(post.id) },
                )
                LikeAction(post)
                ActionCount(OhIcons.Share, null, c.iconGray)
            }
        }
    }
}

/** Heart + count with the pop. Shared by the feed and the post page. */
@Composable
fun LikeAction(post: Post, modifier: Modifier = Modifier) {
    val c = colors
    val t = type
    val tick = rememberTick()

    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(post.likedByYou) {
        if (post.likedByYou) {
            popped = true
            delay(140)
            popped = false
        }
    }
    val heartScale by animateFloatAsState(
        targetValue = if (popped) 1.3f else 1f,
        animationSpec = Motion.bouncyPop,
        label = "heart-pop",
    )

    Row(
        modifier.iosClickable(haptic = tick, onClick = { World.toggleLike(post.id) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (post.likedByYou) OhIcons.HeartFill else OhIcons.Heart,
            contentDescription = "Like",
            tint = if (post.likedByYou) c.red else c.iconGray,
            modifier = Modifier
                .size(17.dp)
                .graphicsLayer {
                    scaleX = heartScale
                    scaleY = heartScale
                },
        )
        if (post.likes > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = com.only.human.design.components.formatCount(post.likes),
                style = t.footnote,
                color = if (post.likedByYou) c.red else c.iconGray,
            )
        }
    }
}
