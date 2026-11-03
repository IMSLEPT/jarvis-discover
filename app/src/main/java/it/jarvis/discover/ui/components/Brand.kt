package it.jarvis.discover.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.jarvis.discover.ui.theme.Palette

/*
 * Elementi grafici ricavati dall'icona: anello a gradiente blu→arancione, bussola a quattro facce,
 * ghirigoro arancione e sfondo con aloni azzurro e pesca. Tutti disegnati con Canvas: nessuna immagine.
 */

private val RingStops = arrayOf(
    0.00f to Color(0xFFFF7A00),
    0.07f to Color(0xFFE8602A),
    0.12f to Color(0xFFB0507E),
    0.17f to Color(0xFF5A63D8),
    0.21f to Color(0xFF3AA8FF),
    0.26f to Color(0xFF3BB1FD),
    0.35f to Color(0xFFB9E4FC),
    0.40f to Color(0xFF6CCBFE),
    0.50f to Color(0xFF2CB6FE),
    0.58f to Color(0xFF1E8CF0),
    0.65f to Color(0xFF2A70D8),
    0.73f to Color(0xFF7A6DA8),
    0.79f to Color(0xFFF07A3A),
    0.83f to Color(0xFFFF8A00),
    0.92f to Color(0xFFFF9302),
    1.00f to Color(0xFFFF7A00),
)

/** Anello a gradiente (senza bussola). [thickness] è la frazione del diametro. */
@Composable
fun BrandRing(modifier: Modifier = Modifier, size: Dp = 48.dp, thickness: Float = 0.085f) {
    Canvas(modifier.size(size)) { drawRing(thickness) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(thickness: Float) {
    val s = this.size.minDimension
    val w = s * thickness
    drawArc(
        brush = Brush.sweepGradient(colorStops = RingStops, center = Offset(s / 2f, s / 2f)),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(s - w, s - w),
        style = Stroke(width = w),
    )
}

/** Bussola dell'icona: anello a gradiente e ago a quattro facce. */
@Composable
fun CompassMark(modifier: Modifier = Modifier, size: Dp = 40.dp, withRing: Boolean = true) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        if (withRing) drawRing(0.085f)
        val c = Offset(s / 2f, s / 2f)
        fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
        // Coordinate dell'ago, in frazioni del diametro, misurate sull'icona.
        val tip = p(0.267f, -0.240f)
        val left = p(-0.150f, -0.033f)
        val mid = p(0.015f, 0.015f)
        val right = p(0.090f, 0.174f)
        val bottom = p(-0.318f, 0.357f)
        fun face(a: Offset, b: Offset, d: Offset, brush: Brush) {
            val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close() }
            drawPath(path, brush)
        }
        face(left, tip, mid, Brush.linearGradient(listOf(Palette.Amber, Color(0xFFFF9A1F)), left, tip))
        face(mid, tip, right, Brush.linearGradient(listOf(Palette.Flame, Color(0xFFFF7A00)), mid, right))
        face(left, mid, bottom, Brush.linearGradient(listOf(Color(0xFF00B7FF), Color(0xFF0A7FE8)), bottom, left))
        face(mid, right, bottom, Brush.linearGradient(listOf(Color(0xFF0A4FA8), Palette.NavyDeep), right, bottom))
    }
}

/** Ghirigoro arancione sotto il titolo: sottile alle estremità, più pieno al centro. */
@Composable
fun Swoosh(modifier: Modifier = Modifier, width: Dp = 120.dp, height: Dp = 12.dp) {
    Canvas(modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.95f)
            cubicTo(w * 0.25f, h * 0.15f, w * 0.7f, h * 0.0f, w, h * 0.55f)
            cubicTo(w * 0.68f, h * 0.32f, w * 0.30f, h * 0.45f, 0f, h * 0.95f)
            close()
        }
        drawPath(path, Brush.horizontalGradient(listOf(Palette.Orange, Color(0xFFFFB02E), Palette.Orange)))
    }
}

/** "Discover" con la bussola, come in cima alle schermate principali. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CompassMark(size = 30.dp)
        Spacer(Modifier.width(8.dp))
        Text("Discover", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Titolo di schermata con il ghirigoro arancione sotto. */
@Composable
fun BrandHeadline(text: String, modifier: Modifier = Modifier, swooshWidth: Dp = 96.dp) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Swoosh(width = swooshWidth, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Sfondo dell'app: bianco con alone azzurro in alto a sinistra e pesca in basso a destra (blu notte di sera). */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.background
    val dark = base.luminance() < 0.5f
    Box(
        modifier
            .background(base)
            .drawBehind {
                val blue = if (dark) Color(0xFF0B4DB0).copy(alpha = 0.40f) else Palette.SkyTint.copy(alpha = 0.95f)
                val warm = if (dark) Palette.Orange.copy(alpha = 0.13f) else Palette.Peach
                drawRect(Brush.radialGradient(listOf(blue, Color.Transparent), center = Offset(0f, 0f), radius = size.maxDimension * 0.75f))
                drawRect(Brush.radialGradient(listOf(warm, Color.Transparent), center = Offset(size.width, size.height), radius = size.maxDimension * 0.7f))
            },
    ) { content() }
}
