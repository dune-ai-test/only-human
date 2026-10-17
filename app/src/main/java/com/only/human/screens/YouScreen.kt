package com.only.human.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.only.human.BuildConfig
import com.only.human.design.colors
import com.only.human.design.components.GroupedRow
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.RowChevron
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.World

/** Your own profile — the only human's page. */
@Composable
fun YouScreen(
    onOpenSettings: () -> Unit,
    onCompose: () -> Unit,
) {
    val c = colors
    val t = type
    val you = Cast.you
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val posts = World.postsBy(you.id)

    LargeTitleScaffold(title = "You", offsetProvider = offset) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item(key = "title") { LargeTitleHeader("You") }
            item(key = "card") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonogramAvatar(you.gradient, "Y", 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(you.name, style = t.title2, color = c.label)
                    Text("@${you.handle}", style = t.subheadline, color = c.labelSecondary)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        StatColumn(posts.size.toString(), "Posts")
                        StatColumn(Cast.people.size.toString(), "Followers")
                        StatColumn(World.following.count { it.value }.toString(), "Following")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "the only human here.",
                        style = t.subheadline,
                        color = c.label,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }

            if (posts.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Nothing here yet", style = t.headline, color = c.label)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Fifteen people are waiting to hear from you.",
                            style = t.footnote,
                            color = c.labelSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        IOSFilledButton("Write something", onClick = onCompose, compact = true)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                item(key = "posts-label") {
                    Text(
                        "Your posts",
                        style = t.headline,
                        color = c.label,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                    )
                }
                items(posts, key = { it.id }) { post ->
                    PostRow(post = post, onOpenProfile = {})
                    Hairline(startInset = 68.dp)
                }
            }

            item(key = "settings-group") {
                InsetGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    GroupedRow(
                        title = "AI & Simulation",
                        subtitle = "Endpoint, model, and API key",
                        trailing = { RowChevron() },
                        showDivider = true,
                        onClick = onOpenSettings,
                    )
                    GroupedRow(
                        title = "About Only Human",
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                        trailing = { RowChevron() },
                        showDivider = false,
                        onClick = onOpenSettings,
                    )
                }
            }
            item(key = "footer") {
                Text(
                    "You are the only human here.",
                    style = t.footnote,
                    color = c.labelSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    val c = colors
    val t = type
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = t.headline, color = c.label, fontWeight = FontWeight.SemiBold)
        Text(label, style = t.footnote, color = c.labelSecondary)
    }
}
