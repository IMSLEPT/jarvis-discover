package it.jarvis.discover.data.wellbeing

/** Un passo di una sessione guidata: un testo da leggere/ascoltare e poi qualche secondo di silenzio. */
data class Step(val text: String, val pauseSec: Int)

enum class SessionCategory(val label: String, val emoji: String) {
    Meditation("Meditazione", "🧘"),
    Mindfulness("Mindfulness", "🌿"),
    Evening("Sera e sonno", "🌙"),
    Emotions("Emozioni", "💛"),
    Stress("Stress", "🍃"),
}

data class Session(
    val id: String,
    val title: String,
    val category: SessionCategory,
    val about: String,
    val steps: List<Step>,
    /** Avvertenza da mostrare prima di iniziare (ad esempio "non usarla in strada"). */
    val caution: String? = null,
) {
    /** Durata stimata: tempo di lettura/ascolto di ogni passo più i silenzi. */
    val durationSec: Int get() = steps.sumOf { Reading.seconds(it.text) + it.pauseSec }
    val minutes: Int get() = ((durationSec + 30) / 60).coerceAtLeast(1)
}

object Reading {
    /** Quanto dura leggere o dire un testo con calma (circa due parole al secondo). */
    fun seconds(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return (words / 2.2).toInt().coerceAtLeast(2)
    }
}

/**
 * Sessioni guidate: testi originali scritti per Discover. Sono esercizi di attenzione e rilassamento per il
 * benessere quotidiano e non sostituiscono cure mediche o psicologiche.
 */
object Sessions {

    private fun s(text: String, pause: Int) = Step(text, pause)

    private val bodyParts = listOf(
        "i piedi", "le gambe", "il bacino", "la pancia", "la schiena", "il petto",
        "le mani", "le braccia", "le spalle", "il collo", "il viso", "la testa",
    )
    private val bodyPhrases = listOf(
        "Porta l'attenzione a %s. Nota ciò che senti, senza cercare di cambiarlo.",
        "Ora %s. Può esserci calore, peso, formicolio, oppure niente: va bene tutto.",
        "Sposta l'attenzione su %s. Se trovi tensione, prova a lasciarla andare un poco.",
    )

    private val tenseParts = listOf(
        "le mani, stringendo i pugni", "le braccia, avvicinando i gomiti al corpo", "le spalle, sollevandole verso le orecchie",
        "il viso, arricciando delicatamente fronte e occhi", "la pancia, rientrandola appena",
        "le gambe, spingendo i talloni verso il basso", "i piedi, tirando le dita verso di te",
    )

    val all: List<Session> = listOf(
        Session(
            id = "respiro_ancora",
            title = "Il respiro come àncora",
            category = SessionCategory.Meditation,
            about = "Una meditazione di base: usare il respiro per tornare al presente ogni volta che la mente si distrae.",
            steps = listOf(
                s("Mettiti comodo. Siediti con la schiena dritta ma non rigida, oppure sdraiati. Se vuoi, chiudi gli occhi.", 6),
                s("Fai un respiro profondo e lascialo andare con un lungo sospiro.", 8),
                s("Ora lascia che il respiro torni al suo ritmo naturale. Non devi cambiarlo.", 10),
                s("Porta l'attenzione al punto in cui senti di più il respiro: le narici, il petto o la pancia.", 15),
                s("Segui un respiro intero: l'aria che entra, una piccola pausa, l'aria che esce.", 20),
                s("La mente si distrarrà: è normale. Quando te ne accorgi, nota dove eri andato e torna con gentilezza al respiro.", 20),
                s("Ogni volta che torni al respiro stai esercitando l'attenzione. Non esiste un modo sbagliato di farlo.", 25),
                s("Continua così. Se ti aiuta, conta mentalmente: uno all'inspirazione, due all'espirazione, fino a dieci, poi ricomincia.", 40),
                s("Resta ancora qualche respiro qui, senza fare nulla.", 40),
                s("Allarga l'attenzione ai suoni intorno a te e al contatto del corpo con la sedia o con il letto.", 15),
                s("Muovi lentamente le dita delle mani e dei piedi.", 8),
                s("Quando sei pronto, apri gli occhi. Porta con te un po' di questa calma nel resto della giornata.", 5),
            ),
        ),
        Session(
            id = "scansione_corpo",
            title = "Scansione del corpo",
            category = SessionCategory.Meditation,
            about = "Percorri il corpo con l'attenzione, dai piedi alla testa, per notare le sensazioni e sciogliere la tensione.",
            steps = buildList {
                add(s("Sdraiati o siediti comodamente. Lascia che il corpo sia sostenuto da ciò su cui poggia.", 8))
                add(s("Fai due respiri lenti. Con ogni espirazione lascia andare un poco di tensione.", 12))
                bodyParts.forEachIndexed { i, part -> add(s(bodyPhrases[i % bodyPhrases.size].format(part), 14)) }
                add(s("Ora senti il corpo nel suo insieme, come un'unica presenza che respira.", 20))
                add(s("Resta qui ancora qualche respiro. Quando vuoi, muovi piano le mani e i piedi e riapri gli occhi.", 8))
            },
        ),
        Session(
            id = "gentilezza",
            title = "Gentilezza verso di sé",
            category = SessionCategory.Meditation,
            about = "Un breve esercizio di auto-compassione: trattarti come tratteresti una persona a cui vuoi bene.",
            steps = listOf(
                s("Siediti comodo e fai qualche respiro lento.", 10),
                s("Pensa a una persona a cui vuoi bene. Immagina il suo volto e nota come ti senti pensando a lei.", 15),
                s("Ripeti dentro di te: che tu possa stare bene, che tu possa essere sereno.", 20),
                s("Ora porta la stessa attenzione a te stesso. Ripeti: che io possa stare bene, che io possa trattarmi con gentilezza.", 25),
                s("Pensa a qualcosa che in questi giorni ti pesa. Non devi risolverlo adesso.", 15),
                s("Riconosci che è difficile, e che avere difficoltà fa parte dell'essere umani. Non sei l'unico a provarlo.", 20),
                s("Se vuoi, posa una mano sul petto e senti il calore del contatto. Dì a te stesso: sto facendo del mio meglio.", 25),
                s("Fai un ultimo respiro lento e riporta l'attenzione alla stanza.", 6),
            ),
        ),
        Session(
            id = "cinque_sensi",
            title = "Qui e ora: i cinque sensi",
            category = SessionCategory.Mindfulness,
            about = "Un esercizio rapido per tornare al presente usando i sensi, utile quando la mente corre.",
            steps = listOf(
                s("Fermati un momento. Appoggia bene i piedi a terra e fai un respiro lento.", 6),
                s("Guardati intorno e nomina dentro di te cinque cose che vedi, senza fretta.", 18),
                s("Ora quattro cose che puoi toccare o sentire sul corpo: i vestiti, la sedia, l'aria sulla pelle.", 18),
                s("Tre suoni che riesci a sentire, vicini o lontani.", 15),
                s("Due odori. Se non ne senti, ricorda un profumo che ti piace.", 12),
                s("Un sapore che hai in bocca, oppure l'ultimo che ricordi.", 10),
                s("Un ultimo respiro lento. Sei qui, in questo momento.", 5),
            ),
        ),
        Session(
            id = "pausa_minuto",
            title = "Una pausa di un minuto",
            category = SessionCategory.Mindfulness,
            about = "Un momento di consapevolezza da fare ovunque, tra un impegno e l'altro.",
            steps = listOf(
                s("Fermati. Appoggia i piedi a terra e lascia le mani ferme.", 4),
                s("Inspira lentamente dal naso.", 4),
                s("Espira lentamente, un po' più a lungo.", 6),
                s("Ancora un respiro: entra l'aria, esce l'aria.", 8),
                s("Rilassa le spalle e la mandibola.", 8),
                s("Ora torna a ciò che stavi facendo, con un po' più di calma.", 3),
            ),
        ),
        Session(
            id = "camminata",
            title = "Camminata consapevole",
            category = SessionCategory.Mindfulness,
            about = "Camminare lentamente prestando attenzione ai passi, in un luogo tranquillo.",
            caution = "Falla in un luogo sicuro e tranquillo (una stanza, un cortile, un giardino), non in strada con il traffico. Se usi le cuffie, tieni il volume basso.",
            steps = listOf(
                s("Scegli un tratto di pochi metri in cui camminare avanti e indietro. Parti da fermo, in piedi.", 8),
                s("Senti il peso del corpo sui piedi. Fai un respiro lento.", 8),
                s("Inizia a camminare molto lentamente. Nota il tallone che si appoggia, poi la pianta, poi le dita.", 25),
                s("Senti il peso che passa da un piede all'altro. Lascia che le braccia siano morbide.", 25),
                s("Quando la mente si distrae, torna con gentilezza alla sensazione dei passi.", 30),
                s("Arrivato alla fine del tratto, fermati, respira e girati lentamente. Poi riparti.", 40),
                s("Continua ancora per un poco, senza meta e senza fretta.", 60),
                s("Fermati. Fai un respiro profondo e senti il corpo che ha camminato.", 5),
            ),
        ),
        Session(
            id = "stress_sciogli",
            title = "Sciogli la tensione",
            category = SessionCategory.Stress,
            about = "Rilassamento muscolare progressivo: contrarre e rilasciare i gruppi di muscoli per sentire la differenza.",
            caution = "Contrai solo con leggerezza e mai fino al dolore. Se una zona del corpo ti fa male, salta quel passaggio.",
            steps = buildList {
                add(s("Siediti o sdraiati comodo. Fai due respiri lenti.", 8))
                tenseParts.forEach { part ->
                    add(s("Contrai leggermente $part. Mantieni per cinque secondi.", 5))
                    add(s("Ora rilascia tutto e senti la differenza tra tensione e rilassamento.", 12))
                }
                add(s("Fai un respiro profondo e senti il corpo più morbido e pesante.", 15))
                add(s("Resta qui qualche respiro. Quando vuoi, riapri gli occhi.", 6))
            },
        ),
        Session(
            id = "emozioni_nome",
            title = "Dare un nome a ciò che senti",
            category = SessionCategory.Emotions,
            about = "Riconoscere un'emozione, localizzarla nel corpo e scegliere una piccola azione gentile.",
            steps = listOf(
                s("Fermati un attimo e chiediti: che cosa sto provando adesso?", 10),
                s("Prova a dare un nome: rabbia, tristezza, paura, gioia, stanchezza, preoccupazione. Non c'è un nome giusto.", 15),
                s("Dove la senti nel corpo? Nel petto, nella gola, nello stomaco, nelle spalle?", 15),
                s("Da uno a dieci, quanto è intensa? Notalo senza giudicare.", 10),
                s("Prova a dirti: in questo momento sto provando questa emozione. Un'emozione è come un'onda: sale, raggiunge il culmine e poi passa.", 15),
                s("Fai tre respiri lenti, con l'espirazione più lunga dell'inspirazione.", 25),
                s("Chiediti: di cosa avrei bisogno adesso? Riposo, parlare con qualcuno, muovermi un po', una pausa?", 20),
                s("Scegli una piccola azione gentile per il prossimo quarto d'ora.", 10),
                s("Se un'emozione è molto intensa o dura da tempo, parlarne con una persona di fiducia o con un professionista è una scelta saggia.", 5),
            ),
        ),
        Session(
            id = "sera",
            title = "Riflessione serale",
            category = SessionCategory.Evening,
            about = "Chiudere la giornata con qualche domanda, per lasciarla andare con più serenità.",
            steps = listOf(
                s("Siediti o sdraiati. Fai un respiro lento e lascia andare le spalle.", 8),
                s("Come ti senti adesso, in questo momento?", 20),
                s("Qual è stato un momento bello di oggi, anche piccolo?", 25),
                s("C'è qualcosa che hai imparato oggi?", 20),
                s("C'è qualcosa che ti è pesato? Riconoscilo, senza giudicarti.", 25),
                s("Di cosa sei grato stasera?", 25),
                s("Qual è l'unica cosa che vorresti fare domani?", 20),
                s("Lascia andare la giornata. Hai fatto quello che potevi.", 6),
            ),
        ),
        Session(
            id = "sonno",
            title = "Prepararsi a dormire",
            category = SessionCategory.Evening,
            about = "Un rilassamento lento prima di dormire. Non promette di farti addormentare: aiuta a rallentare.",
            steps = listOf(
                s("Sdraiati comodo. Se puoi, abbassa le luci e tieni il telefono lontano dal viso.", 10),
                s("Fai un respiro lento: inspira contando fino a quattro, espira contando fino a sei.", 20),
                s("Ripeti ancora, senza sforzo, per qualche respiro.", 30),
                s("Senti il peso del corpo sul materasso. Lascia che sia il materasso a sostenerti.", 20),
                s("Rilassa i piedi, le gambe, il bacino. Ogni espirazione è un po' di tensione che se ne va.", 25),
                s("Rilassa la pancia, il petto, le spalle, le mani.", 25),
                s("Rilassa il collo, la mandibola, gli occhi e la fronte.", 25),
                s("Non cercare di addormentarti: lascia che accada. Se arrivano pensieri, lasciali passare come nuvole.", 40),
                s("Resta con il respiro, lento e morbido. Anche solo riposare è già utile.", 40),
            ),
        ),
    )

    fun byId(id: String): Session? = all.firstOrNull { it.id == id }
}
