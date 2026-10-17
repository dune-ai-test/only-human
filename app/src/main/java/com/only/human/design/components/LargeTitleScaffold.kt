package com.only.human.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.iosClickable
import com.only.human.design.rememberTick
import com.only.human.design.type

/** Height of the tab bar stack at the bottom of the window. */
val LocalTabBarInset = compositionLocalOf { 0.dp }

/** Vertical space the large title occupies before it scrolls away. */
private val CollapseRange = 56.dp

/**
 * iOS large-title screen: a fixed translucent 44dp bar that fades in an inline
 * title as the big title scrolls underneath it, plus optional leading/actions.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    offsetProvider: () -> Int,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = colors
    val t = type
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val collapseRangePx = with(density) { CollapseRange.toPx() }

    val offset = offsetProvider()
    val progress = (offset / collapseRangePx).coerceIn(0f, 1f)
    val inlineAlpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    val bottomInset = LocalTabBarInset.current

    Box(modifier.fillMaxSize().background(c.grouped)) {
        // Scrolling content, full-bleed. It slides under the translucent bar.
        content(PaddingValues(top = statusTop + 44.dp, bottom = bottomInset))

        // The nav bar.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(c.background.copy(alpha = progress * 0.94f))
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
        ) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Text(
                    text = title,
                    style = t.headline,
                    color = c.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 84.dp)
                        .alpha(inlineAlpha),
                )
                if (leading != null) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp),
                    ) { leading() }
                }
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            // Hairline at the very bottom of the bar.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(c.separatorOpaque.copy(alpha = progress * 0.6f)),
            )
        }
    }
}

/** The big title itself — put this as the first item of the scroll content. */
@Composable
fun LargeTitleHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = type.largeTitle,
        color = colors.label,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 3.dp, bottom = 13.dp),
    )
}

/** iOS back chevron, tinted accent. */
@Composable
fun IOSBackButton(onClick: () -> Unit) {
    val tick = rememberTick()
    Icon(
        imageVector = OhIcons.ChevronLeft,
        contentDescription = "Back",
        tint = colors.accent,
        modifier = Modifier
            .size(34.dp)
            .iosClickable(scaleTo = 0.88f, haptic = tick, onClick = onClick)
            .padding(6.dp),
    )
}

/** Bar action: plain accent text, like iOS "Edit" / "Done". */
@Composable
fun IOSBarTextAction(text: String, onClick: () -> Unit) {
    val tick = rememberTick()
    Text(
        text = text,
        style = type.body,
        color = colors.accent,
        modifier = Modifier.iosClickable(haptic = tick, onClick = onClick),
    )
}

// ---------------------------------------------------------------------------
// Collapse offsets
// ---------------------------------------------------------------------------

@Composable
fun rememberCollapseOffset(state: LazyListState): () -> Int {
    val derived = remember(state) {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset else 100000
        }
    }
    return { derived.value }
}

@Composable
fun rememberCollapseOffset(state: ScrollState): () -> Int {
    return { state.value }
}
