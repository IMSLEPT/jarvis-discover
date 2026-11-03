package it.jarvis.discover.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHtmlTest {

    @Test
    fun validIdProducesPlayerPage() {
        val html = PlayerHtml.build("dQw4w9WgXcQ", captions = false)!!
        assertTrue(html.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue(html.contains("https://www.youtube.com/iframe_api"))
        assertTrue(html.contains("origin: 'https://it.jarvis.discover'"))
        assertTrue(html.contains("cc_load_policy: 0"))
        assertTrue(html.contains("cc_lang_pref: 'it'"))
        assertTrue(html.contains("start: 0"))
    }

    @Test
    fun captionsAndStartAreApplied() {
        val html = PlayerHtml.build("dQw4w9WgXcQ", captions = true, startSeconds = 95)!!
        assertTrue(html.contains("cc_load_policy: 1"))
        assertTrue(html.contains("start: 95"))
        assertTrue(PlayerHtml.build("dQw4w9WgXcQ", captions = false, startSeconds = -5)!!.contains("start: 0"))
    }

    @Test
    fun malformedIdsNeverReachThePage() {
        assertNull(PlayerHtml.build("x'); alert(1);//", captions = false))
        assertNull(PlayerHtml.build("troppo_corto", captions = false))
        assertNull(PlayerHtml.build("", captions = false))
        assertFalse(PlayerHtml.isValidId("dQw4w9WgXc "))
        assertTrue(PlayerHtml.isValidId("a-B_c1D2e3F"))
    }
}
