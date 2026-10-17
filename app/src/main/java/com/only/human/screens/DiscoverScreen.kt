package com.only.human.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSFollowButton
import com.only.human.design.components.IOSSearchField
import com.only.human.design.components.IOSSegmentedControl
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.GroupedRow
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.components.RowChevron
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast
import com.only.human.world.Persona
import com.only.human.world.Trends
import com.only.human.world.World

@Composable
fun DiscoverScreen(onOpenProfile: (String) -> Unit, onOpenTag: (String) -> Unit) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    var query by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var segment by rememberSaveable { mutableIntStateOf(0) }

    val people = Cast.people
    val filtered = remember(query, people) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) people
        else people.filter {
            it.name.lowercase().contains(q) || it.handle.lowercase().contains(q)
        }
    }

    LargeTitleScaffold(title = "Discover", offsetProvider = offset) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item(key = "header") { LargeTitleHeader("Discover") }
            item(key = "search") {
                IOSSearchField(
                    text = query,
                    onTextChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = "Search people",
                )
            }
            item(key = "segments") {
                IOSSegmentedControl(
                    options = listOf("People", "Trending"),
                    selectedIndex = segment,
                    onSelect = { segment = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (segment == 0) {
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No one by that name — yet.",
                            style = type.footnote,
                            color = colors.labelSecondary,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { persona ->
                        PersonRow(persona = persona, onOpenProfile = onOpenProfile)
                        Hairline(startInset = 72.dp)
                    }
                }
            } else {
                items(trending, key = { it }) { tag ->
                    TrendingRow(tag = tag, posts = Trends.label(tag), onClick = { onOpenTag(tag) })
                    Hairline(startInset = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun PersonRow(persona: Persona, onOpenProfile: (String) -> Unit) {
    val c = colors
    val t = type
    Row(
        Modifier
            .fillMaxWidth()
            .iosClickable(onClick = { onOpenProfile(persona.id) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramAvatar(persona.gradient, persona.initials, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(persona.name, style = t.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
            Text("@${persona.handle}", style = t.subheadline, color = c.labelSecondary, maxLines = 1)
            if (persona.bio.isNotEmpty() && persona.bio != ".") {
                Text(persona.bio, style = t.footnote, color = c.labelSecondary, maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        IOSFollowButton(
            following = World.following[persona.id] == true,
            onClick = { World.toggleFollow(persona.id) },
        )
    }
}

private val trending = listOf(
    "#nightshift",
    "#morningpeople",
    "#quietweeks",
    "#homemadedal",
    "#nowrongchords",
)

@Composable
private fun TrendingRow(tag: String, posts: String, onClick: () -> Unit) {
    val c = colors
    val t = type
    Row(
        Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tag, style = t.headline, color = c.label, maxLines = 1)
            Text(posts, style = t.footnote, color = c.labelSecondary, maxLines = 1)
        }
        RowChevron()
    }
}
