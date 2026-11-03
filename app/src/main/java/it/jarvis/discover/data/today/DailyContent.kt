package it.jarvis.discover.data.today

import java.time.LocalDate

/**
 * Pensieri, domande, spunti e idee scritti apposta per Discover: sono testi originali dell'app, non citazioni
 * di persone reali e non notizie. La schermata "Oggi" li ruota giorno per giorno, così ogni giorno ne mostra uno diverso.
 */
object DailyContent {

    val thoughts = listOf(
        "Non serve sentirti pronto per iniziare: la chiarezza arriva mentre fai il primo passo.",
        "Un piccolo progresso ripetuto ogni giorno pesa più di un grande sforzo fatto una volta sola.",
        "Imparare qualcosa di nuovo è il modo più economico di cambiare prospettiva.",
        "Rallentare non è perdere tempo: a volte è l'unico modo per vedere dove stai andando.",
        "Ciò che ascolti oggi diventa il modo in cui pensi domani: scegli con cura le tue fonti.",
        "Fare una domanda migliore vale spesso più che trovare una risposta più in fretta.",
        "La curiosità è come un muscolo: si rafforza ogni volta che la usi.",
        "Non devi cambiare tutto. Basta cambiare una cosa, oggi, e ripeterla.",
        "Una giornata difficile non cancella le abitudini che stai costruendo.",
        "Ascoltare davvero qualcuno è già un modo di dargli valore.",
        "Il tempo che dedichi a imparare è un regalo che fai a te stesso di domani.",
        "Non confrontare il tuo inizio con la metà del percorso di qualcun altro.",
        "Cinque minuti di attenzione piena contano più di un'ora distratta.",
        "Dare un nome a ciò che senti è il primo passo per capirlo.",
    )

    val questions = listOf(
        "Quale piccola abitudine, ripetuta ogni giorno, cambierebbe di più la tua vita tra un anno?",
        "Cosa vorresti aver imparato cinque anni fa, e cosa puoi iniziare a imparare oggi?",
        "Di cosa sei grato questa settimana, anche se è piccolo?",
        "Qual è una cosa che rimandi da tempo e che richiede solo dieci minuti per cominciare?",
        "Quando ti sei sentito più curioso, ultimamente? Cosa lo ha provocato?",
        "Chi ti ha insegnato qualcosa di importante senza volerlo?",
        "Se oggi potessi togliere una cosa dalla tua lista, quale ti libererebbe di più?",
        "Cosa vuoi che resti di questa giornata tra una settimana?",
        "In quale situazione reagisci in modo automatico e vorresti scegliere con più calma?",
        "Cosa ti fa perdere la nozione del tempo, in senso buono?",
        "Quale idea in cui credi da anni non hai mai messo davvero alla prova?",
        "Di quale persona vorresti prenderti più cura nei prossimi giorni?",
        "Che cosa significa, per te, \"abbastanza\"?",
        "Cosa insegneresti a una persona a cui tieni, se avessi solo cinque minuti?",
    )

    val relationTips = listOf(
        "Quando parli con qualcuno, prova per qualche minuto a posare il telefono: è un gesto che si nota.",
        "Prima di rispondere a una critica, ripeti con parole tue ciò che hai capito. Spesso basta a evitare un litigio.",
        "Un ringraziamento preciso (\"grazie per aver…\") vale più di uno generico.",
        "Chiedi \"com'è andata davvero?\" invece di \"tutto bene?\": cambia il tipo di risposta che ricevi.",
        "Con i bambini, descrivere ciò che vedi (\"ti vedo arrabbiato\") li aiuta a dare un nome alle emozioni.",
        "Scegli un momento fisso della giornata, anche breve, da dedicare a una persona senza distrazioni.",
        "Un \"scusa\" senza \"ma\" è più credibile.",
        "Ascolta senza cercare subito una soluzione: a volte la persona vuole solo essere ascoltata.",
        "Fai oggi un complimento sincero su qualcosa che di solito dai per scontato.",
        "Quando sei stanco, dillo: \"ho bisogno di dieci minuti\" evita parole di cui pentirsi.",
        "Chiedi aiuto in modo concreto e preciso: è più facile ricevere un sì.",
        "Condividere un ricordo insieme rafforza il legame più di tanti progetti futuri.",
        "Non dare per scontato ciò che l'altro sa o sente: chiedere è un atto di attenzione.",
        "Una breve telefonata a qualcuno che non senti da tempo vale più di un \"dobbiamo sentirci\".",
    )

    val ideas = listOf(
        "Scrivi per cinque minuti, senza correggere, tutto ciò che hai in testa. Poi rileggi e sottolinea una sola frase utile.",
        "Prova la regola dei due minuti: se un compito richiede meno di due minuti, fallo subito.",
        "Impara oggi una parola nuova, anche in un'altra lingua, e usala in tre frasi.",
        "Fai una passeggiata di dieci minuti senza cuffie e osserva tre cose che di solito non noti.",
        "Spiega ad alta voce, come a un amico, qualcosa che hai imparato di recente: se ti riesce, l'hai capito.",
        "Scegli un oggetto di casa che non usi più e decidi cosa farne entro stasera.",
        "Prepara oggi la lista delle tre cose più importanti di domani.",
        "Silenzia le notifiche non essenziali per un'ora e nota cosa cambia.",
        "Disegna a mano la mappa di un tuo progetto: bastano un foglio e cinque minuti.",
        "Cucina una ricetta nuova usando solo ciò che hai già in casa.",
        "Scrivi a qualcuno che ti ha ispirato e digli perché.",
        "Dedica un quarto d'ora a un argomento che non c'entra niente con il tuo lavoro.",
        "Riordina soltanto un cassetto o una cartella del telefono: i piccoli ordini danno energia.",
        "Registra una nota vocale con un'idea che ti gira in testa e riascoltala domani.",
    )

    /** Giorno corrente come numero progressivo (cambia a mezzanotte, ora locale). */
    fun today(): Long = LocalDate.now().toEpochDay()

    /**
     * Voce del giorno: stessa per tutto il giorno, diversa il giorno dopo. [salt] sfasa le liste tra loro
     * perché pensiero, domanda e idea non avanzino sempre in coppia.
     */
    fun <T> pick(list: List<T>, day: Long, salt: Int = 0): T {
        require(list.isNotEmpty())
        val i = Math.floorMod(day + salt * 5L, list.size.toLong()).toInt()
        return list[i]
    }
}
