package com.lumen.keyboard.model

/** The functional category of a key. */
enum class KeyType {
    CHARACTER,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    SYMBOLS,
    LETTERS,
    EXTRA_SYMBOLS,
    EMOJI,
    LANGUAGE
}

/**
 * Definition of a single keyboard key.
 *
 * @param longPress alternate characters revealed by a long press (accents, numbers, etc.)
 * @param weight relative width compared to a standard 1f-wide key
 */
data class KeyDef(
    val type: KeyType,
    val label: String = "",
    val capsLabel: String = label.uppercase(),
    val longPress: List<String> = emptyList(),
    val weight: Float = 1f
)

data class KeyRow(val keys: List<KeyDef>)

data class KeyboardLayout(val rows: List<KeyRow>)
