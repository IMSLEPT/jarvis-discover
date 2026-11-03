package it.jarvis.discover.data

import androidx.compose.ui.graphics.Color
import it.jarvis.discover.ui.theme.Palette

/** Macro-categorie di interesse con i relativi sottoargomenti (dal documento di progetto). */
data class InterestCategory(
    val id: String,
    val emoji: String,
    val title: String,
    val accent: Color,
    val topics: List<String>,
)

val InterestCategories = listOf(
    InterestCategory(
        "psicologia", "🧠", "Psicologia e mente", Palette.PlumDeep,
        listOf(
            "Psicologia umana e comportamentale", "Neuroscienze", "Intelligenza emotiva",
            "Autostima e fiducia in sé", "Gestione delle emozioni", "Ansia, stress e resilienza",
            "Concentrazione e attenzione", "Bias cognitivi e pensiero critico",
            "Comunicazione e linguaggio del corpo", "Abitudini, motivazione e disciplina",
            "Felicità, consapevolezza e soddisfazione personale",
        ),
    ),
    InterestCategory(
        "crescita", "🚀", "Motivazione e crescita personale", Palette.OrangeDeep,
        listOf(
            "Discorsi motivazionali", "Superare le difficoltà", "Mentalità e disciplina",
            "Costanza e determinazione", "Raggiungere obiettivi", "Storie di successo",
            "Fallimenti e insegnamenti", "Cambiare vita e abitudini",
            "Sviluppare il proprio potenziale", "Migliorare il rapporto con il tempo",
        ),
    ),
    InterestCategory(
        "tecnologia", "🤖", "AI, tecnologia e futuro", Palette.BlueDeep,
        listOf(
            "Intelligenza artificiale", "ChatGPT, Gemini, Claude e altri strumenti",
            "Novità e innovazioni tecnologiche", "Automazione e produttività", "Futuro del lavoro",
            "Robotica", "Programmazione e creazione di app", "Strumenti digitali utili",
            "Innovazione scientifica", "Opportunità offerte dall'AI",
        ),
    ),
    InterestCategory(
        "relazioni", "❤️", "Relazioni, famiglia e genitorialità", Palette.RoseDeep,
        listOf(
            "Essere genitori consapevoli", "Educazione dei bambini",
            "Sviluppo psicologico e cognitivo dei figli", "Comunicazione genitore-figlio",
            "Gestione dei capricci e delle emozioni", "Attaccamento e relazioni affettive",
            "Educazione positiva basata su evidenze", "Vita di coppia", "Comunicazione efficace",
            "Gestione dei conflitti", "Empatia, amicizie e rapporti umani",
            "Come migliorare come partner e genitore",
        ),
    ),
    InterestCategory(
        "benessere", "🧘", "Benessere mentale e fisico", Palette.GreenDeep,
        listOf(
            "Meditazione guidata", "Mindfulness", "Gratitudine", "Respirazione consapevole",
            "Tecniche di rilassamento", "Gestione dello stress", "Sonno e recupero",
            "Attività fisica e salute", "Alimentazione e benessere", "Metodo Wim Hof",
            "Pratiche per migliorare la qualità della vita",
        ),
    ),
    InterestCategory(
        "cultura", "📚", "Cultura, filosofia e conoscenza", Palette.TealDeep,
        listOf(
            "Filosofia antica e moderna", "Stoicismo", "Grandi pensatori", "Scienza e curiosità",
            "Storia e biografie", "Economia spiegata semplicemente", "Educazione finanziaria",
            "Libri e letteratura", "Documentari", "TED Talks e conferenze",
            "Lezioni universitarie e divulgazione",
        ),
    ),
    InterestCategory(
        "creativita", "💡", "Creatività, idee e opportunità", Palette.GoldDeep,
        listOf(
            "Idee imprenditoriali", "Progetti fai-da-te", "Creatività", "Problem solving",
            "Nuove competenze", "Hobby interessanti", "Esperimenti scientifici", "Invenzioni",
            "Opportunità di apprendimento", "Idee per migliorare la quotidianità",
        ),
    ),
)
