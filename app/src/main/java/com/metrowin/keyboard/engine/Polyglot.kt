package com.metrowin.keyboard.engine

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Several languages at once. The keyboard of a person who writes Italian and English does not have to be switched: the engines of the
 * active languages are asked together and the answers are weighed by which language the text is in right now.
 *
 * Which language that is comes from the words written so far (the last few dozen, the newest counting most): a word is more or less
 * likely in each language by its share of that language's word counts, so one Italian word among English ones changes little, and a
 * line of Italian turns the suggestions Italian even if the keys are English. The language of the keyboard has a small head start.
 * A word that any of the languages knows is never "corrected"; a typo is fixed to the likeliest word of the likeliest language.
 */
class Polyglot(private val engines: List<LanguageEngine>, private val home: Int = 0) {
    private val locale: Locale = engines.getOrNull(home)?.layout?.locale ?: Locale.getDefault()

    /** How likely each language is for the text with these [recent] words (oldest first); the numbers add up to 1. */
    fun weights(recent: List<String>): DoubleArray {
        val n = engines.size
        if (n <= 1) return DoubleArray(n) { 1.0 }
        val scores = DoubleArray(n)
        recent.takeLast(WINDOW).forEachIndexed { k, word ->
            val age = recent.takeLast(WINDOW).size - 1 - k
            val weight = Math.pow(DECAY, age.toDouble())
            for (i in 0 until n) {
                val e = engines[i]
                val count = e.dictionary.frequency(word) + e.user.count(word) * USER_COUNT
                scores[i] += weight * ln((count + 0.5) / (e.dictionary.totalFrequency + 1.0))
            }
        }
        val best = scores.max()
        val z = DoubleArray(n) { i -> exp((scores[i] - best) * SHARP) * (if (i == home) HOME_HEAD_START else 1.0) }
        val sum = z.sum()
        val w = DoubleArray(n) { max(z[it] / sum, FLOOR) }
        val total = w.sum()
        return DoubleArray(n) { w[it] / total }
    }

    /** The language the text is most likely in. */
    fun likely(recent: List<String>): Int = weights(recent).withIndex().maxByOrNull { it.value }?.index ?: home

    fun forTyped(typed: String, previous: String?, before: String?, after: String?, o: SuggestOptions, touch: List<Touch?>?, recent: List<String>): WordSuggestions {
        if (engines.size == 1) return engines[0].suggester.forTyped(typed, previous, o, touch, before, after)
        val w = weights(recent)
        val rs = engines.map { it.suggester.forTyped(typed, previous, o, touch, before, after) }
        val known = rs.any { it.typedIsKnown }
        val merged = LinkedHashMap<String, Double>()
        rs.forEachIndexed { i, r -> val lw = ln(w[i]); for ((word, s) in r.scored) merged.merge(word, s + lw) { a, b -> max(a, b) } }
        val lower = typed.lowercase(locale)
        val ordered = merged.entries.sortedByDescending { it.value }.map { it.key }.filter { it != lower }
        // The automatic fix: the best of the fixes the languages found, if no language knows the word and no other reading is as likely.
        var auto: String? = null
        var autoWord: String? = null
        if (!known && o.autocorrect) {
            var bestScore = Double.NEGATIVE_INFINITY
            rs.forEachIndexed { i, r ->
                val s = r.autoScore ?: return@forEachIndexed
                val a = r.autocorrect ?: return@forEachIndexed
                if (s + ln(w[i]) > bestScore) { bestScore = s + ln(w[i]); auto = a }
            }
            val chosen = auto
            if (chosen != null) {
                val raw = chosen.lowercase(locale)
                autoWord = raw
                val rival = ordered.firstOrNull { it != raw }?.let { merged[it] }
                val own = merged[raw]
                if (rs.any { it.ambiguous && it.autocorrect != null } || (rival != null && own != null && own - rival < TIE)) auto = null
            }
        }
        val list = ArrayList<String>()
        auto?.let { list += it }
        for (word in ordered) { val shown = Text.matchCase(typed, word, locale); if (shown != auto && list.size < 6) list += shown }
        return WordSuggestions(typed, list, auto, known, ordered.take(12).map { it to (merged[it] ?: 0.0) }, null, false)
    }

    /** The likely next words over all the languages, the likelier languages first. */
    fun nextWords(previous: String?, before: String?, limit: Int, o: SuggestOptions, recent: List<String>): List<String> {
        if (engines.size == 1) return engines[0].suggester.nextWords(previous, before, limit, o)
        val w = weights(recent)
        val mix = HashMap<String, Double>()
        engines.forEachIndexed { i, e ->
            val own = e.suggester.nextWords(previous, before, limit * 2, o)
            own.forEachIndexed { rank, word -> mix.merge(word, (own.size - rank).toDouble() * w[i]) { a, b -> a + b } }
        }
        return mix.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
    }

    fun phrase(previous: String?, o: SuggestOptions, recent: List<String>): String? = engines.getOrNull(likely(recent))?.suggester?.phrase(previous, o)

    /** The engine a typed word is learned by: one that knows it, the likelier language first; else the likeliest language. */
    fun learnTarget(word: String, recent: List<String>): LanguageEngine? {
        if (engines.isEmpty()) return null
        val w = weights(recent)
        val byLikelihood = engines.indices.sortedByDescending { w[it] }
        return engines[byLikelihood.firstOrNull { engines[it].dictionary.contains(word) || engines[it].user.count(word) > 0 } ?: byLikelihood.first()]
    }

    companion object {
        private const val WINDOW = 28              // how many of the last words say which language it is
        private const val DECAY = 0.94             // an older word counts a little less than a newer one
        private const val SHARP = 0.45             // how sure the weights are of themselves
        private const val HOME_HEAD_START = 1.6    // the language of the keys is a little likelier from the start
        private const val FLOOR = 0.04             // no language is ever ruled out
        private const val USER_COUNT = 60          // a word the user wrote before counts as if it were frequent
        private const val TIE = 0.7

        /** The words of a text, lower case, newest last: what [weights] reads. Punctuation does not end the run, a language goes on across sentences. */
        fun recentWords(text: CharSequence, max: Int = WINDOW): List<String> =
            Regex("[\\p{L}]+(?:'[\\p{L}]+)?").findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList().takeLast(max)
    }
}
