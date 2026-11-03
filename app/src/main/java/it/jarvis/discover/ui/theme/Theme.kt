package it.jarvis.discover.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Palette.Orange,
    onPrimary = Palette.Dark,
    primaryContainer = Color(0xFFF7DFD3),
    onPrimaryContainer = Color(0xFF5A2A18),
    secondary = Palette.Blue,
    onSecondary = Palette.Dark,
    secondaryContainer = Color(0xFFDCE8F3),
    onSecondaryContainer = Color(0xFF1E3A52),
    tertiary = Palette.Green,
    onTertiary = Palette.Dark,
    tertiaryContainer = Color(0xFFE3EAD8),
    onTertiaryContainer = Color(0xFF2E3B1F),
    background = Palette.Cream,
    onBackground = Palette.Dark,
    surface = Palette.Cream,
    onSurface = Palette.Dark,
    surfaceVariant = Palette.Sand,
    onSurfaceVariant = Color(0xFF5E5D59),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F4EE),
    surfaceContainer = Color(0xFFF0EEE6),
    surfaceContainerHigh = Color(0xFFEAE8DE),
    surfaceContainerHighest = Color(0xFFE4E2D8),
    outline = Palette.Gray,
    outlineVariant = Palette.Sand,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Palette.Orange,
    onPrimary = Palette.Dark,
    primaryContainer = Color(0xFF4A2616),
    onPrimaryContainer = Color(0xFFF7DFD3),
    secondary = Palette.Blue,
    onSecondary = Palette.Dark,
    secondaryContainer = Color(0xFF24394D),
    onSecondaryContainer = Color(0xFFDCE8F3),
    tertiary = Color(0xFF8FA574),
    onTertiary = Palette.Dark,
    tertiaryContainer = Color(0xFF2E3B1F),
    onTertiaryContainer = Color(0xFFE3EAD8),
    background = Palette.Dark,
    onBackground = Palette.Cream,
    surface = Palette.Dark,
    onSurface = Palette.Cream,
    surfaceVariant = Color(0xFF2E2D2A),
    onSurfaceVariant = Palette.Gray,
    surfaceContainerLowest = Color(0xFF0F0F0E),
    surfaceContainerLow = Color(0xFF1B1B19),
    surfaceContainer = Color(0xFF20201E),
    surfaceContainerHigh = Color(0xFF2A2A27),
    surfaceContainerHighest = Color(0xFF343431),
    outline = Color(0xFF7A7872),
    outlineVariant = Color(0xFF3A3A36),
    error = Color(0xFFF2B8B5),
)

private val DiscoverShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DiscoverTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DiscoverTypography,
        shapes = DiscoverShapes,
        content = content,
    )
}
