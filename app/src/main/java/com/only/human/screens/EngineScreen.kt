package com.only.human.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.GroupedRow
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.IOSBarTextAction
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.OhIcons
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.type
import com.only.human.world.Engine
import kotlinx.coroutines.launch

/**
 * The engine, in the open: an OpenAI-compatible endpoint and a key, both
 * yours. Kilo Gateway is pre-filled because it's the one we test against.
 * With no key the app never touches the network — the offline voices carry.
 */
@Composable
fun EngineScreen(onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val c = colors
    val t = type
    val cfg = Engine.config

    var on by remember(cfg) { mutableStateOf(cfg.on) }
    var endpoint by remember(cfg) { mutableStateOf(cfg.endpoint) }
    var model by remember(cfg) { mutableStateOf(cfg.model) }
    var apiKey by remember(cfg) { mutableStateOf(cfg.apiKey) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun current() = Engine.Config(
        endpoint = endpoint.trim(),
        model = model.trim(),
        apiKey = apiKey.trim(),
        on = on,
    )

    LargeTitleScaffold(
        title = "AI Engine",
        offsetProvider = offset,
        leading = { IOSBackButton(onBack) },
        actions = {
            IOSBarTextAction("Save") {
                Engine.save(current())
                onBack()
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item(key = "title") { LargeTitleHeader("AI Engine") }

            item(key = "switch") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    footer = "With this off, the fifteen write from their offline voices and the app never touches the network.",
                ) {
                    GroupedRow(
                        title = "AI engine",
                        subtitle = if (on) "On — they write for real" else "Off — offline voices",
                        trailing = {
                            if (on) {
                                Icon(
                                    imageVector = OhIcons.Check,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = {
                            on = !on
                            Engine.save(current().copy(on = on))
                        },
                    )
                }
            }

            item(key = "connection") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    header = "Connection",
                    footer = "Any OpenAI-compatible API works. Kilo Gateway is pre-filled — make a key at kilo.ai, paste it here, switch the engine on.",
                ) {
                    GroupedRow(
                        title = "Endpoint",
                        trailing = {
                            CompactValueField(
                                value = endpoint,
                                onValueChange = { endpoint = it },
                                placeholder = "https://…",
                            )
                        },
                        showDivider = true,
                    )
                    GroupedRow(
                        title = "Model",
                        trailing = {
                            CompactValueField(
                                value = model,
                                onValueChange = { model = it },
                                placeholder = "model-name",
                            )
                        },
                        showDivider = true,
                    )
                    GroupedRow(
                        title = "API key",
                        trailing = {
                            CompactValueField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                placeholder = "sk-…",
                                secret = true,
                            )
                        },
                    )
                }
            }

            item(key = "test") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IOSFilledButton(
                        text = if (testing) "Testing…" else "Test connection",
                        enabled = !testing,
                        onClick = {
                            Engine.save(current())
                            scope.launch {
                                testing = true
                                status = Engine.test()
                                testing = false
                            }
                        },
                    )
                    status?.let {
                        Text(
                            it,
                            style = t.footnote,
                            color = when {
                                it.startsWith("Connected") -> c.green
                                it.startsWith("Failed") -> c.red
                                else -> c.labelSecondary
                            },
                        )
                    }
                }
            }

            item(key = "footer") {
                Text(
                    "Settings are saved on this device only. The key is sent to nothing but the endpoint above.",
                    style = t.footnote,
                    color = c.labelSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                )
            }
        }
    }
}

/** iOS-style right-aligned value field inside a settings row. */
@Composable
private fun CompactValueField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
) {
    val c = colors
    val t = type
    Box(Modifier.width(190.dp)) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = t.subheadline,
                color = c.labelTertiary,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = t.subheadline.copy(color = c.labelSecondary, textAlign = TextAlign.End),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
