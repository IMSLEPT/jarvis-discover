package it.jarvis.discover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import it.jarvis.discover.data.DiscoverViewModel
import it.jarvis.discover.data.ThemeMode
import it.jarvis.discover.ui.DiscoverApp
import it.jarvis.discover.ui.theme.DiscoverTheme

class MainActivity : ComponentActivity() {
    private val model: DiscoverViewModel by viewModels()

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

    override fun onStop() {
        // Per sicurezza, l'allenamento di respirazione non prosegue se l'app esce dallo schermo o il telefono si blocca.
        model.wimHof.stop()
        super.onStop()
    }
}
