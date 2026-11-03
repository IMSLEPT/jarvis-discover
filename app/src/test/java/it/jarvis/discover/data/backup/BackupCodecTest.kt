package it.jarvis.discover.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val pass = "frase-segreta-lunga".toCharArray()

    @Test
    fun roundTrip() {
        val plain = "contenuto «di prova» con accenti àèìòù".toByteArray()
        val file = BackupCodec.encrypt(plain, pass, iterations = 2_000)
        assertArrayEquals(plain, BackupCodec.decrypt(file, pass))
    }

    @Test
    fun differentEncryptionsDiffer() {
        val plain = "uguale".toByteArray()
        val a = BackupCodec.encrypt(plain, pass, iterations = 2_000)
        val b = BackupCodec.encrypt(plain, pass, iterations = 2_000)
        assertFalse(a.contentEquals(b)) // sale e IV casuali
    }

    @Test
    fun plaintextIsNotVisibleInFile() {
        val file = BackupCodec.encrypt("AIzaSegretissima1234567890".toByteArray(), pass, iterations = 2_000)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("AIza"))
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val file = BackupCodec.encrypt("x".toByteArray(), pass, iterations = 2_000)
        try {
            BackupCodec.decrypt(file, "un'altra-frase".toCharArray())
            fail("doveva fallire")
        } catch (e: BackupCodec.WrongPassphraseException) {
            // atteso
        }
    }

    @Test
    fun tamperedFileIsRejected() {
        val file = BackupCodec.encrypt("dati importanti".toByteArray(), pass, iterations = 2_000)
        val body = file.copyOf().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }
        val header = file.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() } // sale: cambia la chiave derivata
        for (bad in listOf(body, header)) {
            try {
                BackupCodec.decrypt(bad, pass)
                fail("doveva fallire")
            } catch (e: BackupCodec.WrongPassphraseException) {
                // atteso
            }
        }
    }

    @Test
    fun foreignFilesAreRejected() {
        for (bad in listOf(ByteArray(0), ByteArray(100), "non sono un backup, solo testo lungo abbastanza per superare i controlli".toByteArray())) {
            try {
                BackupCodec.decrypt(bad, pass)
                fail("doveva fallire")
            } catch (e: BackupCodec.InvalidFileException) {
                // atteso
            }
        }
    }

    @Test
    fun absurdIterationCountIsRejected() {
        val file = BackupCodec.encrypt("x".toByteArray(), pass, iterations = 2_000)
        file[8] = 0x7f // porta le iterazioni a un valore enorme (denial of service)
        try {
            BackupCodec.decrypt(file, pass)
            fail("doveva fallire")
        } catch (e: BackupCodec.InvalidFileException) {
            // atteso
        }
    }

    @Test
    fun payloadKeepsTypesOfPreferences() {
        val content = BackupContent(
            createdAt = 1_700_000_000_000,
            prefs = mapOf(
                "discover_prefs" to mapOf<String, Any>(
                    "theme_mode" to "Dark", "session_minutes" to 10, "accept_subtitled" to false,
                    "big" to 123456789012L, "ratio" to 0.5f, "tags" to setOf("a", "b"),
                ),
            ),
            secrets = mapOf("youtube_api_key" to "AIzaTEST"),
        )
        val back = BackupPayload.fromJson(BackupPayload.toJson(content))
        assertEquals(content.createdAt, back.createdAt)
        assertEquals(content.secrets, back.secrets)
        val p = back.prefs.getValue("discover_prefs")
        assertEquals("Dark", p["theme_mode"])
        assertEquals(10, p["session_minutes"])
        assertEquals(false, p["accept_subtitled"])
        assertEquals(123456789012L, p["big"])
        assertEquals(0.5f, p["ratio"])
        assertEquals(setOf("a", "b"), p["tags"])
    }

    @Test
    fun payloadOfOtherAppsIsRejected() {
        try {
            BackupPayload.fromJson("""{"app":"altro","prefs":{}}""")
            fail("doveva fallire")
        } catch (e: BackupCodec.InvalidFileException) {
            // atteso
        }
        try {
            BackupPayload.fromJson("non json")
            fail("doveva fallire")
        } catch (e: BackupCodec.InvalidFileException) {
            // atteso
        }
    }

    @Test
    fun minimumPassphraseLengthIsSensible() {
        assertTrue(BackupCodec.MIN_PASSPHRASE >= 8)
    }
}
