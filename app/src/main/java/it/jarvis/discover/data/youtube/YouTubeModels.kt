package it.jarvis.discover.data.youtube

/** Come l'app ha capito la lingua di un video (YouTube non la dichiara sempre). */
enum class LangClass {
    /** Audio dichiarato in italiano dall'autore. */
    Italian,

    /** Lingua non dichiarata, ma titolo/descrizione sembrano italiani: stima, non certezza. */
    ItalianGuess,

    /** Audio non italiano (o ignoto) ma con una traccia di sottotitoli: la lingua dei sottotitoli non è verificabile. */
    Subtitled,

    /** Audio non italiano e nessun sottotitolo: non viene mai mostrato. */
    Foreign,
}

/** Un video reale restituito da YouTube. */
data class VideoItem(
    val id: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val description: String,
    val publishedAt: String,
    val durationSec: Int,
    val thumbnail: String,
    val views: Long?,
    val likes: Long?,
    val lang: LangClass,
    val hasCaptions: Boolean,
    /** Argomento o modalità che ha fatto comparire il video (serve a spiegare "perché lo vedi"). */
    val topic: String?,
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id"
    val shareUrl: String get() = "https://youtu.be/$id"

    /** Vero se, con le impostazioni attuali, il video può comparire nel feed. */
    fun isShown(acceptSubtitled: Boolean): Boolean = when (lang) {
        LangClass.Italian, LangClass.ItalianGuess -> true
        LangClass.Subtitled -> acceptSubtitled
        LangClass.Foreign -> false
    }
}

/** Una ricerca da fare su YouTube, con l'argomento che la motiva. */
data class FeedQuery(val text: String, val topic: String)

enum class YtErrorKind { InvalidKey, ApiNotEnabled, KeyRestricted, QuotaExceeded, Network, Other }

/** Errore di YouTube o di rete, già classificato. Non contiene mai la chiave API. */
data class YtError(val kind: YtErrorKind, val httpCode: Int? = null, val detail: String? = null) {
    val userMessage: String
        get() = when (kind) {
            YtErrorKind.InvalidKey -> "YouTube non riconosce la chiave. Controlla di averla copiata per intero."
            YtErrorKind.ApiNotEnabled -> "La chiave è valida, ma nel tuo progetto Google manca l'abilitazione di «YouTube Data API v3»."
            YtErrorKind.KeyRestricted -> "La chiave ha restrizioni che bloccano l'app: consenti solo «YouTube Data API v3» e lascia «Nessuna» come restrizione di applicazione."
            YtErrorKind.QuotaExceeded -> "Hai finito la quota gratuita di oggi. Si rinnova a mezzanotte, ora della California (circa le 9:00 in Italia)."
            YtErrorKind.Network -> "Non riesco a collegarmi a YouTube. Controlla la connessione e riprova."
            YtErrorKind.Other -> "YouTube ha risposto con un errore${httpCode?.let { " ($it)" } ?: ""}${detail?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}."
        }

    /** Vero se il problema riguarda la chiave e si risolve nelle impostazioni. */
    val isKeyProblem: Boolean
        get() = kind == YtErrorKind.InvalidKey || kind == YtErrorKind.ApiNotEnabled || kind == YtErrorKind.KeyRestricted
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val error: YtError) : ApiResult<Nothing>
}

/** Filtri rapidi del feed. [durationHint] è il valore `videoDuration` di search.list usato per cercare altri video di quel tipo. */
enum class FeedFilter(val label: String, val durationHint: String?) {
    All("Tutti", null),
    Short("Brevi", "short"),
    Talks("Conferenze", "long");

    fun matches(item: VideoItem): Boolean = when (this) {
        All -> true
        Short -> item.durationSec <= SHORT_MAX_SEC
        Talks -> item.durationSec >= TALK_MIN_SEC
    }

    companion object {
        const val SHORT_MAX_SEC = 5 * 60
        const val TALK_MIN_SEC = 20 * 60
    }
}
