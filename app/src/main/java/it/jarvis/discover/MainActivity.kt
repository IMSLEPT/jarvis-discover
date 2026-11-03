package it.jarvis.discover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import it.jarvis.discover.data.DiscoverViewModel
import it.jarvis.discover.data.ThemeMode
import it.jarvis.discover.ui.DiscoverApp
import it.jarvis.discover.ui.theme.DiscoverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: DiscoverViewModel = viewModel()
            val darkTheme = when (vm.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            DiscoverTheme(darkTheme = darkTheme) {
                DiscoverApp(vm = vm, darkTheme = darkTheme)
            }
        }
    }
}
