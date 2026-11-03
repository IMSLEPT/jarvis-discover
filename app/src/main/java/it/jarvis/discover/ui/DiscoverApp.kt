@file:OptIn(ExperimentalMaterial3Api::class)

package it.jarvis.discover.ui

import android.app.Activity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import it.jarvis.discover.data.DiscoverViewModel
import it.jarvis.discover.ui.components.BrandBackground
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.Wordmark
import it.jarvis.discover.ui.content.AudiobooksScreen
import it.jarvis.discover.ui.content.BooksScreen
import it.jarvis.discover.ui.content.MiniPlayer
import it.jarvis.discover.ui.content.NewsScreen
import it.jarvis.discover.ui.content.PodcastsScreen
import it.jarvis.discover.ui.creators.CreatorPage
import it.jarvis.discover.ui.search.SearchSheet
import it.jarvis.discover.ui.wellbeing.BenessereScreen
import it.jarvis.discover.ui.wellbeing.SessionDialog
import it.jarvis.discover.ui.screens.InterestsScreen
import it.jarvis.discover.ui.screens.MoodScreen
import it.jarvis.discover.ui.screens.OggiScreen
import it.jarvis.discover.ui.assistant.AssistantSheet
import it.jarvis.discover.ui.screens.RaccoltaScreen
import it.jarvis.discover.ui.screens.ScopriScreen
import it.jarvis.discover.ui.screens.VideoScreen

private const val ROUTE_MOOD = "mood"
private const val ROUTE_ONBOARDING = "interessi_iniziali"
private const val ROUTE_INTERESTS = "interessi"
private const val ROUTE_NEWS = "notizie"
private const val ROUTE_BOOKS = "libri"
private const val ROUTE_PODCASTS = "podcast"
private const val ROUTE_AUDIOBOOKS = "audiolibri"

/** Schermate a tutto schermo che gestiscono da sole i margini di sistema. */
private val FullScreenRoutes by lazy { setOf(ROUTE_MOOD, ROUTE_ONBOARDING, ROUTE_INTERESTS, Tab.Video.route) }

/** Le cinque sezioni principali della barra di navigazione. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Oggi("oggi", "Oggi", Icons.Rounded.WbSunny),
    Scopri("scopri", "Scopri", Icons.Rounded.Explore),
    Video("video", "Video", Icons.Rounded.PlayCircle),
    Benessere("benessere", "Benessere", Icons.Rounded.SelfImprovement),
    Raccolta("raccolta", "Raccolta", Icons.Rounded.CollectionsBookmark),
}

private enum class AppSheet { Search, Assistant, Settings }

@Composable
fun DiscoverApp(vm: DiscoverViewModel, darkTheme: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTab = Tab.entries.any { it.route == route }
    val isVideo = route == Tab.Video.route
    val edgeToEdge = route == null || route in FullScreenRoutes
    var sheet by remember { mutableStateOf<AppSheet?>(null) }
    // Il punto di partenza si decide una volta sola: gli interessi si chiedono solo al primo avvio.
    val startRoute = remember { if (vm.onboardingDone) ROUTE_MOOD else ROUTE_ONBOARDING }

    // Colore delle icone di sistema: chiare sul feed video (sempre scuro), altrimenti in base al tema.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme && !isVideo
        controller.isAppearanceLightNavigationBars = !darkTheme
    }

    fun openTab(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(Tab.Oggi.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BrandBackground(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = if (edgeToEdge) WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (isTab && !isVideo) {
                DiscoverTopBar(
                    onSearch = { sheet = AppSheet.Search },
                    onAssistant = { sheet = AppSheet.Assistant },
                    onSettings = { sheet = AppSheet.Settings },
                )
            }
        },
        bottomBar = {
            Column {
                if (route != ROUTE_MOOD && route != ROUTE_ONBOARDING) MiniPlayer(vm.audio)
                if (isTab) DiscoverBottomBar(currentRoute = route, onSelect = ::openTab)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(ROUTE_ONBOARDING) {
                InterestsScreen(
                    onboarding = true,
                    selected = vm.interests,
                    customTopics = vm.customTopics,
                    onToggle = vm::toggleInterest,
                    onAddCustom = vm::addCustomTopic,
                    onDone = {
                        vm.completeOnboarding()
                        navController.navigate(ROUTE_MOOD) {
                            popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(ROUTE_INTERESTS) {
                InterestsScreen(
                    onboarding = false,
                    selected = vm.interests,
                    customTopics = vm.customTopics,
                    onToggle = vm::toggleInterest,
                    onAddCustom = vm::addCustomTopic,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(ROUTE_MOOD) {
                MoodScreen(
                    selected = vm.modes,
                    onToggle = vm::toggleMode,
                    onContinue = {
                        navController.navigate(Tab.Oggi.route) {
                            popUpTo(ROUTE_MOOD) { inclusive = true }
                        }
                    },
                )
            }
            composable(Tab.Oggi.route) {
                OggiScreen(
                    modes = vm.modes,
                    minutes = vm.sessionMinutes,
                    interests = vm.interests,
                    acceptSubtitled = vm.acceptSubtitled,
                    news = vm.news,
                    videos = vm.videos,
                    todayBook = vm.todayBook,
                    onMinutesChange = vm::updateSessionMinutes,
                    onOpenVideo = { openTab(Tab.Video) },
                    onOpenBenessere = { openTab(Tab.Benessere) },
                    onOpenNews = { navController.navigate(ROUTE_NEWS) },
                    onOpenBooks = { navController.navigate(ROUTE_BOOKS) },
                )
            }
            composable(Tab.Scopri.route) {
                ScopriScreen(
                    selected = vm.interests,
                    onToggleTopic = vm::toggleInterest,
                    onOpenSearch = { sheet = AppSheet.Search },
                    onManageInterests = { navController.navigate(ROUTE_INTERESTS) },
                    onOpenNews = { navController.navigate(ROUTE_NEWS) },
                    onOpenBooks = { navController.navigate(ROUTE_BOOKS) },
                    onOpenPodcasts = { navController.navigate(ROUTE_PODCASTS) },
                    onOpenAudiobooks = { navController.navigate(ROUTE_AUDIOBOOKS) },
                    onOpenVideos = { openTab(Tab.Video) },
                )
            }
            composable(ROUTE_NEWS) { NewsScreen(vm.news, onBack = { navController.popBackStack() }) }
            composable(ROUTE_BOOKS) { BooksScreen(vm.library, vm.interests, onBack = { navController.popBackStack() }) }
            composable(ROUTE_PODCASTS) { PodcastsScreen(vm.library, vm.audio, vm.interests, onBack = { navController.popBackStack() }) }
            composable(ROUTE_AUDIOBOOKS) { AudiobooksScreen(vm.library, vm.audio, onBack = { navController.popBackStack() }) }
            composable(Tab.Video.route) {
                VideoScreen(
                    feed = vm.videos,
                    modes = vm.modes,
                    interests = vm.interests,
                    acceptSubtitled = vm.acceptSubtitled,
                    onOpenSettings = { sheet = AppSheet.Settings },
                    onOpenCreator = vm.videos.creators::open,
                )
            }
            composable(Tab.Benessere.route) {
                BenessereScreen(vm.wellbeing, vm.sessions, vm.breathing, vm.noise, onSaveGratitude = vm::addGratitude)
            }
            composable(Tab.Raccolta.route) {
                RaccoltaScreen(
                    savedVideos = vm.videos.saved,
                    onRemoveVideo = vm.videos::toggleSave,
                    gratitude = vm.gratitude,
                    onDeleteGratitude = vm::deleteGratitude,
                    interests = vm.interests,
                    onRemoveInterest = vm::toggleInterest,
                    onManageInterests = { navController.navigate(ROUTE_INTERESTS) },
                    follows = vm.videos.creators.follows,
                    onOpenCreator = vm.videos.creators::open,
                    library = vm.library,
                    audio = vm.audio,
                    profile = vm.videos.profile,
                )
            }
        }
    }
    }

    vm.videos.creators.page?.let { ref ->
        CreatorPage(
            ref = ref,
            feed = vm.videos,
            acceptSubtitled = vm.acceptSubtitled,
            onClose = vm.videos.creators::close,
        )
    }

    // Una sessione avviata dall'assistente deve vedersi anche fuori dalla scheda Benessere (lì c'è già il suo dialogo).
    if (vm.sessions.session != null && route != Tab.Benessere.route) SessionDialog(vm.sessions)

    sheet?.let { current ->
        key(current) {
            // Ricerca, assistente e impostazioni contengono un campo di testo: si aprono subito a tutta altezza.
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
                when (current) {
                    AppSheet.Search -> SearchSheet(
                        feed = vm.videos,
                        acceptSubtitled = vm.acceptSubtitled,
                        onOpenSettings = { sheet = AppSheet.Settings },
                    )
                    AppSheet.Assistant -> AssistantSheet(
                        engine = vm.assistant,
                        feed = vm.videos,
                        onStartSession = { session ->
                            sheet = null
                            vm.sessions.start(session)
                        },
                        onOpenSettings = { sheet = AppSheet.Settings },
                    )
                    AppSheet.Settings -> SettingsSheet(vm)
                }
            }
        }
    }
}

@Composable
private fun DiscoverTopBar(onSearch: () -> Unit, onAssistant: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = {
            Wordmark()
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Cerca") }
            IconButton(onClick = onAssistant) { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Assistente", tint = MaterialTheme.colorScheme.secondary) }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Impostazioni") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun DiscoverBottomBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f), tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
    }
}
