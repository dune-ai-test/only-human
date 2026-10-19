package com.only.human.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.Hairline
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.OhIcons
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.rememberTick
import com.only.human.design.type
import com.only.human.world.ActivityItem
import com.only.human.world.ActivityKind
import com.only.human.world.Cast
import com.only.human.world.World

@Composable
fun NotificationsScreen(
    onOpenProfile: (String) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenDm: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val c = colors
    val t = type
    val tick = rememberTick()

    // You're looking at it — the tab badge clears.
    LaunchedEffect(World.activities.size) { World.markActivitySeen() }

    LargeTitleScaffold(
        title = "Activity",
        offsetProvider = offset,
        actions = {
            Box {
                Icon(
                    imageVector = OhIcons.Bubble,
                    contentDescription = "Messages",
                    tint = c.accent,
                    modifier = Modifier
                        .size(22.dp)
                        .iosClickable(scaleTo = 0.88f, haptic = tick, onClick = onOpenMessages),
                )
                if (World.unreadDmsTotal() + World.unreadGroupsTotal() > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(c.red),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item(key = "header") { LargeTitleHeader("Activity") }
            item(key = "today-label") {
                Text(
                    "Today",
                    style = t.headline,
                    color = c.label,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )
            }
            item(key = "today-group") {
                InsetGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    World.activities.forEachIndexed { index, activity ->
                        ActivityRow(
                            activity = activity,
                            showDivider = index != World.activities.lastIndex,
                            onOpenProfile = onOpenProfile,
                            onOpenDm = onOpenDm,
                        )
                    }
                }
            }
            item(key = "footer") {
                Text(
                    "That's everything since you arrived.",
                    style = t.footnote,
                    color = c.labelSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(
    activity: ActivityItem,
    showDivider: Boolean,
    onOpenProfile: (String) -> Unit,
    onOpenDm: (String) -> Unit,
) {
    val c = colors
    val t = type
    val persona = Cast.byId(activity.personaId)

    Row(
        Modifier
            .fillMaxWidth()
            .iosClickable(
                onClick = {
                    when (activity.kind) {
                        ActivityKind.System -> Unit
                        ActivityKind.Message -> onOpenDm(persona.id)
                        else -> onOpenProfile(persona.id)
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramAvatar(persona.gradient, persona.initials, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(activity.boldPart) }
                    append(activity.rest)
                },
                style = t.subheadline,
                color = c.label,
                maxLines = 3,
            )
            Text(activity.timeLabel, style = t.footnote, color = c.labelSecondary)
        }
        Spacer(Modifier.width(10.dp))
        when (activity.kind) {
            ActivityKind.Follow ->
                Icon(OhIcons.Person, contentDescription = null, tint = c.iconGray, modifier = Modifier.size(15.dp))
            ActivityKind.Like ->
                Icon(OhIcons.HeartFill, contentDescription = null, tint = c.red, modifier = Modifier.size(15.dp))
            ActivityKind.Reply, ActivityKind.Mention, ActivityKind.Message ->
                Icon(OhIcons.Bubble, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
            ActivityKind.System -> Unit
        }
    }
    if (showDivider) {
        Hairline(startInset = 64.dp)
    }
}
