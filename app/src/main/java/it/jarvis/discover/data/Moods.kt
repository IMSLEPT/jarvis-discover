package it.jarvis.discover.data

import androidx.compose.ui.graphics.Color
import it.jarvis.discover.ui.theme.Palette

/** Modalità tra cui scegliere all'apertura ("Di cosa hai bisogno oggi?"). */
data class Mood(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val gradient: List<Color>,
)

val Moods = listOf(
    Mood("motivami", "🔥", "Motivami", "Video energici, disciplina e storie di successo", listOf(Palette.OrangeDeep, Palette.OrangeLight)),
    Mood("insegnami", "🧠", "Insegnami qualcosa", "AI, scienza, psicologia, cultura e curiosità", listOf(Palette.BlueDeep, Palette.BlueLight)),
    Mood("riflettere", "🤔", "Fammi riflettere", "Filosofia, discorsi profondi e prospettive diverse", listOf(Palette.PlumDeep, Palette.PlumLight)),
    Mood("stare_bene", "❤️", "Fammi stare bene", "Notizie positive, empatia e storie incoraggianti", listOf(Palette.RoseDeep, Palette.RoseLight)),
    Mood("rilassami", "🧘", "Rilassami", "Mindfulness, respirazione, meditazione e gratitudine", listOf(Palette.GreenDeep, Palette.GreenLight)),
    Mood("idee", "💡", "Dammi nuove idee", "Progetti, creatività, possibilità e consigli", listOf(Palette.GoldDeep, Palette.GoldLight)),
    Mood("leggere", "📚", "Fammi leggere o ascoltare", "Libri, audiolibri, podcast e conferenze", listOf(Palette.TealDeep, Palette.TealLight)),
    Mood("sorprendimi", "✨", "Sorprendimi", "Contenuti inaspettati che potrebbero interessarti", listOf(Palette.BlueDeep, Palette.OrangeDeep)),
    Mood("per_te", "🏠", "Per te", "Il feed completo, basato su tutti i tuoi interessi", listOf(Palette.Ink, Palette.InkLight)),
)

fun moodById(id: String): Mood? = Moods.firstOrNull { it.id == id }
