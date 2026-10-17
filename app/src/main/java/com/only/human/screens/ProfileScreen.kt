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
import com.only.human.design.colors
import com.only.human.design.components.GroupedRow
import com.only.human.design.components.Hairline
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.IOSFollowButton
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.RowChevron
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.Engine
import com.only.human.world.Persona
import com.only.human.world.World

/** A persona's profile, pushed from anywhere their name appears. */
@Composable
fun ProfileScreen(
    personaId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenDm: (String) -> Unit,
    onOpenModel: (String) -> Unit,
) {
    val persona = Cast.byId(personaId)
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val posts = World.postsBy(persona.id)

    LargeTitleScaffold(
        title = persona.name,
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
            item(key = "title") { LargeTitleHeader(persona.name) }
            item(key = "card") { ProfileCard(persona, onOpenDm) }
            if (persona.id != "you") {
                item(key = "model") {
                    InsetGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        GroupedRow(
                            title = "AI model",
                            subtitle = World.personaModel(persona.id)
                                ?: "Engine default (${Engine.config.model})",
                            trailing = { RowChevron() },
                            onClick = { onOpenModel(persona.id) },
                        )
                    }
                }
            }
            item(key = "posts-label") {
                Text(
                    "Posts",
                    style = type.headline,
                    color = colors.label,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )
            }
            items(posts, key = { it.id }) { post ->
                PostRow(post = post, onOpenProfile = {}, onOpenPost = onOpenPost)
                Hairline(startInset = 68.dp)
            }
        }
    }
}

@Composable
private fun ProfileCard(persona: Persona, onOpenDm: (String) -> Unit) {
    val c = colors
    val t = type

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MonogramAvatar(persona.gradient, persona.initials, 88.dp)
        Spacer(Modifier.height(12.dp))
        Text(persona.name, style = t.title2, color = c.label, textAlign = TextAlign.Center)
        Text("@${persona.handle}", style = t.subheadline, color = c.labelSecondary)
        val aboutLine = listOf(persona.occupation, persona.location)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        if (aboutLine.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(aboutLine, style = t.footnote, color = c.labelSecondary)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            StatColumn(World.postsBy(persona.id).size.toString(), "Posts")
            StatColumn(World.followerCount(persona).toString(), "Followers")
            StatColumn((Cast.relationships.count { it.a == persona.id || it.b == persona.id } + 3).toString(), "Following")
        }
        Spacer(Modifier.height(14.dp))
        if (persona.id != "you") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IOSFollowButton(
                    following = World.following[persona.id] == true,
                    onClick = { World.toggleFollow(persona.id) },
                )
                IOSFilledButton(
                    text = "Message",
                    onClick = { onOpenDm(persona.id) },
                    compact = true,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            persona.bio,
            style = t.subheadline,
            color = c.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(18.dp))
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
