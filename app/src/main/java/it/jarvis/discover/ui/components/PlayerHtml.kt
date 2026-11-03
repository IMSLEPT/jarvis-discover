package it.jarvis.discover.ui.components

/** Pagina HTML con il player incorporato di YouTube (IFrame Player API). Senza dipendenze Android. */
object PlayerHtml {

    /** Origine usata come base della pagina: YouTube richiede un Referer che identifichi l'app (`https://<app id>`). */
    const val ORIGIN = "https://it.jarvis.discover"

    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun isValidId(id: String) = ID.matches(id)

    /**
     * @param captions se vero, chiede i sottotitoli e preferisce quelli italiani
     * @param startSeconds secondo da cui far partire il video
     * @return null se [videoId] non ha il formato di un id YouTube (così non finisce mai nel codice della pagina)
     */
    fun build(videoId: String, captions: Boolean, startSeconds: Int = 0): String? {
        if (!isValidId(videoId)) return null
        val cc = if (captions) 1 else 0
        val start = startSeconds.coerceAtLeast(0)
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
  #player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
</style>
</head>
<body>
<div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player;
  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      width: '100%',
      height: '100%',
      videoId: '$videoId',
      playerVars: {
        autoplay: 1, playsinline: 1, rel: 0, fs: 0, enablejsapi: 1,
        hl: 'it', cc_lang_pref: 'it', cc_load_policy: $cc, start: $start,
        origin: '$ORIGIN'
      },
      events: {
        onReady: function (e) { Android.onReady(); },
        onStateChange: function (e) { Android.onState(e.data); },
        onError: function (e) { Android.onError(e.data); }
      }
    });
  }
  function setRate(r) { if (player && player.setPlaybackRate) { player.setPlaybackRate(r); } }
  function pauseVideo() { if (player && player.pauseVideo) { player.pauseVideo(); } }
  function getTime() { return (player && player.getCurrentTime) ? player.getCurrentTime() : 0; }
</script>
</body>
</html>
""".trimIndent()
    }
}
