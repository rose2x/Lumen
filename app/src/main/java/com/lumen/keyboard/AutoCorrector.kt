package com.lumen.keyboard

/**
 * Simple, fully offline autocorrect: if a just-typed word isn't in the
 * dictionary, look for the closest dictionary word within a small edit
 * distance and suggest it as a correction.
 */
object AutoCorrector {

    /**
     * Returns a correction for [word], or null if [word] is already valid,
     * too short to bother correcting, or no close-enough match exists.
     */
    fun correct(word: String): String? {
        val lower = word.lowercase()
        if (lower.length < 3) return null
        if (WordDictionary.contains(lower)) return null

        val maxDistance = if (lower.length <= 4) 1 else 2
        var best: String? = null
        var bestDistance = Int.MAX_VALUE

        for (candidate in WordDictionary.words) {
            // Cheap length pre-filter before the more expensive edit-distance calc.
            if (kotlin.math.abs(candidate.length - lower.length) > maxDistance) continue
            val distance = LevenshteinUtil.distance(lower, candidate)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }

        return if (best != null && bestDistance <= maxDistance) best else null
    }
}
