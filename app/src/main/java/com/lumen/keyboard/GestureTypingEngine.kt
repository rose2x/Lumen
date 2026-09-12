package com.lumen.keyboard

/**
 * A lightweight, fully offline swipe-typing matcher.
 *
 * [KeyboardView] records the sequence of distinct letter keys the finger
 * passed over during a single continuous gesture (consecutive duplicates
 * already collapsed, e.g. dragging back and forth over "l" only counts once
 * per direction change). This engine scores dictionary words by how well
 * their letters fit, as an ordered subsequence, inside that path -- the
 * same core idea real swipe-typing engines use, just without a weighted
 * probability model or a key-distance/geometry score.
 */
object GestureTypingEngine {

    /** Returns candidate words for [path], best match first. */
    fun candidatesFor(path: List<Char>, limit: Int = 3): List<String> {
        if (path.size < 2) return emptyList()
        val pathStr = path.joinToString("")

        val scored = WordDictionary.words.mapNotNull { word ->
            val score = matchScore(word, pathStr)
            if (score >= 0) word to score else null
        }

        return scored
            .sortedWith(compareBy({ -it.second }, { it.first.length }))
            .map { it.first }
            .distinct()
            .take(limit)
    }

    /**
     * Returns a non-negative score if every letter of [word] appears, in
     * order, somewhere in [path] (an ordered-subsequence match, allowing
     * the finger to have passed over extra letters in between). Returns -1
     * if [word] cannot be formed from [path] at all. Higher score = better
     * match (closer in length to the swiped path, i.e. fewer "extra" keys
     * the finger passed over that weren't part of the word).
     */
    private fun matchScore(word: String, path: String): Int {
        if (word.isEmpty()) return -1
        // Must start and end near the start/end of the swipe for a sane match.
        if (word.first() != path.first()) return -1

        var pathIndex = 0
        for (ch in word) {
            var found = -1
            var i = pathIndex
            while (i < path.length) {
                if (path[i] == ch) { found = i; break }
                i++
            }
            if (found == -1) return -1
            pathIndex = found + 1
        }
        // Reward words whose length is close to the swiped path length,
        // and a bonus if the swipe also ends on the word's last letter.
        val lengthPenalty = kotlin.math.abs(path.length - word.length)
        val endsBonus = if (path.last() == word.last()) 2 else 0
        return 100 - lengthPenalty + endsBonus
    }
}
