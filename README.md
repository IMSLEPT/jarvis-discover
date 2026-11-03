# Discover — Impara, cresci, ispirati

App Android (Kotlin + Jetpack Compose) che trasforma il tempo passato sullo smartphone in tempo
che arricchisce: video, idee, libri, podcast, notizie costruttive e benessere, sempre in italiano e
sempre sotto il controllo dell'utente. Obiettivo: costo zero, nessuna API a pagamento.

> **Stato onesto:** tutto il codice compila e i test automatici passano in CI (GitHub Actions).
> Nessuna funzione è stata provata dal vivo su un telefono o con chiavi vere: lo sviluppo non ha accesso
> a un dispositivo né a un account. Vedi «Cosa è verificato e cosa no».

## Le sezioni

Oggi · Scopri · Video · Benessere · Raccolta, più Ricerca, Assistente e Impostazioni in alto.

| Fase | Contenuto | Stato |
|---|---|---|
| 1 | Identità grafica (stile dell'icona), navigazione, schermate | completata |
| 2 | Interessi (7 macro-categorie, argomenti propri) | completata |
| 3 | Feed video verticale con video reali di YouTube in italiano | completata nel codice; richiede la tua chiave |
| 4 | Creator: ricerca universale, @handle, pagina creator, segui con livelli, notifiche facoltative | completata nel codice |
| 5 | Notizie (RSS), libri (Open Library), podcast (Apple Podcasts), audiolibri (LibriVox), lettore audio | completata nel codice |
| 6 | Sessioni guidate con voce italiana, respirazione, rumori rilassanti, gratitudine, statistiche | completata nel codice |
| 7 | Classifica trasparente dei video e profilo azzerabile | completata nel codice |
| 8 | Assistente: regole in italiano su fonti reali + IA Gemini opzionale | completata nel codice |
| 9 | Test, build ottimizzata (R8), APK in Releases | completata |

Extra della specifica realizzati: Quaderno (note e «Ho imparato», riepilogo settimanale), cronologia,
statistiche personali senza obiettivi né serie, backup cifrato. **Non realizzati:** raccolte/playlist
personalizzate, modalità senza distrazioni, backend aggiornabile (i contenuti si aggiornano da soli
perché vengono letti dal vivo dalle fonti, senza un backend proprio).

## Cosa serve (credenziali, permessi, servizi esterni)

| Cosa | Per quale funzione | Obbligatorio? |
|---|---|---|
| Chiave API YouTube Data v3 (gratuita) | feed Video, ricerca, creator, assistente (video) | per tutto ciò che riguarda i video |
| Chiave API Gemini da Google AI Studio (livello gratuito) | domande libere, «spiega questo video», suggerimenti pratici | no: l'assistente funziona anche senza |
| Permesso notifiche (Android 13+) | avviso di nuovi video dei creator seguiti | no, e le notifiche sono per creator |
| Servizio in primo piano «riproduzione multimediale» | audio di podcast e audiolibri a schermo spento | automatico |
| Voce italiana di sintesi vocale (Google/altro) | voce delle sessioni guidate | no: senza voce si leggono i testi |
| Internet | tutto il contenuto reale | sì |

Le chiavi si inseriscono in Impostazioni, restano sul telefono cifrate (AES-256-GCM, chiave nell'Android
Keystore, non esportabile) e non passano da nessun server dello sviluppatore.

## Backup

Impostazioni › Backup: crea un file cifrato con una passphrase (PBKDF2-SHA256 con 210.000 iterazioni e
AES-256-GCM) che contiene preferenze, interessi, raccolta, profilo, quaderno, benessere e **le chiavi API**.
Si salva dove vuoi tu e si ripristina su un altro telefono. Chi perde la passphrase perde il backup: non esiste recupero.

## Video

- Quota gratuita ufficiale: 100 ricerche al giorno (`search.list`) più 10.000 unità per il resto,
  azzerate a mezzanotte californiana (circa le 9:00 in Italia). Il feed usa 4 ricerche per volta, conserva
  i risultati per 6 ore e mostra un contatore locale (una stima).
- I video dei creator seguiti si leggono dalla playlist dei caricamenti e non consumano ricerche.
- Si mostrano solo video pubblici, incorporabili, non in diretta, non bloccati in Italia, non «per
  bambini» e lunghi almeno 30 secondi.
- Lingua: «italiano» solo se l'autore lo dichiara; altrimenti un'euristica dà «probabile italiano». I video
  con audio straniero compaiono solo con una traccia di sottotitoli **e** l'impostazione attiva; YouTube non
  dice in che lingua siano e l'app non traduce nulla.
- Il player è l'IFrame ufficiale di YouTube (nessun download né riutilizzo dei contenuti).

## Classifica trasparente (fase 7)

Ogni video riceve punti da regole semplici e visibili, mostrate sotto al video come «Perché lo vedi»:
interesse scelto, argomenti e creator che hai già apprezzato o scartato, creator seguiti, durata abituale,
freschezza, segnali di qualità (apprezzamenti/visualizzazioni, sottotitoli, lingua certa), meno punti per titoli
da clickbait e per i video già visti; poi diversità (niente file di video sullo stesso tema) e, a scelta tua
(nessuna/poche/medie/molte), proposte di argomenti nuovi segnalate come tali.
**Limite:** l'app non può verificare se un video dice il vero; la «qualità» è solo un indizio.
Il profilo (Raccolta › Profilo) si corregge voce per voce, si può spegnere (l'app smette di imparare) e azzerare.

## Assistente (fase 8)

Capisce le richieste con regole sulla lingua italiana e cerca **solo contenuti reali**: YouTube, Open Library,
LibriVox, Apple Podcasts e i feed RSS dell'app. Non inventa titoli, canali o link. Per le richieste di una persona
(«video di X») avvisa se nessun risultato sembra davvero suo.
Con una chiave Gemini e il tuo consenso esplicito può anche: rispondere a domande libere, spiegare un video
(solo da titolo e descrizione: non ha la trascrizione, e lo dice) e dare suggerimenti pratici. Le risposte
dell'IA sono sempre marcate e possono contenere errori.
**Privacy dell'IA:** con il livello gratuito Google può usare ciò che invii per migliorare i suoi prodotti e
revisori umani possono leggerlo (condizioni Gemini API). L'app invia solo il testo della richiesta, le ultime
battute della conversazione e gli interessi scelti — mai cronologia, diario o chiavi — e non salva le conversazioni.
Il modello si sceglie da `models.list` tra quelli indicati nella documentazione di Google; si può cambiare.

## Contenuti scritti per l'app

Pensiero del giorno, domande di riflessione, idee, consigli sulle relazioni e testi delle sessioni guidate sono scritti
per Discover e sono etichettati come tali («Scritto per Discover»). Non sono citazioni di persone reali.
Le sessioni di benessere non sono assistenza sanitaria; l'allenamento Wim Hof (solo respirazione, nessun esercizio con il freddo) è una versione prudente dello schema descritto dal sito ufficiale: controllo di sicurezza obbligatorio prima di ogni sessione, livello "Dolce" con meno respiri e apnea al massimo 30 secondi, livello "Standard" sbloccato solo dopo aver completato il Dolce, tetto alla durata dell'apnea, arresto automatico se l'app esce dallo schermo. Il ritmo dei respiri e i tetti sono scelte di Discover, non del metodo originale; non è una cura e non sostituisce il parere del medico. Fonti: wimhofmethod.com (esercizi di respirazione e avvertenze), Live Science (aprile 2024), Healthline (revisione medica 2021). La logica è provata con test automatici; non è stata provata su un telefono.

## Cosa è verificato e cosa no

**Verificato in CI:** compilazione, 150+ test unitari su: parser YouTube e feed, query, creator, backup cifrato
(round-trip, passphrase errata, file manomesso), RSS, Open Library, podcast, audiolibri, sessioni e profilo benessere,
classifica e profilo, parser delle richieste e client Gemini (con rete simulata), quaderno.

**Non verificato dal vivo (nessun dispositivo, nessuna chiave):** aspetto reale dell'interfaccia, risposte vere di
YouTube/Open Library/iTunes/archive.org/Gemini, player YouTube (rischio di errori di incorporamento), voce italiana
di sintesi, rumori generati, servizio audio in primo piano, notifiche, comportamento della build ottimizzata (R8)
su telefono. L'APK di prova (`discover-debug.apk`) è fornito come ripiego.

## Build

L'APK viene compilato da GitHub Actions a ogni push (`.github/workflows/build-apk.yml`) e pubblicato in
**Releases** (tag `latest`): `discover.apk` è la versione ottimizzata, `discover-debug.apk` quella di prova. Hanno la
stessa firma e si installano l'una sopra l'altra senza perdere i dati. **La chiave di firma è pubblica e non è di produzione.**
Il workflow esegue prima i test (`./gradlew testDebugUnitTest`). In locale servono JDK 17 e l'Android SDK:

```
./gradlew testDebugUnitTest assembleDebug
```

## Privacy

Impostazioni, diario, quaderno, profilo, cronologia, video salvati e preferenze restano sul dispositivo. Le richieste
di rete vanno solo a: YouTube (API e player), le testate RSS elencate, Open Library, Apple Podcasts (iTunes Search),
Internet Archive, e Google (Gemini) soltanto se attivi l'IA.

## Design

Stile ricavato dall'icona: anello sfumato blu → arancio, bussola a quattro punte, scia arancio, testo blu notte,
sfondo chiaro con aloni azzurro e pesca, caratteri Poppins (licenza OFL).
