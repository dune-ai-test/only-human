package com.only.human.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.only.human.BuildConfig
import com.only.human.design.components.GroupedRow
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.IosSwitch
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.RowChevron
import com.only.human.design.components.rememberCollapseOffset
import com.only.human.world.Engine
import com.only.human.world.World

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenEngine: () -> Unit) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)

    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // The switch follows the truth: granted or not.
        World.setNotifyEnabled(granted)
    }
    val onToggleNotifications: () -> Unit = {
        when {
            World.notifyEnabled -> World.setNotifyEnabled(false)
            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> World.setNotifyEnabled(true)
        }
    }

    LargeTitleScaffold(
        title = "Settings",
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
            item(key = "title") { LargeTitleHeader("Settings") }
            item(key = "ai") {
                val cfg = Engine.config
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    header = "AI",
                    footer = "The fifteen write through Kilo's free gateway out of the box — no key needed. " +
                        "Or point them at any OpenAI-compatible endpoint, local servers included.",
                ) {
                    GroupedRow(
                        "AI engine",
                        subtitle = if (cfg.ready) "On — writing for real" else "Off — they stay quiet",
                        trailing = { RowChevron() },
                        showDivider = true,
                        onClick = onOpenEngine,
                    )
                    GroupedRow(
                        "Endpoint",
                        subtitle = cfg.endpoint.ifBlank { "not set" },
                        trailing = { RowChevron() },
                        showDivider = true,
                        onClick = onOpenEngine,
                    )
                    GroupedRow(
                        "Model",
                        subtitle = cfg.model.ifBlank { "not set" },
                        trailing = { RowChevron() },
                        showDivider = true,
                        onClick = onOpenEngine,
                    )
                    GroupedRow(
                        "API key",
                        subtitle = if (cfg.apiKey.isBlank()) "optional — not set" else "saved on this device",
                        showDivider = false,
                        onClick = onOpenEngine,
                    )
                }
            }
            item(key = "notifications") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    header = "Notifications",
                    footer = "Local only — no push, no server. A quiet line when one of them " +
                        "writes to you while you're away, and nothing while you're here.",
                ) {
                    GroupedRow(
                        "Tell me when they act",
                        subtitle = if (World.notifyEnabled) "On" else "Off",
                        trailing = { IosSwitch(World.notifyEnabled) },
                        showDivider = false,
                        onClick = onToggleNotifications,
                    )
                }
            }
            item(key = "network") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    header = "Network",
                ) {
                    GroupedRow(
                        "Simulation",
                        subtitle = "Catch-up — the world moves while you're away",
                        showDivider = true,
                    )
                    GroupedRow("World clock", subtitle = "Local time", showDivider = false)
                }
            }
            item(key = "about") {
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    header = "About",
                    footer = "Nightly build. If something feels off, it probably is — this is the ground floor.",
                ) {
                    GroupedRow("Only Human", subtitle = "v${BuildConfig.VERSION_NAME}", showDivider = true)
                    GroupedRow("Cast", subtitle = "15 people · 1 human", showDivider = false)
                }
            }
        }
    }
}
