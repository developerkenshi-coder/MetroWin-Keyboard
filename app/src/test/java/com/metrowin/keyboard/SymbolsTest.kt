package com.metrowin.keyboard

import com.metrowin.keyboard.layout.KeyType
import com.metrowin.keyboard.layout.KeyboardBuilder
import com.metrowin.keyboard.layout.LanguageLayout
import com.metrowin.keyboard.layout.LocaleSymbols
import com.metrowin.keyboard.layout.Page
import org.junit.Assert.*
import org.junit.Test

class SymbolsTest {
    private fun layout(id: String, loc: String) = LanguageLayout.parse("id=$id\nname=$id\nlocale=$loc\nrow=q w e r t y u i o p\nrow=a s d f g h j k l\nrow=z x c v b n m\n", true)!!
    private fun labels(p: Page, l: LanguageLayout?) = KeyboardBuilder.symbols(p, false, l).keys.filter { it.type == KeyType.CHAR }.map { it.label }

    @Test fun theCurrencyOfTheLanguageIsOnTheFirstPage() {
        assertTrue("€" in labels(Page.SYMBOLS_1, layout("it", "it_IT")))
        assertTrue("$" in labels(Page.SYMBOLS_1, layout("en", "en_US")))
        assertTrue("£" in labels(Page.SYMBOLS_1, layout("en", "en_GB")))
        assertTrue("zł" in labels(Page.SYMBOLS_1, layout("pl", "pl_PL")))
        assertTrue("₺" in labels(Page.SYMBOLS_1, layout("tr", "tr_TR")))
        assertTrue("₽" in labels(Page.SYMBOLS_1, layout("ru", "ru_RU")))
    }

    @Test fun noCurrencyIsLostBetweenThePages() {
        for ((id, loc) in listOf("it" to "it_IT", "en" to "en_US", "en" to "en_GB", "tr" to "tr_TR", "pl" to "pl_PL")) {
            val l = layout(id, loc)
            val all = (listOf(Page.SYMBOLS_1, Page.SYMBOLS_2, Page.SYMBOLS_3)).flatMap { labels(it, l) }
            for (c in listOf("$", "€", "£", "¥")) assertTrue("$c on the $loc pages", c in all)
        }
    }

    @Test fun thePagesHoldTheSymbolsOfGboardAndNoneTwice() {
        val l = layout("en", "en_US")
        val p1 = labels(Page.SYMBOLS_1, l); val p2 = labels(Page.SYMBOLS_2, l)
        for (c in "@ # $ _ & - + ( ) / * \" ' : ; ! ?".split(' ')) assertTrue("$c on page 1", c in p1)
        for (c in "~ ` | • √ π ÷ × ¶ ∆ £ ¢ € ¥ ^ ° = { } \\ % © ® ™ ✓ [ ]".split(' ')) assertTrue("$c on page 2", c in p2)
        for (p in listOf(Page.SYMBOLS_1, Page.SYMBOLS_2, Page.SYMBOLS_3)) {
            val ls = labels(p, l).filter { it.length == 1 && !it[0].isDigit() }
            assertEquals("duplicates on $p: $ls", ls.size, ls.toSet().size)
        }
        assertTrue("the third page has what is left", "«" in labels(Page.SYMBOLS_3, l) && "½" in labels(Page.SYMBOLS_3, l))
    }

    @Test fun everyPageHasTheSameShapeAndTheToggleCycles() {
        val l = layout("it", "it_IT")
        val pages = listOf(Page.SYMBOLS_1, Page.SYMBOLS_2, Page.SYMBOLS_3).map { KeyboardBuilder.symbols(it, false, l) }
        for (p in pages) assertEquals(listOf(10, 10, 9, 6), p.rows.map { it.size })
        assertEquals(listOf("1/3", "2/3", "3/3"), pages.map { pg -> pg.keys.first { it.type == KeyType.SYMBOLS_PAGE }.label })
        assertEquals("¿", LocaleSymbols.quotesFirst(layout("es", "es_ES")).first())
    }

    @Test fun theEmojiKeyCanBeLeftOutAndTheSpaceBarTakesItsRoom() {
        val l = layout("it", "it_IT")
        fun bottom(emoji: Boolean) = KeyboardBuilder.letters(l, false, true, false, emoji).rows.last()
        assertTrue(bottom(true).any { it.type == KeyType.EMOJI })
        assertFalse(bottom(false).any { it.type == KeyType.EMOJI })
        assertEquals(bottom(true).first { it.type == KeyType.SPACE }.weight + 1f, bottom(false).first { it.type == KeyType.SPACE }.weight, 1e-6f)
        assertEquals(bottom(true).sumOf { it.weight.toDouble() }, bottom(false).sumOf { it.weight.toDouble() }, 1e-6)
    }
}
