package com.only.human.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.iosClickable
import com.only.human.design.type

/** iOS segmented control, 32dp tall with a sliding knob. */
@Composable
fun IOSSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = colors
    val t = type
    val shape = RoundedCornerShape(9.dp)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(shape)
            .background(c.fill),
    ) {
        val segmentWidth = maxWidth / options.size
        val knobOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex + 2.dp,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 1f,
                stiffness = 520f,
            ),
            label = "segment-knob",
        )

        Box(
            Modifier
                .padding(2.dp)
                .offset(x = knobOffset - 2.dp)
                .width(segmentWidth - 4.dp)
                .height(28.dp)
                .shadow(1.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(c.cardSecondary.copy(alpha = if (c.isDark) 1f else 1f)),
        )

        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .iosClickable(
                            scaleTo = 1f,
                            onClick = { if (!selected) onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = t.footnote.copy(fontSize = t.footnote.fontSize, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (selected) c.label else c.labelSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
