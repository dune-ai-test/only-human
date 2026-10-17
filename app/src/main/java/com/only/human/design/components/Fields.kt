package com.only.human.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.iosClickable
import com.only.human.design.type

/** iOS search field: 36dp, 10dp radius, gray fill, magnifier on the left. */
@Composable
fun IOSSearchField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val c = colors
    val t = type
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.fill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = OhIcons.Search,
            contentDescription = null,
            tint = if (focused) c.labelSecondary else c.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(placeholder, style = t.body, color = c.labelTertiary, maxLines = 1)
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = t.body.copy(color = c.label),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (text.isNotEmpty()) {
            Icon(
                imageVector = OhIcons.Xmark,
                contentDescription = "Clear",
                tint = c.labelTertiary,
                modifier = Modifier
                    .size(24.dp)
                    .iosClickable(scaleTo = 0.85f, onClick = { onTextChange("") })
                    .padding(5.dp),
            )
        }
    }
}

/** Filled capsule button (iOS "primary"). */
@Composable
fun IOSFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    tint: Color? = null,
) {
    val c = colors
    val t = type
    val bg = tint ?: c.accent
    val height = if (compact) 32.dp else 46.dp

    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .iosClickable(enabled = enabled, scaleTo = 0.96f, onClick = onClick)
            .defaultMinSize(minWidth = if (compact) 76.dp else 120.dp)
            .padding(horizontal = if (compact) 14.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (compact) t.subheadline.copy(fontWeight = FontWeight.SemiBold) else t.headline,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Follow / Following button, X-style proportions but iOS colors. */
@Composable
fun IOSFollowButton(
    following: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = colors
    val t = type
    val bg = if (following) c.fill else c.accent
    val fg = if (following) c.label else Color.White

    Box(
        modifier
            .height(33.dp)
            .clip(CircleShape)
            .background(bg)
            .iosClickable(scaleTo = 0.95f, onClick = onClick)
            .defaultMinSize(minWidth = 88.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (following) "Following" else "Follow",
            style = t.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
        )
    }
}

/** Small icon + count used under posts. */
@Composable
fun ActionCount(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    tint: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = type

    Row(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.iosClickable(
                    scaleTo = 1f,
                    dimTo = 0.45f,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        if (count != null && count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = formatCount(count),
                style = t.footnote,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

fun formatCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 10000 -> String.format("%.1fK", n / 1000f).replace(".0K", "K")
    else -> "${n / 1000}K"
}
