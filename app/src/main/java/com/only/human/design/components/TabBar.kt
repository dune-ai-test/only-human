package com.only.human.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.iosClickable
import com.only.human.design.rememberTick
import com.only.human.design.type

/** One tab. [badge] draws a small red count above the icon when above zero. */
data class TabSpec(val label: String, val icon: ImageVector, val badge: Int = 0)

/**
 * Translucent iOS tab bar: 49dp, hairline on top, 25dp icons with 10-11pt
 * labels. No pill indicator, no ripple — selection just turns blue.
 * A red count badge rides the top-right of the icon when there's news.
 */
@Composable
fun IOSTabBar(
    tabs: List<TabSpec>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = colors
    val t = type
    val tick = rememberTick()
    Column(
        modifier
            .fillMaxWidth()
            .background(c.background.copy(alpha = 0.94f)),
    ) {
        Hairline(color = c.separator.copy(alpha = c.separator.alpha * 0.7f))
        Row(Modifier.fillMaxWidth().height(49.dp)) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val tint = if (selected) c.accent else c.iconGray
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .iosClickable(scaleTo = 0.9f, haptic = tick, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(25.dp),
                            )
                            if (tab.badge > 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 9.dp, y = (-4).dp)
                                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                        .clip(CircleShape)
                                        .background(c.red)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (tab.badge > 9) "9+" else tab.badge.toString(),
                                        style = t.caption2.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = t.caption2.copy(fontWeight = FontWeight.Medium),
                            color = tint,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
