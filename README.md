# Discover — Impara, cresci, ispirati

App Android (Kotlin + Jetpack Compose) che trasforma il tempo passato sullo smartphone in tempo
che arricchisce: video, idee, libri, benessere e notizie positive, sempre in italiano e sempre
sotto il controllo dell'utente. Obiettivo: costo zero, nessuna API a pagamento.

## Stato del progetto

| Fase | Contenuto | Stato |
|---|---|---|
| 1 | Identità grafica, navigazione, schermate principali | completata |
| 2 | Selezione e gestione degli interessi | completata |
| 3 | Feed video con contenuti reali in italiano | **in corso** (codice scritto, test automatici superati in CI; mai provato con una chiave vera) |
| 4 | Catalogo creator e ricerca | da fare |
| 5 | Notizie positive, libri e podcast | da fare |
| 6 | Meditazione, mindfulness e gratitudine | parziale (respirazione e diario già attivi) |
| 7 | Algoritmo di personalizzazione | da fare |
| 8 | Assistente AI | da fare |
| 9 | Ottimizzazione, debug e APK | da fare |

## Cosa funziona davvero (fasi 1, 2 e 3)

- Primo avvio: scelta degli interessi (7 macro-categorie, tutti i sottoargomenti del documento di
  progetto), con ricerca e possibilità di aggiungere argomenti propri. Nessun limite di numero.
- Gli interessi si modificano sempre da Scopri o da La mia raccolta › Interessi e restano sul telefono.
- Schermata d'apertura "Di cosa hai bisogno oggi?" con scelta multipla facoltativa.
- Navigazione a cinque sezioni: Oggi, Scopri, Video, Benessere, Raccolta.
- Oggi: saluto, scelta del tempo (2/5/10/20 min o libero) che adatta il numero di sezioni mostrate.
- Scopri: le sette macro-categorie di interessi con tutti i sottoargomenti del documento di progetto.
- Benessere: respirazione lenta guidata (4 s inspira, 6 s espira) e diario della gratitudine.
- La mia raccolta: il diario della gratitudine salvato sul telefono.
- Impostazioni: tema chiaro/scuro/sistema e preferenza sui video stranieri sottotitolati.
- Video (fase 3): feed verticale di video **veri** di YouTube, in italiano, costruito su modalità e
  interessi scelti. Player incorporato (velocità, sottotitoli, apri su YouTube), mi piace, salva
  (compare in La mia raccolta › Video), «Più così», «Non mi interessa» (con annulla), dettagli e condivisione.
- La chiave API gratuita di YouTube si inserisce da Impostazioni › Video da YouTube (con guida passo passo).

## Contenuti di esempio

Dove il contenuto reale non è ancora collegato, l'interfaccia mostra l'etichetta **ANTEPRIMA** o
**ESEMPIO**. Notizie, video, libri e autori **non** sono inventati: sono solo segnaposto di layout.
Il pensiero del giorno e la domanda di riflessione sono testi di esempio scritti per questo progetto.

## Video: come funziona e quali sono i limiti

- Serve una chiave API gratuita di YouTube Data API v3, inserita dall'utente. Viene cifrata
  (AES-256-GCM, chiave nell'Android Keystore), esclusa dai backup e usata solo per interrogare YouTube.
- Quota gratuita ufficiale: 100 ricerche al giorno (`search.list`) più 10.000 unità per il resto,
  azzerate a mezzanotte (fuso della California). Il feed usa 4 ricerche per volta, conserva i risultati
  per 6 ore e mostra un contatore locale (una stima: non conosce altri usi della stessa chiave).
- Si mostrano solo video pubblici, incorporabili, non in diretta, non bloccati in Italia, non «per
  bambini» e lunghi almeno 30 secondi.
- Lingua: l'audio è «italiano» solo se l'autore lo dichiara. Senza dichiarazione, un'euristica su
  titolo e descrizione dà «probabile italiano». I video con audio straniero compaiono solo se hanno
  una traccia di sottotitoli **e** l'impostazione è attiva; YouTube non dice in che lingua siano.
- Il player (WebView + IFrame API) usa come origine `https://it.jarvis.discover`, richiesta da YouTube.
- Non testato dal vivo: rete reale, player e quota sono stati provati solo con test su risposte
  di esempio (`app/src/test`), perché lo sviluppo non ha accesso a una chiave né a un telefono.
- Il riassunto dei video non c'è: arriverà con l'assistente (fase 8), senza testi inventati.

## Privacy

Impostazioni, diario, video salvati e preferenze restano sul dispositivo (SharedPreferences).
L'unico permesso è INTERNET, usato solo per parlare con YouTube (API e player) e per le miniature
(`i.ytimg.com`). La chiave API non lascia il telefono se non per interrogare YouTube.

## Build

L'APK di debug viene compilato da GitHub Actions a ogni push (`.github/workflows/build-apk.yml`):
scaricalo dall'artifact `discover-debug-apk` della corsa del workflow.

Il workflow esegue prima i test (`./gradlew testDebugUnitTest`), poi compila. Gli APK di debug sono
firmati sempre con la stessa chiave pubblica (`app/debug.keystore`), così ogni nuova versione si
installa sopra la precedente senza perdere dati. **Non è una chiave di produzione.**

In locale servono JDK 17 e l'Android SDK:

```
./gradlew testDebugUnitTest assembleDebug
```

## Design

Palette: Dark `#141413`, Light `#FAF9F5`, Orange `#D97757`, Blue `#6A9BCC`, Green `#788C5D`.
Font: Poppins (titoli) e Lora (testi), inclusi nell'app con le rispettive licenze OFL.
