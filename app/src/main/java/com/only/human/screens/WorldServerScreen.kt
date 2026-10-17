package com.only.human.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.only.human.design.colors
import com.only.human.design.components.GroupedRow
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.IOSFilledButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.design.type
import com.only.human.world.Server
import com.only.human.world.relativeTime
import kotlinx.coroutines.delay

/**
 * The window handle. From here the world moves off this phone and onto a
 * server of your own: create one, recover one on a new phone, watch it
 * sync, set the daily budget, take a copy, or walk away from it.
 */
@Composable
fun WorldServerScreen(onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)
    val c = colors
    val t = type
    val context = LocalContext.current

    fun copy(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("Only Human", text))
    }

    var url by remember { mutableStateOf(Server.baseUrl) }
    var worldIn by remember { mutableStateOf("") }
    var keyIn by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteBad by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var budgetOpen by remember { mutableStateOf(false) }

    // A five-second beat so "synced 2m ago" stays honest while you watch.
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick = System.currentTimeMillis()
        }
    }

    fun say(message: String?, bad: Boolean = false) {
        note = message
        noteBad = bad
    }

    LargeTitleScaffold(
        title = "World Server",
        offsetProvider = offset,
        leading = { IOSBackButton(onBack) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "title") { LargeTitleHeader("World Server") }

            if (!Server.active) {
                item(key = "url") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        header = "Server",
                        footer = "The address of your own Worker — server/README.md walks through deploying one. " +
                            "Nothing here is sent anywhere until you tap a button below.",
                    ) {
                        GroupedRow(
                            "Server URL",
                            trailing = { CompactValueField(url, { url = it }, "https://…") },
                            showDivider = false,
                        )
                    }
                }
                item(key = "create") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        header = "Move the world",
                        footer = "Ships your cast, your name and your follows to the server. The world starts " +
                            "living there — ticking whether this phone is on or not. This device becomes the window.",
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IOSFilledButton(
                                text = if (busy) "Working…" else "Create the world",
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    Server.create(url) { err ->
                                        busy = false
                                        say(
                                            err ?: "The world exists now — it will fill itself up over the next minutes.",
                                            bad = err != null,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                item(key = "recover") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        header = "Coming back",
                        footer = "Had a world already? Its id and recovery key were shown when it was created. " +
                            "On a new phone, enter both to pick the world back up where it stands.",
                    ) {
                        GroupedRow(
                            "World ID",
                            trailing = { CompactValueField(worldIn, { worldIn = it }, "xxxxxxxx-…") },
                            showDivider = true,
                        )
                        GroupedRow(
                            "Recovery key",
                            trailing = { CompactValueField(keyIn, { keyIn = it }, "ABCD-…", secret = true) },
                            showDivider = true,
                        )
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IOSFilledButton(
                                text = if (busy) "Working…" else "Recover the world",
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    Server.recover(url, worldIn, keyIn) { err ->
                                        busy = false
                                        say(err ?: "Welcome back — the world is on this phone again.", bad = err != null)
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                item(key = "status") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        header = "World",
                        footer = "The world lives on the server now and keeps its own time. This phone holds " +
                            "a cache and syncs quietly while it's open.",
                    ) {
                        GroupedRow(
                            "Status",
                            subtitle = statusLine(),
                            showDivider = true,
                        )
                        GroupedRow(
                            "World ID",
                            subtitle = "tap to copy",
                            trailing = {
                                Text(Server.worldId.take(8) + "…", style = t.footnote, color = c.labelSecondary)
                            },
                            showDivider = true,
                            onClick = {
                                copy(Server.worldId)
                                say("World ID copied.")
                            },
                        )
                        GroupedRow(
                            "Recovery key",
                            subtitle = "tap to copy — keep it somewhere safe",
                            trailing = {
                                Text(maskKey(Server.key), style = t.footnote, color = c.labelSecondary)
                            },
                            showDivider = true,
                            onClick = {
                                copy(Server.key)
                                say("Recovery key copied.")
                            },
                        )
                        GroupedRow(
                            "Last sync",
                            subtitle = if (Server.lastSyncAt == 0L) "not yet" else relativeTime(Server.lastSyncAt, tick),
                            showDivider = false,
                        )
                    }
                }
                item(key = "sync") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        footer = when {
                            Server.outboxCount > 0 ->
                                "${Server.outboxCount} of your actions are still waiting for the network — nothing is lost."
                            else -> null
                        },
                    ) {
                        GroupedRow(
                            "Sync now",
                            subtitle = if (Server.syncing) "Syncing…" else "Pull whatever's new",
                            showDivider = false,
                            onClick = { Server.kick() },
                        )
                    }
                }
                item(key = "budget") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        header = "AI calls",
                        footer = "What the world is allowed to write in a day. When the budget runs low, " +
                            "ambient life pauses — your own posts, replies and DMs always go first.",
                    ) {
                        GroupedRow(
                            "Daily AI calls",
                            subtitle = "up to ${Server.budget} a day",
                            showDivider = false,
                            onClick = { budgetOpen = true },
                        )
                    }
                }
                item(key = "data") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        header = "Data",
                        footer = "Export copies the whole world — every post, reply, DM and memory — to the " +
                            "clipboard as JSON. Delete is forever.",
                    ) {
                        GroupedRow(
                            "Export world",
                            subtitle = "copy everything as JSON",
                            showDivider = true,
                            onClick = {
                                Server.exportWorld { json, err ->
                                    if (json != null) {
                                        copy(json)
                                        say("Copied — ${json.length} characters.")
                                    } else {
                                        say(err ?: "Couldn't export.", bad = true)
                                    }
                                }
                            },
                        )
                        GroupedRow(
                            "Delete world…",
                            titleColor = c.red,
                            showDivider = false,
                            onClick = { confirmDelete = true },
                        )
                    }
                }
                item(key = "leave") {
                    InsetGroup(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        footer = "Disconnecting leaves the world on the server, untouched. This phone goes " +
                            "back to its own local world — the one from before, as it was.",
                    ) {
                        GroupedRow(
                            "Disconnect",
                            showDivider = false,
                            onClick = { confirmDisconnect = true },
                        )
                    }
                }
            }

            note?.let { message ->
                item(key = "note") {
                    Text(
                        message,
                        style = t.footnote,
                        color = if (noteBad) c.red else c.labelSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }

    if (budgetOpen) {
        var budgetText by remember { mutableStateOf(Server.budget.toString()) }
        AlertDialog(
            onDismissRequest = { budgetOpen = false },
            title = { Text("Daily AI calls", style = t.headline) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Between 0 and 1000. The default is 200.",
                        style = t.footnote,
                        color = c.labelSecondary,
                    )
                    Box(Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = budgetText,
                            onValueChange = { budgetText = it.filter { ch -> ch.isDigit() }.take(4) },
                            singleLine = true,
                            textStyle = t.body.copy(color = c.label),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        budgetOpen = false
                        val value = budgetText.toIntOrNull()
                        if (value != null) {
                            Server.applyBudget(value)
                            say("The world may write up to ${value.coerceIn(0, 1000)} times a day.")
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { budgetOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this world?", style = t.headline) },
            text = {
                Text(
                    "Every post, every DM, everyone — gone from the server, for good. This phone steps back " +
                        "to a local world with a fresh start.",
                    style = t.footnote,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        busy = true
                        Server.deleteWorld { err ->
                            busy = false
                            say(err ?: "The world is gone.", bad = err != null)
                        }
                    },
                ) { Text("Delete", color = c.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            },
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect?", style = t.headline) },
            text = {
                Text(
                    "The world stays on the server exactly as it is. This phone stops syncing and goes " +
                        "back to the local world — you can point it back here any time.",
                    style = t.footnote,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisconnect = false
                        Server.disconnect()
                        say("Disconnected — the world keeps ticking without us.")
                    },
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Stay") }
            },
        )
    }
}

/** One line about where we stand, honest about the in-between states. */
private fun statusLine(): String {
    val s = Server.state
    return when {
        Server.syncing -> "Syncing…"
        s is Server.State.Live -> "Live — the world is ticking"
        s is Server.State.Offline -> "Offline — ${s.reason}"
        else -> "Waiting for the first sync"
    }
}

/** The key, shown half-dressed — tap the row to copy the real thing. */
private fun maskKey(key: String): String =
    if (key.length <= 8) "••••" else key.take(4) + " •••• " + key.takeLast(4)
