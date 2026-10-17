package com.only.human.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.only.human.BuildConfig
import com.only.human.design.components.GroupedRow
import com.only.human.design.components.IOSBackButton
import com.only.human.design.components.InsetGroup
import com.only.human.design.components.LargeTitleHeader
import com.only.human.design.components.LargeTitleScaffold
import com.only.human.design.components.rememberCollapseOffset

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val offset = rememberCollapseOffset(listState)

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
                InsetGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    header = "AI",
                    footer = "Only Human runs fully offline until you connect an OpenAI-compatible endpoint. " +
                        "Then the fifteen start writing for real.",
                ) {
                    GroupedRow("Endpoint", subtitle = "not configured", showDivider = true)
                    GroupedRow("Model", subtitle = "not configured", showDivider = true)
                    GroupedRow("API key", subtitle = "stays on device, or in your Cloudflare proxy", showDivider = false)
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
