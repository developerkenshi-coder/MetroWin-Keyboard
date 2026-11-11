package com.metrowin.keyboard

import com.metrowin.keyboard.engine.*
import com.metrowin.keyboard.layout.LanguageLayout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.util.Locale

class PolyglotTest {
    private fun layout(id: String, loc: String) = LanguageLayout.parse("id=$id\nname=$id\nlocale=$loc\nrow=q w e r t y u i o p\nrow=a s d f g h j k l\nrow=z x c v b n m\n", true)!!

    private fun engine(id: String, loc: String, locale: Locale, words: String, seed: NextWordSeed = NextWordSeed.EMPTY, user: UserModel = UserModel()): LanguageEngine {
        val l = layout(id, loc)
        return LanguageEngine(l, WordDictionary.parse(StringReader(words.trimIndent()).buffered(), locale), user, Blocklist(), File.createTempFile("user", ".txt"), seed)
    }

    private val it = engine("it", "it_IT", Locale.ITALIAN, """
        il 90000
        la 80000
        che 90000
        sono 70000
        e 95000
        gatto 4000
        mangia 6000
        pizza 5000
        casa 9000
        cosa 8000
        ciao 9000
        molto 7000
    """, NextWordSeed(mapOf("il" to listOf("gatto" to 900, "cane" to 400))))

    private val en = engine("en", "en_US", Locale.ENGLISH, """
        the 90000
        is 80000
        and 95000
        a 85000
        cat 4000
        eats 1500
        house 9000
        horse 2500
        world 8000
        hello 9000
        very 7000
    """, NextWordSeed(mapOf("the" to listOf("cat" to 900, "house" to 700))))

    private val opts = SuggestOptions()

    private fun words(s: String) = Polyglot.recentWords(s)

    @Test fun theLanguageOfTheTextIsRead() {
        val poly = Polyglot(listOf(it, en), home = 0)
        val italian = poly.weights(words("il gatto mangia la pizza e sono molto felice"))
        assertTrue("italian ${italian.toList()}", italian[0] > 0.9)
        val english = poly.weights(words("the cat eats a house and the world is very big"))
        assertTrue("english ${english.toList()}", english[1] > 0.9)
        // With nothing written the language of the keys leads, and none is ruled out.
        val none = poly.weights(emptyList())
        assertTrue(none[0] > none[1] && none[1] > 0.0)
        assertEquals(1.0, none.sum(), 1e-9)
    }

    @Test fun oneForeignWordDoesNotTurnTheTextAround() {
        val poly = Polyglot(listOf(it, en), home = 0)
        val w = poly.weights(words("il gatto mangia la pizza hello e sono molto"))
        assertTrue("${w.toList()}", w[0] > 0.8)
    }

    @Test fun aWordOfAnyActiveLanguageIsLeftAlone() {
        val poly = Polyglot(listOf(it, en), home = 0)
        val r = poly.forTyped("house", "il", null, null, opts, null, words("il gatto mangia la pizza e sono"))
        assertTrue(r.typedIsKnown); assertNull(r.autocorrect)
    }

    @Test fun aTypoIsFixedInTheLanguageOfTheText() {
        val poly = Polyglot(listOf(it, en), home = 0)
        val english = poly.forTyped("hous", "the", null, null, opts, null, words("the cat eats a house and the world is very"))
        assertEquals("house", english.autocorrect)
        val italian = poly.forTyped("pizzs", "la", null, null, opts, null, words("il gatto mangia la"))
        assertEquals("pizza", italian.autocorrect)
        // Alone, the Italian engine knows nothing of "worlf"; together with English it is fixed.
        assertNull(Polyglot(listOf(it), 0).forTyped("worlf", "the", null, null, opts, null, words("the cat eats the")).autocorrect)
        assertEquals("world", poly.forTyped("worlf", "the", null, null, opts, null, words("the cat eats the")).autocorrect)
    }

    @Test fun theNextWordsFollowTheLanguage() {
        val poly = Polyglot(listOf(it, en), home = 0)
        assertEquals("gatto", poly.nextWords("il", null, 3, opts, words("la pizza e il")).first())
        assertEquals("cat", poly.nextWords("the", null, 3, opts, words("eats a house and the")).first())
    }

    @Test fun aWordIsLearnedByItsOwnLanguage() {
        val poly = Polyglot(listOf(it, en), home = 0)
        assertSame(it, poly.learnTarget("gatto", words("the cat eats a house")))
        assertSame(en, poly.learnTarget("hello", words("il gatto mangia la pizza")))
        // An unknown word follows the language of the text.
        assertSame(en, poly.learnTarget("zzyx", words("the cat eats a house and the world is very")))
        assertSame(it, poly.learnTarget("zzyx", words("il gatto mangia la pizza e sono molto")))
    }

    @Test fun theWordsBeforeAndAfterWeighOnAFix() {
        val user = UserModel()
        repeat(3) { user.learn("una", "bella", "casa"); user.learn(null, "casa", "grande") }
        val e = engine("it", "it_IT", Locale.ITALIAN, "casa 5000\ncosa 5000\nuna 9000\nbella 9000\ngrande 9000", user = user)
        val s = e.suggester
        // "cesa" is as near to "casa" as to "cosa": what is known of the words around decides.
        assertEquals("casa", s.forTyped("cesa", "bella", opts, null, "una", null).autocorrect)
        assertEquals("casa", s.forTyped("cesa", null, opts, null, null, "grande").autocorrect)
    }

    @Test fun oneLanguageBehavesAsBefore() {
        val poly = Polyglot(listOf(en), 0)
        assertEquals("house", poly.forTyped("hous", "the", null, null, opts, null, emptyList()).autocorrect)
    }
}
