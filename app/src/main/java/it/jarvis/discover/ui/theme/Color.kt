package it.jarvis.discover.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette di Discover, ricavata dall'icona dell'app: blu elettrico, arancione acceso, blu notte per i testi,
 * su un fondo quasi bianco con aloni azzurro e pesca.
 */
object Palette {
    // Base dell'icona
    val Navy = Color(0xFF041937)       // testo e "Discover"
    val NavyDeep = Color(0xFF012F6F)   // faccia scura della bussola
    val Blue = Color(0xFF0B7DF0)       // blu dell'anello
    val Sky = Color(0xFF2CB6FE)        // azzurro dell'anello
    val SkyTint = Color(0xFFD2EAFD)    // alone azzurro dello sfondo
    val Orange = Color(0xFFFF8A00)     // arancione dell'anello e del ghirigoro
    val Flame = Color(0xFFFF4D01)      // faccia rossa della bussola
    val Amber = Color(0xFFFDC248)      // faccia gialla della bussola
    val Peach = Color(0xFFFAEFE9)      // alone pesca dello sfondo
    val Cloud = Color(0xFFFBFAFC)      // fondo

    // Nomi storici usati in tutta l'app
    val Dark = Navy
    val Cream = Cloud
    val Green = Color(0xFF0E9F8E)
    val Gray = Color(0xFF8A97B1)
    val Sand = Color(0xFFE3EAF5)

    // Varianti profonde, usate per i gradienti sotto testo bianco (contrasto leggibile)
    val OrangeDeep = Color(0xFFB93A08)
    val BlueDeep = Color(0xFF0B5FC4)
    val GreenDeep = Color(0xFF0B7A6E)
    val PlumDeep = Color(0xFF4B3FA3)
    val RoseDeep = Color(0xFFB3365A)
    val GoldDeep = Color(0xFFB35F00)
    val TealDeep = Color(0xFF0A6E94)
    val Ink = Color(0xFF0A2250)

    // Secondo colore dei gradienti (più chiaro, comunque leggibile sotto testo grande bianco)
    val OrangeLight = Color(0xFFE0580F)
    val BlueLight = Color(0xFF1E88F0)
    val GreenLight = Color(0xFF12A08E)
    val PlumLight = Color(0xFF6F5DD0)
    val RoseLight = Color(0xFFD65577)
    val GoldLight = Color(0xFFD98300)
    val TealLight = Color(0xFF1290BE)
    val InkLight = Color(0xFF1B4A9C)

    /** Arancione per il testo piccolo su fondo chiaro (4,7:1 su bianco). */
    val OrangeText = Color(0xFFB85400)
}
