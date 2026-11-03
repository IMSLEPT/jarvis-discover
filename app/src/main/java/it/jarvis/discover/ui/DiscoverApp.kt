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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import it.jarvis.discover.data.DiscoverViewModel
import it.jarvis.discover.data.ThemeMode
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.PhaseChip
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.screens.BenessereScreen
import it.jarvis.discover.ui.screens.MoodScreen
import it.jarvis.discover.ui.screens.OggiScreen
import it.jarvis.discover.ui.screens.RaccoltaScreen
import it.jarvis.discover.ui.screens.ScopriScreen
import it.jarvis.discover.ui.screens.VideoScreen

private const val ROUTE_MOOD = "mood"

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
    val edgeToEdge = isVideo || route == ROUTE_MOOD || route == null
    var sheet by remember { mutableStateOf<AppSheet?>(null) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            if (isTab) {
                DiscoverBottomBar(currentRoute = route, onSelect = ::openTab)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_MOOD,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
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
                    onMinutesChange = vm::updateSessionMinutes,
                    onOpenVideo = { openTab(Tab.Video) },
                    onOpenBenessere = { openTab(Tab.Benessere) },
                )
            }
            composable(Tab.Scopri.route) { ScopriScreen(onOpenSearch = { sheet = AppSheet.Search }) }
            composable(Tab.Video.route) { VideoScreen() }
            composable(Tab.Benessere.route) { BenessereScreen(onSaveGratitude = vm::addGratitude) }
            composable(Tab.Raccolta.route) {
                RaccoltaScreen(gratitude = vm.gratitude, onDeleteGratitude = vm::deleteGratitude)
            }
        }
    }

    sheet?.let { current ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            when (current) {
                AppSheet.Search -> ComingSoonSheet(
                    emoji = "🔎",
                    title = "Ricerca universale",
                    body = "Cercherai persone, canali, argomenti e conferenze da un unico posto, con filtri per durata, lingua e popolarità.",
                    phase = 4,
                )
                AppSheet.Assistant -> ComingSoonSheet(
                    emoji = "✨",
                    title = "Il tuo assistente",
                    body = "Potrai chiedergli di trovare video, libri e notizie reali, spiegarti un contenuto o guidarti in cinque minuti di mindfulness.",
                    phase = 8,
                )
                AppSheet.Settings -> SettingsSheet(vm)
            }
        }
    }
}

@Composable
private fun DiscoverTopBar(onSearch: () -> Unit, onAssistant: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text("Discover", style = MaterialTheme.typography.titleLarge)
            }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Cerca") }
            IconButton(onClick = onAssistant) { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Assistente") }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Impostazioni") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun DiscoverBottomBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun ComingSoonSheet(emoji: String, title: String, body: String, phase: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmojiBubble(emoji = emoji, size = 64)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PhaseChip(phase)
    }
}

@Composable
private fun SettingsSheet(vm: DiscoverViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Aspetto", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vm.themeMode == mode,
                        onClick = { vm.updateThemeMode(mode) },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Lingua dei contenuti", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Accetta video stranieri sottotitolati",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Se disattivato, vedrai solo contenuti parlati in italiano o doppiati.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.acceptSubtitled, onCheckedChange = vm::updateAcceptSubtitled)
            }
        }
    }
}
