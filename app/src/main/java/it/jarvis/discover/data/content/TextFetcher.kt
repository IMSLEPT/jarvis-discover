package it.jarvis.discover.data.content

import it.jarvis.discover.data.youtube.HttpFetcher
import it.jarvis.discover.data.youtube.HttpResponse
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Scarica pagine di testo (RSS, JSON) con limiti di dimensione e redirect gestiti a mano,
 * anche da http a https. Si identifica con un nome generico, senza dati personali.
 */
object TextFetcher : HttpFetcher {
    private const val MAX_BYTES = 3 * 1024 * 1024
    private const val MAX_REDIRECTS = 4
    private const val USER_AGENT = "DiscoverApp/0.5 (Android; app personale)"

    @Throws(IOException::class)
    override fun get(url: String): HttpResponse {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/rss+xml, application/xml, application/json, text/xml, */*")
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: throw IOException("redirect senza destinazione")
                    current = URL(URL(current), next).toString()
                    if (!current.startsWith("https://")) throw IOException("redirect non sicuro")
                    return@repeat
                }
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.use { readLimited(it) }.orEmpty()
                return HttpResponse(code, body)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("troppi redirect")
    }

    private fun readLimited(input: java.io.InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) throw IOException("risposta troppo grande")
        }
        return out.toString("UTF-8")
    }
}
