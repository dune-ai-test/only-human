package com.only.human.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.only.human.design.Motion
import com.only.human.design.colors
import com.only.human.design.components.IOSTabBar
import com.only.human.design.components.LocalTabBarInset
import com.only.human.design.components.OhIcons
import com.only.human.design.components.TabSpec
import com.only.human.screens.ComposerSheet
import com.only.human.screens.DiscoverScreen
import com.only.human.screens.FeedScreen
import com.only.human.screens.NotificationsScreen
import com.only.human.screens.ProfileScreen
import com.only.human.screens.SettingsScreen
import com.only.human.screens.YouScreen
import com.only.human.world.World

/** Where you can be. Tabs are depth 0; pushed screens stack on top. */
sealed interface Route {
    data class Home(val tab: Int) : Route
    data class Person(val id: String) : Route
    data object Settings : Route
}

/** Route + stack depth — depth drives push/pop animation direction. */
data class ScreenKey(val route: Route, val depth: Int)

@Stable
class NavController {
    var stack by mutableStateOf<List<Route>>(emptyList())
        private set

    fun push(route: Route) {
        stack = stack + route
    }

    fun pop() {
        if (stack.isNotEmpty()) stack = stack.dropLast(1)
    }

    fun clear() {
        stack = emptyList()
    }
}

@Composable
fun AppShell() {
    val nav = remember { NavController() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var composerOpen by rememberSaveable { mutableStateOf(false) }

    val density = LocalDensity.current
    val navBarInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val tabBarInset = 49.dp + navBarInset + 10.dp

    val key = ScreenKey(nav.stack.lastOrNull() ?: Route.Home(tab), nav.stack.size)

    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }

    CompositionLocalProvider(LocalTabBarInset provides tabBarInset) {
        Box(Modifier.fillMaxSize().background(colors.grouped)) {
            AnimatedContent(
                targetState = key,
                transitionSpec = {
                    val push = targetState.depth > initialState.depth
                    val pop = targetState.depth < initialState.depth
                    when {
                        push ->
                            (slideInHorizontally(Motion.screen) { it } + fadeIn(Motion.screenFade)) togetherWith
                                (slideOutHorizontally(Motion.screen) { -it / 3 } + fadeOut(tween(220)))
                        pop ->
                            (slideInHorizontally(Motion.screen) { -it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(Motion.screen) { it } + fadeOut(Motion.screenFade))
                        else ->
                            fadeIn(Motion.tab) togetherWith fadeOut(Motion.tab)
                    }
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { screenKey ->
                when (val route = screenKey.route) {
                    is Route.Home -> when (route.tab) {
                        0 -> FeedScreen(onOpenProfile = { nav.push(Route.Person(it)) })
                        1 -> DiscoverScreen(onOpenProfile = { nav.push(Route.Person(it)) })
                        3 -> NotificationsScreen(onOpenProfile = { nav.push(Route.Person(it)) })
                        else -> YouScreen(
                            onOpenSettings = { nav.push(Route.Settings) },
                            onCompose = { composerOpen = true },
                        )
                    }
                    is Route.Person -> ProfileScreen(
                        personaId = route.id,
                        onBack = { nav.pop() },
                    )
                    Route.Settings -> SettingsScreen(onBack = { nav.pop() })
                }
            }

            IOSTabBar(
                tabs = tabs,
                selectedIndex = tab,
                onSelect = { index ->
                    if (index == 2) {
                        composerOpen = true
                    } else {
                        nav.clear()
                        tab = index
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (composerOpen) {
        ComposerSheet(
            onDismiss = { composerOpen = false },
            onPost = { World.addPost(it) },
        )
    }
}

private val tabs = listOf(
    TabSpec("Feed", OhIcons.Home),
    TabSpec("Discover", OhIcons.Search),
    TabSpec("Post", OhIcons.Plus),
    TabSpec("Activity", OhIcons.Bell),
    TabSpec("You", OhIcons.Person),
)
