package com.only.human.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.iosClickable
import com.only.human.design.type

/** A true hairline, like iOS separators. */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
    color: Color = colors.separator,
) {
    Row(modifier.fillMaxWidth().height(0.5.dp)) {
        if (startInset > 0.dp) Spacer(Modifier.width(startInset))
        Box(Modifier.weight(1f).fillMaxHeight().background(color))
        if (endInset > 0.dp) Spacer(Modifier.width(endInset))
    }
}

/** Inset-grouped card, the backbone of iOS settings-style layout. */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = colors
    val t = type
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                style = t.footnote,
                color = c.labelSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.card),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = t.footnote,
                color = c.labelSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/** A single row inside an InsetGroup. No ripple — iOS rows dim on press. */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = colors.label,
    showDivider: Boolean = false,
    dividerStartInset: Dp = 16.dp,
    minHeight: Dp = 46.dp,
    onClick: (() -> Unit)? = null,
) {
    val c = colors
    val t = type
    var rowModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        rowModifier = rowModifier.iosClickable(highlightShape = RoundedCornerShape(0.dp), onClick = onClick)
    }
    Column(rowModifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = t.body, color = titleColor, maxLines = 2)
                if (subtitle != null) {
                    Text(subtitle, style = t.footnote, color = c.labelSecondary, maxLines = 2)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (showDivider) {
            Hairline(startInset = dividerStartInset)
        }
    }
}

/** Small gray chevron used at the end of tappable rows. */
@Composable
fun RowChevron() {
    androidx.compose.material3.Icon(
        imageVector = OhIcons.ChevronRight,
        contentDescription = null,
        tint = colors.labelTertiary,
        modifier = Modifier.size(13.dp),
    )
}
