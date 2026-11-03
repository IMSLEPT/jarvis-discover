package it.jarvis.discover.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6FDA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EAFE),
    onPrimaryContainer = Color(0xFF05264F),
    secondary = Palette.OrangeText,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8D0),
    onSecondaryContainer = Color(0xFF5A2A00),
    tertiary = Color(0xFF123C84),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE5F6),
    onTertiaryContainer = Color(0xFF041937),
    background = Palette.Cloud,
    onBackground = Palette.Navy,
    surface = Palette.Cloud,
    onSurface = Palette.Navy,
    surfaceVariant = Color(0xFFE6ECF6),
    onSurfaceVariant = Color(0xFF4B5A78),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF4F7FC),
    surfaceContainerHigh = Color(0xFFEAF0F9),
    surfaceContainerHighest = Color(0xFFE0E8F4),
    outline = Color(0xFF7C8AA6),
    outlineVariant = Color(0xFFDDE5F1),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF59ADFF),
    onPrimary = Palette.Navy,
    primaryContainer = Color(0xFF0E3A78),
    onPrimaryContainer = Color(0xFFD7EAFE),
    secondary = Color(0xFFFF9A2E),
    onSecondary = Color(0xFF2A1400),
    secondaryContainer = Color(0xFF5A2E06),
    onSecondaryContainer = Color(0xFFFFE8D0),
    tertiary = Color(0xFF9DC3FF),
    onTertiary = Palette.Navy,
    tertiaryContainer = Color(0xFF173568),
    onTertiaryContainer = Color(0xFFDCE5F6),
    background = Color(0xFF050E20),
    onBackground = Color(0xFFF1F5FC),
    surface = Color(0xFF050E20),
    onSurface = Color(0xFFF1F5FC),
    surfaceVariant = Color(0xFF1B2A4A),
    onSurfaceVariant = Color(0xFFA9B6D0),
    surfaceContainerLowest = Color(0xFF030A18),
    surfaceContainerLow = Color(0xFF0B1730),
    surfaceContainer = Color(0xFF0F1D3B),
    surfaceContainerHigh = Color(0xFF16264B),
    surfaceContainerHighest = Color(0xFF1D3058),
    outline = Color(0xFF6E7FA3),
    outlineVariant = Color(0xFF22345C),
    error = Color(0xFFF2B8B5),
)

/** Angoli molto morbidi, come lo squircle dell'icona. */
private val DiscoverShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Elementi grafici di marca, in comune a tutte le schermate. */
object Brand {
    /** Da blu ad arancione, come l'anello dell'icona. */
    val gradient: Brush = Brush.horizontalGradient(listOf(Palette.Blue, Palette.Orange))
    val gradientDiagonal: Brush = Brush.linearGradient(listOf(Palette.Blue, Palette.Orange))
}

@Composable
fun DiscoverTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DiscoverTypography,
        shapes = DiscoverShapes,
        content = content,
    )
}
