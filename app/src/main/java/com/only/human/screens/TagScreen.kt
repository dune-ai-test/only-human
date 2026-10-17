package com.only.human.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.type
import com.only.human.world.Trends

/** Everything they've written under a tag — real posts, honest counts. */
@Composable
fun TagScreen(
    tag: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val posts = Trends.posts(tag)

    LargeTitleScaffold(
        title = tag,
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
            item(key = "title") { LargeTitleHeader(tag) }
            if (posts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "quiet under this tag for now — they live more than they label.",
                        style = type.footnote,
                        color = colors.labelSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    )
                }
            }
            items(posts, key = { it.id }) { post ->
                PostRow(
                    post = post,
                    onOpenProfile = onOpenProfile,
                    onOpenPost = onOpenPost,
                )
                Hairline(startInset = 68.dp)
            }
        }
    }
}
