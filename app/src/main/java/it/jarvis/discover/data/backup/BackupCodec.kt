package it.jarvis.discover.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Formato del file di backup: intestazione in chiaro (magic, versione, iterazioni, sale), poi IV e
 * testo cifrato con AES-256-GCM. La chiave si ricava dalla passphrase con PBKDF2-HMAC-SHA256;
 * l'intestazione è autenticata (AAD), quindi non si può alterare senza far fallire la decifratura.
 * Senza passphrase il file è illeggibile: non esiste modo di recuperarla.
 */
object BackupCodec {

    class WrongPassphraseException : Exception("Passphrase errata o file danneggiato")
    class InvalidFileException : Exception("Il file non è un backup di Discover")

    const val MIN_PASSPHRASE = 8
    const val DEFAULT_ITERATIONS = 210_000
    private val MAGIC = "DSCBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 5 + 1 + 4 + SALT_LEN
    private const val MIN_ITER = 1_000
    private const val MAX_ITER = 2_000_000

    fun encrypt(
        plain: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + iv + cipher.doFinal(plain)
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        if (file.size < HEADER_LEN + IV_LEN + TAG_BITS / 8) throw InvalidFileException()
        val buf = ByteBuffer.wrap(file)
        val magic = ByteArray(MAGIC.size).also(buf::get)
        if (!magic.contentEquals(MAGIC)) throw InvalidFileException()
        if (buf.get() != VERSION) throw InvalidFileException()
        val iterations = buf.getInt()
        if (iterations !in MIN_ITER..MAX_ITER) throw InvalidFileException()
        val salt = ByteArray(SALT_LEN).also(buf::get)
        val iv = ByteArray(IV_LEN).also(buf::get)
        val body = ByteArray(buf.remaining()).also(buf::get)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(file.copyOfRange(0, HEADER_LEN))
            return cipher.doFinal(body)
        } catch (e: GeneralSecurityException) {
            throw WrongPassphraseException()
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

/** Contenuto di un backup (prima della cifratura): preferenze dell'app e, se richiesto, le chiavi API. */
data class BackupContent(
    val createdAt: Long,
    val prefs: Map<String, Map<String, Any>>,
    val secrets: Map<String, String>,
)

/** Conversione tra il contenuto del backup e JSON, con i tipi delle SharedPreferences preservati. */
object BackupPayload {

    fun toJson(content: BackupContent): String {
        val prefs = JSONObject()
        content.prefs.forEach { (file, entries) ->
            val obj = JSONObject()
            entries.forEach { (key, value) -> encodeValue(value)?.let { obj.put(key, it) } }
            prefs.put(file, obj)
        }
        val secrets = JSONObject()
        content.secrets.forEach { (k, v) -> secrets.put(k, v) }
        return JSONObject()
            .put("app", "discover").put("v", 1).put("created", content.createdAt)
            .put("prefs", prefs).put("secrets", secrets)
            .toString()
    }

    fun fromJson(json: String): BackupContent {
        val root = try { JSONObject(json) } catch (e: Exception) { throw BackupCodec.InvalidFileException() }
        if (root.optString("app") != "discover") throw BackupCodec.InvalidFileException()
        val prefs = LinkedHashMap<String, Map<String, Any>>()
        root.optJSONObject("prefs")?.let { all ->
            for (file in all.keys()) {
                val entries = LinkedHashMap<String, Any>()
                val obj = all.getJSONObject(file)
                for (key in obj.keys()) decodeValue(obj.getJSONObject(key))?.let { entries[key] = it }
                prefs[file] = entries
            }
        }
        val secrets = LinkedHashMap<String, String>()
        root.optJSONObject("secrets")?.let { s -> for (k in s.keys()) secrets[k] = s.getString(k) }
        return BackupContent(root.optLong("created"), prefs, secrets)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun encodeValue(value: Any): JSONObject? = when (value) {
        is String -> JSONObject().put("t", "s").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "l").put("v", value)
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray((value as Set<String>).toList()))
        else -> null
    }

    internal fun decodeValue(obj: JSONObject): Any? = when (obj.optString("t")) {
        "s" -> obj.getString("v")
        "i" -> obj.getInt("v")
        "l" -> obj.getLong("v")
        "b" -> obj.getBoolean("v")
        "f" -> obj.getDouble("v").toFloat()
        "ss" -> obj.getJSONArray("v").let { arr -> (0 until arr.length()).mapTo(LinkedHashSet()) { arr.getString(it) } }
        else -> null
    }
}
