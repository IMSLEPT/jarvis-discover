package it.jarvis.discover.data.youtube

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Archivio cifrato per i segreti (la chiave API di YouTube). Il testo è cifrato con AES-256-GCM
 * usando una chiave generata dentro l'Android Keystore: non è esportabile e non lascia il telefono.
 * Non esiste alcun ripiego in chiaro: se il cifrario non funziona, il salvataggio fallisce.
 */
class SecureStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    fun has(name: String): Boolean = prefs.contains(name)

    /** @throws java.security.GeneralSecurityException se il Keystore del telefono non riesce a cifrare. */
    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = b64(cipher.iv) + ":" + b64(encrypted)
        prefs.edit().putString(name, packed).commit()
    }

    /** Restituisce null se non c'è o se non è più decifrabile (in quel caso lo cancella). */
    fun get(name: String): String? {
        val raw = prefs.getString(name, null) ?: return null
        return try {
            val (iv, data) = raw.split(":", limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            prefs.edit().remove(name).apply()
            null
        }
    }

    fun remove(name: String) {
        prefs.edit().remove(name).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "discover_secure_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
