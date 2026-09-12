package com.lumen.keyboard

/**
 * A small, fully offline word list used for simple prefix-based suggestions.
 * This is intentionally lightweight -- swap in a real frequency-ranked
 * dictionary file here for production-grade predictions.
 */
object WordDictionary {

    val words = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "it", "for", "not", "on", "with",
        "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up", "out", "if",
        "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time", "no", "just", "him",
        "know", "take", "people", "into", "year", "your", "good", "some", "could", "them", "see", "other", "than",
        "then", "now", "look", "only", "come", "its", "over", "think", "also", "back", "after", "use", "two", "how",
        "our", "work", "first", "well", "way", "even", "new", "want", "because", "any", "these", "give", "day", "most",
        "us", "love", "great", "thank", "thanks", "please", "hello", "hey", "today", "tomorrow", "later", "sure", "okay",
        "yes", "no", "maybe", "really", "actually", "definitely", "probably", "meeting", "project", "email", "phone",
        "message", "call", "tonight", "morning", "afternoon", "evening", "weekend", "sorry", "awesome", "sounds"
    )

    private val wordSet: Set<String> by lazy { words.toHashSet() }

    fun contains(word: String): Boolean = wordSet.contains(word.lowercase())

    fun suggestionsFor(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()
        return words
            .filter { it.startsWith(lower) && it != lower }
            .sortedBy { it.length }
            .take(limit)
    }
}
