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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.only.human.design.Motion
import com.only.human.design.colors
import com.only.human.design.components.IOSTabBar
import com.only.human.design.components.LocalTabBarInset
import com.only.human.design.components.OhIcons
import com.only.human.design.components.TabSpec
import com.only.human.screens.ComposerSheet
import com.only.human.screens.CreatePersonaScreen
import com.only.human.screens.DiscoverScreen
import com.only.human.screens.DmScreen
import com.only.human.screens.EngineScreen
import com.only.human.screens.FeedScreen
import com.only.human.screens.MessagesScreen
import com.only.human.screens.NotificationsScreen
import com.only.human.screens.PersonModelScreen
import com.only.human.screens.PostPage
import com.only.human.screens.ProfileScreen
import com.only.human.screens.SettingsScreen
import com.only.human.screens.StoryViewer
import com.only.human.screens.TagScreen
import com.only.human.screens.YouScreen
import com.only.human.world.World

/** Where you can be. Tabs are depth 0; pushed screens stack on top. */
sealed interface Route {
    data class Home(val tab: Int) : Route
    data class Person(val id: String) : Route
    data class PersonModel(val id: String) : Route
    data class Post(val id: String) : Route
    data class Dm(val id: String) : Route
    data object Messages : Route
    data object Settings : Route
    data object Engine : Route
    data object CreatePersona : Route
    data class Tag(val tag: String) : Route
    data class Stories(val startId: String) : Route
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

    // Everything unseen lives behind the Activity tab: notifications + DMs.
    val activityBadge = World.unreadActivityCount() + World.unreadDmsTotal()
    val tabs = listOf(
        TabSpec("Feed", OhIcons.Home),
        TabSpec("Discover", OhIcons.Search),
        TabSpec("Post", OhIcons.Plus),
        TabSpec("Activity", OhIcons.Bell, badge = activityBadge),
        TabSpec("You", OhIcons.Person),
    )

    CompositionLocalProvider(LocalTabBarInset provides tabBarInset) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.grouped)
                // iOS back gesture: a drag in from the left edge pops the top
                // screen. Taps are never touched — only edge drags are claimed.
                .pointerInput(Unit) {
                    val edgePx = 26.dp.toPx()
                    val popPx = 64.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (nav.stack.isEmpty() || down.position.x > edgePx) {
                            return@awaitEachGesture
                        }
                        var drag = 0f
                        val claim = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            drag = over
                        } ?: return@awaitEachGesture
                        horizontalDrag(claim.id) { change ->
                            change.consume()
                            drag += change.positionChange().x
                        }
                        if (drag > popPx) nav.pop()
                    }
                },
        ) {
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
                        0 -> FeedScreen(
                            onOpenProfile = { nav.push(Route.Person(it)) },
                            onOpenPost = { nav.push(Route.Post(it)) },
                            onOpenStory = { nav.push(Route.Stories(it)) },
                        )
                        1 -> DiscoverScreen(
                            onOpenProfile = { nav.push(Route.Person(it)) },
                            onOpenTag = { nav.push(Route.Tag(it)) },
                        )
                        3 -> NotificationsScreen(
                            onOpenProfile = { nav.push(Route.Person(it)) },
                            onOpenMessages = { nav.push(Route.Messages) },
                            onOpenDm = { nav.push(Route.Dm(it)) },
                        )
                        else -> YouScreen(
                            onOpenSettings = { nav.push(Route.Settings) },
                            onOpenEngine = { nav.push(Route.Engine) },
                            onCreatePersona = { nav.push(Route.CreatePersona) },
                            onCompose = { composerOpen = true },
                            onOpenPost = { nav.push(Route.Post(it)) },
                        )
                    }
                    is Route.Person -> ProfileScreen(
                        personaId = route.id,
                        onBack = { nav.pop() },
                        onOpenPost = { nav.push(Route.Post(it)) },
                        onOpenDm = { nav.push(Route.Dm(it)) },
                        onOpenModel = { nav.push(Route.PersonModel(it)) },
                    )
                    is Route.PersonModel -> PersonModelScreen(
                        personaId = route.id,
                        onBack = { nav.pop() },
                    )
                    is Route.Post -> PostPage(
                        postId = route.id,
                        onBack = { nav.pop() },
                        onOpenProfile = { nav.push(Route.Person(it)) },
                    )
                    is Route.Dm -> DmScreen(
                        personaId = route.id,
                        onBack = { nav.pop() },
                    )
                    Route.Messages -> MessagesScreen(
                        onBack = { nav.pop() },
                        onOpenDm = { nav.push(Route.Dm(it)) },
                    )
                    Route.Settings -> SettingsScreen(
                        onBack = { nav.pop() },
                        onOpenEngine = { nav.push(Route.Engine) },
                    )
                    Route.Engine -> EngineScreen(onBack = { nav.pop() })
                    Route.CreatePersona -> CreatePersonaScreen(
                        onBack = { nav.pop() },
                        onCreated = { id ->
                            nav.pop()
                            nav.push(Route.Person(id))
                        },
                    )
                    is Route.Tag -> TagScreen(
                        tag = route.tag,
                        onBack = { nav.pop() },
                        onOpenProfile = { nav.push(Route.Person(it)) },
                        onOpenPost = { nav.push(Route.Post(it)) },
                    )
                    is Route.Stories -> StoryViewer(
                        startId = route.startId,
                        onClose = { nav.pop() },
                    )
                }
            }

            // Stories play full-screen, the way they should — no tab bar.
            if (key.route !is Route.Stories) {
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
    }

    if (composerOpen) {
        ComposerSheet(
            onDismiss = { composerOpen = false },
            onPost = { World.addPost(it) },
        )
    }
}
