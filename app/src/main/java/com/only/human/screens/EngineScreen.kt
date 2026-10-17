package com.only.human.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.only.human.design.components.Hairline
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.IOSBarTextAction
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.OhIcons
import com.only.human.design.components.RowChevron
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.iosClickable
import com.only.human.design.type
import com.only.human.world.Engine
import kotlinx.coroutines.launch

/**
 * The engine, in the open: an OpenAI-compatible endpoint, a model, an
 * optional key. Kilo's gateway is pre-filled out of the box — free models,
 * no key needed. Models come from the endpoint's /models and are kept on
 * this device for next time. With the engine off, the fifteen stay quiet
 * and the app never touches the network.
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
    var models by remember(endpoint) { mutableStateOf(Engine.cachedModels(endpoint)) }
    var picking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun current() = Engine.Config(
        endpoint = endpoint.trim(),
        model = model.trim(),
        apiKey = apiKey.trim(),
        on = on,
    )

    if (picking) {
        ModelPickerScreen(
            endpoint = endpoint,
            apiKey = apiKey,
            models = models,
            selected = model,
            onFetched = { models = it },
            onPick = {
                model = it
                picking = false
            },
            onBack = { picking = false },
        )
        return
    }

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
                    footer = "With this off, the fifteen stay quiet — nothing is written and the app never touches the network.",
                ) {
                    GroupedRow(
                        title = "AI engine",
                        subtitle = if (on) "On — they write for real" else "Off — they stay quiet",
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
                    footer = "Any OpenAI-compatible API works, including local servers that ask for no key. Kilo Gateway is pre-filled — add a key for it, or leave the field empty.",
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
                                placeholder = "optional",
                                secret = true,
                            )
                        },
                    )
                }
            }

            item(key = "modellist") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    header = "Model list",
                    footer = "Fetched from the endpoint's /v1/models and kept on this device, so it's there next time.",
                ) {
                    GroupedRow(
                        title = "Choose model",
                        subtitle = if (models.isNotEmpty()) {
                            "${models.size} fetched — tap to browse"
                        } else {
                            "Fetch the list from the endpoint"
                        },
                        trailing = { RowChevron() },
                        onClick = { picking = true },
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

/** The full-screen model browser: fetch, search, tap to choose. */
@Composable
internal fun ModelPickerScreen(
    endpoint: String,
    apiKey: String,
    models: List<String>,
    selected: String,
    onFetched: (List<String>) -> Unit,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    title: String = "Models",
    note: String? = null,
    defaultTitle: String? = null,
) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val c = colors
    val t = type
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fetch() {
        if (fetching) return
        scope.launch {
            fetching = true
            error = null
            val result = Engine.fetchModels(endpoint, apiKey)
            if (result != null) {
                Engine.cacheModels(endpoint, result)
                onFetched(result)
            } else {
                error = Engine.lastError ?: "Couldn't fetch the list."
            }
            fetching = false
        }
    }

    // Nothing cached for this endpoint — go get a list.
    LaunchedEffect(Unit) { if (models.isEmpty()) fetch() }

    val filtered = if (query.isBlank()) {
        models
    } else {
        models.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    LargeTitleScaffold(
        title = title,
        offsetProvider = offset,
        leading = { IOSBackButton(onBack) },
        actions = {
            IOSBarTextAction(if (fetching) "Fetching…" else "Fetch") { fetch() }
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
            item(key = "title") { LargeTitleHeader(title) }

            if (note != null) {
                item(key = "note") {
                    Text(
                        note,
                        style = t.footnote,
                        color = c.labelSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                }
            }

            item(key = "search") {
                InsetGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                        if (query.isEmpty()) {
                            Text("Search", style = t.body, color = c.labelTertiary)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = t.body.copy(color = c.label),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (defaultTitle != null) {
                item(key = "default") {
                    InsetGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ModelPickRow(
                            id = defaultTitle,
                            selected = selected.isBlank(),
                            showDivider = false,
                            onClick = { onPick("") },
                        )
                    }
                }
            }

            val err = error
            if (err != null) {
                item(key = "error") {
                    Text(
                        err,
                        style = t.footnote,
                        color = c.red,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                    )
                }
            } else if (fetching && models.isEmpty()) {
                item(key = "loading") {
                    Text(
                        "Fetching from /v1/models…",
                        style = t.footnote,
                        color = c.labelSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                    )
                }
            } else if (models.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing here yet — tap Fetch to pull the list from this endpoint.",
                        style = t.footnote,
                        color = c.labelSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                    )
                }
            }

            if (models.isNotEmpty() && filtered.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        "No model matches \u201C${query.trim()}\u201D.",
                        style = t.footnote,
                        color = c.labelSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                    )
                }
            }

            itemsIndexed(filtered, key = { _, id -> id }) { i, id ->
                ModelPickRow(
                    id = id,
                    selected = id == selected,
                    showDivider = i < filtered.lastIndex,
                    onClick = { onPick(id) },
                )
            }
        }
    }
}

/** One model id in the picker — accent and a check when chosen. */
@Composable
private fun ModelPickRow(
    id: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val c = colors
    val t = type
    Column(
        Modifier
            .fillMaxWidth()
            .iosClickable(highlightShape = RoundedCornerShape(0.dp), onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                id,
                style = t.body,
                color = if (selected) c.accent else c.label,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = OhIcons.Check,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) Hairline(startInset = 16.dp)
    }
}
