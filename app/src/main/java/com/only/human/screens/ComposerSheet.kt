package com.only.human.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.MonogramAvatar
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Cast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerSheet(
    onDismiss: () -> Unit,
    onPost: (String) -> Unit,
) {
    val c = colors
    val t = type
    val you = Cast.you
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .width(36.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(c.fillSecondary),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .imePadding(),
        ) {
            Box(Modifier.fillMaxWidth().height(36.dp)) {
                Text(
                    "Cancel",
                    style = t.body,
                    color = c.label,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .iosClickable(onClick = onDismiss),
                )
                Text(
                    "New post",
                    style = t.headline,
                    color = c.label,
                    modifier = Modifier.align(Alignment.Center),
                )
                IOSFilledButton(
                    text = "Post",
                    onClick = {
                        onPost(text.trim())
                        onDismiss()
                    },
                    enabled = text.isNotBlank(),
                    compact = true,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth()) {
                MonogramAvatar(you.gradient, "Y", 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "You  @${you.handle}",
                        style = t.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = c.label,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().heightIn(min = 130.dp)) {
                        if (text.isEmpty()) {
                            Text(
                                "What's on your mind?",
                                style = t.postBody,
                                color = c.labelTertiary,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { if (it.length <= 500) text = it },
                            textStyle = t.postBody.copy(color = c.label),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "${text.length} / 500",
                style = t.footnote,
                color = if (text.length >= 480) c.orange else c.labelTertiary,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
