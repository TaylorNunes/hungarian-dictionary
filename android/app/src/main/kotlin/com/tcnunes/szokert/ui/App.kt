package com.tcnunes.szokert.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tcnunes.szokert.R
import com.tcnunes.szokert.SzokertApp
import com.tcnunes.szokert.ui.about.AboutScreen
import com.tcnunes.szokert.ui.entry.EntryScreen
import com.tcnunes.szokert.ui.guide.GuideScreen
import com.tcnunes.szokert.ui.saved.SavedScreen
import com.tcnunes.szokert.ui.search.SearchScreen
import com.tcnunes.szokert.ui.search.SearchViewModel
import com.tcnunes.szokert.ui.theme.Szokert
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable data object SearchRoute
@Serializable data class EntryRoute(val word: String, val pos: String, val id: Int? = null, val from: String? = null)
@Serializable data object SavedRoute
@Serializable data class GuideRoute(val anchor: String? = null)
@Serializable data object AboutRoute

private class Tab(val label: String, val icon: Int, val route: Any, val type: KClass<*>)

private val TABS = listOf(
    Tab("Search", R.drawable.ic_search, SearchRoute, SearchRoute::class),
    Tab("Saved", R.drawable.ic_star_border, SavedRoute, SavedRoute::class),
    Tab("Guide", R.drawable.ic_book, GuideRoute(), GuideRoute::class),
    Tab("About", R.drawable.ic_info, AboutRoute, AboutRoute::class),
)

/** The app's screens and the bottom navigation between them. */
@Composable
fun App(app: SzokertApp) {
    val c = Szokert.colors
    val nav = rememberNavController()
    val search: SearchViewModel = viewModel()
    val dataState by app.data.state.collectAsStateWithLifecycle()
    val savedCount by app.saved.list.collectAsStateWithLifecycle()
    val searchList = rememberLazyListState()
    val back by nav.currentBackStackEntryAsState()

    // The download notification needs permission on Android 13+; the download runs either way.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.data.download() }
    val startDownload = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else app.data.download()
    }
    LaunchedEffect(Unit) { runCatching { app.data.checkForUpdate() } }

    // A word page belongs to the tab it was opened from.
    val current = back?.destination
    val tabType = TABS.firstOrNull { t -> current?.hasRoute(t.type) == true }?.type
        ?: nav.previousBackStackEntry?.destination?.let { prev -> TABS.firstOrNull { prev.hasRoute(it.type) }?.type }

    fun lookUp(word: String) {
        search.lookUp(word)
        nav.navigate(SearchRoute) {
            popUpTo(SearchRoute)
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = c.bg,
        bottomBar = {
            NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
                for (tab in TABS) {
                    NavigationBarItem(
                        selected = tab.type == tabType,
                        onClick = { nav.goToTab(tab.route) },
                        icon = {
                            BadgedBox(badge = {
                                if (tab.route == SavedRoute && savedCount.isNotEmpty()) Badge(containerColor = c.accent, contentColor = c.onAccent) { Text("${savedCount.size}") }
                            }) { Icon(painterResource(tab.icon), contentDescription = null) }
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accentStrong, selectedTextColor = c.accentStrong, indicatorColor = c.accentSoft,
                            unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            // A quick fade-in over the plain background; the default slow cross-fade overlaps two screens' text.
            NavHost(
                nav,
                startDestination = SearchRoute,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { ExitTransition.None },
            ) {
                composable<SearchRoute> {
                    val installed = dataState.installed
                    if (installed == null) {
                        if (!dataState.loading) DownloadScreen(dataState.task, onDownload = startDownload)
                    } else {
                        SearchScreen(
                            search, dataState, app.saved, app.glossary, searchList,
                            onOpen = { r, from ->
                                nav.navigate(EntryRoute(r.lemma.w, r.lemma.pos, r.lemmaId, from?.takeIf { it.lowercase() != r.lemma.w.lowercase() }))
                            },
                            onOpenPartial = { p -> nav.navigate(EntryRoute(p.word, p.pos, p.lemmaId)) },
                            onUpdate = startDownload,
                        )
                    }
                }
                composable<EntryRoute> { entry ->
                    val r = entry.toRoute<EntryRoute>()
                    val source = dataState.installed?.source
                    if (source == null) {
                        Text("The dictionary isn't downloaded yet.", color = c.muted, modifier = Modifier.padding(16.dp))
                    } else {
                        EntryScreen(
                            r.word, r.pos, r.id, r.from, source, app.glossary, app.saved,
                            onBack = { nav.popBackStack() },
                            onWord = ::lookUp,
                            onGuide = { anchor -> nav.navigate(GuideRoute(anchor)) },
                        )
                    }
                }
                composable<SavedRoute> {
                    SavedScreen(app.saved, onOpen = { s -> nav.navigate(EntryRoute(s.word, s.pos, s.lemmaId)) })
                }
                composable<GuideRoute> { entry ->
                    GuideScreen(app.glossary, dataState.installed?.source, entry.toRoute<GuideRoute>().anchor)
                }
                composable<AboutRoute> {
                    AboutScreen(app.data, app.settings, onGuide = { nav.goToTab(GuideRoute()) })
                }
            }
        }
    }
}

/** Switch tabs, keeping each tab's own history and state. */
private fun NavHostController.goToTab(route: Any) {
    navigate(route) {
        popUpTo(SearchRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
