# Discover — Impara, cresci, ispirati

App Android (Kotlin + Jetpack Compose) che trasforma il tempo passato sullo smartphone in tempo
che arricchisce: video, idee, libri, benessere e notizie positive, sempre in italiano e sempre
sotto il controllo dell'utente. Obiettivo: costo zero, nessuna API a pagamento.

## Stato del progetto

| Fase | Contenuto | Stato |
|---|---|---|
| 1 | Identità grafica, navigazione, schermate principali | completata |
| 2 | Selezione e gestione degli interessi | **in corso** |
| 3 | Feed video con contenuti reali in italiano | da fare |
| 4 | Catalogo creator e ricerca | da fare |
| 5 | Notizie positive, libri e podcast | da fare |
| 6 | Meditazione, mindfulness e gratitudine | parziale (respirazione e diario già attivi) |
| 7 | Algoritmo di personalizzazione | da fare |
| 8 | Assistente AI | da fare |
| 9 | Ottimizzazione, debug e APK | da fare |

## Cosa funziona davvero (fasi 1 e 2)

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

## Contenuti di esempio

Dove il contenuto reale non è ancora collegato, l'interfaccia mostra l'etichetta **ANTEPRIMA** o
**ESEMPIO**. Notizie, video, libri e autori **non** sono inventati: sono solo segnaposto di layout.
Il pensiero del giorno e la domanda di riflessione sono testi di esempio scritti per questo progetto.

## Privacy

Impostazioni e diario restano sul dispositivo (SharedPreferences). L'app non richiede permessi
e non invia dati a nessun server.

## Build

L'APK di debug viene compilato da GitHub Actions a ogni push (`.github/workflows/build-apk.yml`):
scaricalo dall'artifact `discover-debug-apk` della corsa del workflow.

In locale servono JDK 17 e l'Android SDK:

```
./gradlew assembleDebug
```

## Design

Palette: Dark `#141413`, Light `#FAF9F5`, Orange `#D97757`, Blue `#6A9BCC`, Green `#788C5D`.
Font: Poppins (titoli) e Lora (testi), inclusi nell'app con le rispettive licenze OFL.
