package it.jarvis.discover.data.assistant

import it.jarvis.discover.data.youtube.HttpResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Rete per le chiamate con corpo (POST): separata per poterla simulare nei test. */
fun interface HttpRequester {
    @Throws(IOException::class)
    fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse
}

object UrlConnectionRequester : HttpRequester {
    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.requestMethod = method
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}

enum class GeminiErrorKind { InvalidKey, NoAccess, ModelNotFound, Quota, Blocked, Network, Other }

/** Errore di Gemini già classificato. Non contiene mai la chiave API. */
data class GeminiError(val kind: GeminiErrorKind, val httpCode: Int? = null, val detail: String? = null) {
    val userMessage: String
        get() = when (kind) {
            GeminiErrorKind.InvalidKey -> "Gemini non riconosce la chiave. Controlla di averla copiata per intero."
            GeminiErrorKind.NoAccess -> "La chiave non ha accesso a Gemini (o il servizio non è disponibile nel tuo Paese). Controlla il progetto su Google AI Studio."
            GeminiErrorKind.ModelNotFound -> "Il modello scelto non è più disponibile. Riprova: ne sceglierò un altro."
            GeminiErrorKind.Quota -> "Hai raggiunto il limite gratuito di richieste a Gemini (al minuto o al giorno). Riprova tra un po'."
            GeminiErrorKind.Blocked -> "Il servizio IA ha rifiutato di rispondere a questa richiesta."
            GeminiErrorKind.Network -> "Non riesco a collegarmi a Gemini. Controlla la connessione e riprova."
            GeminiErrorKind.Other -> "Gemini ha risposto con un errore${httpCode?.let { " ($it)" } ?: ""}${detail?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}."
        }
}

sealed interface GeminiResult<out T> {
    data class Ok<T>(val value: T) : GeminiResult<T>
    data class Err(val error: GeminiError) : GeminiResult<Nothing>
}

/** Un turno della conversazione mandato a Gemini. */
data class Turn(val fromUser: Boolean, val text: String)

/**
 * Client minimo per l'API Gemini (generateContent), con la chiave nell'intestazione `x-goog-api-key`.
 * Nulla qui è legato ad Android; la chiave viene passata a ogni chiamata e non è mai conservata.
 */
class GeminiClient(private val http: HttpRequester = UrlConnectionRequester) {

    /** Modelli che sanno generare testo (nome base, es. «gemini-3.8-flash»). */
    fun listModels(apiKey: String): GeminiResult<List<String>> {
        val names = ArrayList<String>()
        var token: String? = null
        repeat(MAX_PAGES) {
            val url = "$BASE/models?pageSize=200" + (token?.let { "&pageToken=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
            val r = try {
                http.send("GET", url, headers(apiKey), null)
            } catch (e: IOException) {
                return GeminiResult.Err(GeminiError(GeminiErrorKind.Network))
            }
            if (r.code !in 200..299) return GeminiResult.Err(classify(r.code, r.body))
            val page = parseModels(r.body)
            names += page.first
            token = page.second
            if (token == null) return GeminiResult.Ok(names.distinct())
        }
        return GeminiResult.Ok(names.distinct())
    }

    /** Una risposta a [turns] (l'ultimo è la domanda). Restituisce il testo generato. */
    fun generate(apiKey: String, model: String, system: String, turns: List<Turn>, maxTokens: Int = 2048): GeminiResult<String> {
        val body = buildRequest(system, turns, maxTokens)
        val r = try {
            http.send("POST", "$BASE/models/$model:generateContent", headers(apiKey) + ("Content-Type" to "application/json"), body)
        } catch (e: IOException) {
            return GeminiResult.Err(GeminiError(GeminiErrorKind.Network))
        }
        if (r.code !in 200..299) return GeminiResult.Err(classify(r.code, r.body))
        return parseGenerate(r.body)
    }

    private fun headers(apiKey: String) = mapOf("x-goog-api-key" to apiKey, "Accept" to "application/json")

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_PAGES = 5

        /** Preferenze di modello, dal più capace al più leggero (quelle indicate nella documentazione di Google). */
        val PREFERRED = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

        private val EXCLUDED = listOf("preview", "live", "tts", "image", "audio", "exp", "robotics", "computer", "embedding", "thinking", "native", "vision", "latest")
        private val STABLE_FLASH = Regex("^gemini-(\\d+)(?:\\.(\\d+))?-flash(-lite)?$")

        fun buildRequest(system: String, turns: List<Turn>, maxTokens: Int): String {
            val contents = JSONArray()
            turns.forEach { t ->
                contents.put(
                    JSONObject().put("role", if (t.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", t.text))),
                )
            }
            return JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
                .toString()
        }

        /** Elenco dei modelli che supportano generateContent e pagina successiva (se c'è). */
        fun parseModels(json: String): Pair<List<String>, String?> = runCatching {
            val o = JSONObject(json)
            val arr = o.optJSONArray("models") ?: JSONArray()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val methods = m.optJSONArray("supportedGenerationMethods")
                val ok = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                if (!ok) continue
                val base = m.optString("baseModelId").ifBlank { m.optString("name").removePrefix("models/") }
                if (base.isNotBlank()) out += base
            }
            out to o.optString("nextPageToken").ifBlank { null }
        }.getOrDefault(emptyList<String>() to null)

        /**
         * Sceglie il modello: prima quelli preferiti, poi il Flash stabile più recente (senza anteprime, live, immagini…).
         * Null se non c'è nulla di adatto.
         */
        fun pickModel(available: List<String>): String? {
            PREFERRED.firstOrNull { it in available }?.let { return it }
            val stable = available.filter { id -> EXCLUDED.none { it in id } }.mapNotNull { id ->
                STABLE_FLASH.matchEntire(id)?.let { m ->
                    val major = m.groupValues[1].toInt()
                    val minor = m.groupValues[2].toIntOrNull() ?: 0
                    Triple(id, major * 100 + minor, m.groupValues[3].isEmpty())
                }
            }
            return stable.sortedWith(compareByDescending<Triple<String, Int, Boolean>> { it.second }.thenByDescending { it.third }).firstOrNull()?.first
        }

        /** Altri modelli utilizzabili, nell'ordine in cui provarli se il primo non va. */
        fun candidates(available: List<String>): List<String> {
            val preferred = PREFERRED.filter { it in available }
            val rest = available.filter { id ->
                id !in preferred && EXCLUDED.none { it in id } && STABLE_FLASH.matches(id)
            }.sortedDescending()
            return preferred + rest
        }

        fun parseGenerate(json: String): GeminiResult<String> = runCatching {
            val o = JSONObject(json)
            val candidates = o.optJSONArray("candidates")
            val first = candidates?.optJSONObject(0)
            val parts = first?.optJSONObject("content")?.optJSONArray("parts")
            val text = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                text.append(p.optString("text"))
            }
            val answer = text.toString().trim()
            when {
                answer.isNotEmpty() -> GeminiResult.Ok(answer)
                o.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty().isNotBlank() ||
                    first?.optString("finishReason") in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION") ->
                    GeminiResult.Err(GeminiError(GeminiErrorKind.Blocked))
                else -> GeminiResult.Err(GeminiError(GeminiErrorKind.Other, detail = "risposta vuota"))
            }
        }.getOrElse { GeminiResult.Err(GeminiError(GeminiErrorKind.Other, detail = "risposta non leggibile")) }

        fun classify(code: Int, body: String): GeminiError {
            val err = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
            val status = err?.optString("status").orEmpty()
            val message = err?.optString("message").orEmpty()
            val kind = when {
                code == 429 || status == "RESOURCE_EXHAUSTED" -> GeminiErrorKind.Quota
                code == 404 || status == "NOT_FOUND" -> GeminiErrorKind.ModelNotFound
                code == 401 || message.contains("API key not valid", ignoreCase = true) || body.contains("API_KEY_INVALID") -> GeminiErrorKind.InvalidKey
                code == 403 || status == "PERMISSION_DENIED" -> GeminiErrorKind.NoAccess
                code == 400 && status == "FAILED_PRECONDITION" -> GeminiErrorKind.NoAccess
                else -> GeminiErrorKind.Other
            }
            // Il messaggio di Google non contiene la chiave, ma lo accorciamo comunque.
            return GeminiError(kind, code, message.take(160).takeIf { kind == GeminiErrorKind.Other })
        }
    }
}
