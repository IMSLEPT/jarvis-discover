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
    Mood("motivami", "🔥", "Motivami", "Video energici, disciplina e storie di successo", listOf(Palette.OrangeDeep, Color(0xFFCB6440))),
    Mood("insegnami", "🧠", "Insegnami qualcosa", "AI, scienza, psicologia, cultura e curiosità", listOf(Palette.BlueDeep, Color(0xFF4F7FAE))),
    Mood("riflettere", "🤔", "Fammi riflettere", "Filosofia, discorsi profondi e prospettive diverse", listOf(Palette.PlumDeep, Color(0xFF7662A0))),
    Mood("stare_bene", "❤️", "Fammi stare bene", "Notizie positive, empatia e storie incoraggianti", listOf(Palette.RoseDeep, Color(0xFFC0657A))),
    Mood("rilassami", "🧘", "Rilassami", "Mindfulness, respirazione, meditazione e gratitudine", listOf(Palette.GreenDeep, Color(0xFF68804D))),
    Mood("idee", "💡", "Dammi nuove idee", "Progetti, creatività, possibilità e consigli", listOf(Palette.GoldDeep, Color(0xFFB08420))),
    Mood("leggere", "📚", "Fammi leggere o ascoltare", "Libri, audiolibri, podcast e conferenze", listOf(Palette.TealDeep, Color(0xFF4A8497))),
    Mood("sorprendimi", "✨", "Sorprendimi", "Contenuti inaspettati che potrebbero interessarti", listOf(Color(0xFF6A3F7C), Color(0xFF9566A8))),
    Mood("per_te", "🏠", "Per te", "Il feed completo, basato su tutti i tuoi interessi", listOf(Palette.Ink, Color(0xFF4A4843))),
)

fun moodById(id: String): Mood? = Moods.firstOrNull { it.id == id }
