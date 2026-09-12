package com.lumen.keyboard.model

/**
 * All built-in keyboard layouts. Adding a new layout (e.g. a second language)
 * only requires defining a new [KeyboardLayout] here and wiring it up in
 * LumenInputMethodService.
 */
object Layouts {

    private fun c(label: String, longPress: List<String> = emptyList()) =
        KeyDef(KeyType.CHARACTER, label = label, longPress = longPress)

    val qwerty = KeyboardLayout(
        listOf(
            KeyRow(
                listOf(
                    c("q", listOf("1")), c("w", listOf("2")), c("e", listOf("3", "è", "é", "ê", "ë")),
                    c("r", listOf("4")), c("t", listOf("5")), c("y", listOf("6")),
                    c("u", listOf("7", "ù", "ú", "û", "ü")), c("i", listOf("8", "ì", "í", "î", "ï")),
                    c("o", listOf("9", "ò", "ó", "ô", "ö", "õ")), c("p", listOf("0"))
                )
            ),
            KeyRow(
                listOf(
                    c("a", listOf("à", "á", "â", "ä", "ã", "å")), c("s", listOf("ß", "ś")), c("d"),
                    c("f"), c("g"), c("h"), c("j"), c("k"), c("l", listOf("ł"))
                )
            ),
            KeyRow(
                listOf(
                    KeyDef(KeyType.SHIFT, weight = 1.5f),
                    c("z"), c("x"), c("c", listOf("ç")), c("v"), c("b"), c("n", listOf("ñ")), c("m"),
                    KeyDef(KeyType.BACKSPACE, weight = 1.5f)
                )
            ),
            KeyRow(
                listOf(
                    KeyDef(KeyType.SYMBOLS, label = "?123", weight = 1.5f),
                    KeyDef(KeyType.EMOJI, weight = 1f),
                    KeyDef(KeyType.SPACE, label = " ", weight = 4f),
                    c(".", listOf(",", "!", "?")),
                    KeyDef(KeyType.ENTER, label = "enter", weight = 1.5f)
                )
            )
        )
    )

    private val numberRow = KeyRow(
        listOf(c("1"), c("2"), c("3"), c("4"), c("5"), c("6"), c("7"), c("8"), c("9"), c("0"))
    )

    /** Same as [qwerty] but with a permanent number row on top. */
    val qwertyWithNumberRow = KeyboardLayout(listOf(numberRow) + qwerty.rows)

    val symbols1 = KeyboardLayout(
        listOf(
            KeyRow(listOf(c("1"), c("2"), c("3"), c("4"), c("5"), c("6"), c("7"), c("8"), c("9"), c("0"))),
            KeyRow(listOf(c("@"), c("#"), c("$"), c("_"), c("&"), c("-"), c("+"), c("("), c(")"), c("/"))),
            KeyRow(
                listOf(
                    KeyDef(KeyType.EXTRA_SYMBOLS, label = "=\\<", weight = 1.5f),
                    c("*"), c("\""), c("'"), c(":"), c(";"), c("!"), c("?"),
                    KeyDef(KeyType.BACKSPACE, weight = 1.5f)
                )
            ),
            KeyRow(
                listOf(
                    KeyDef(KeyType.LETTERS, label = "ABC", weight = 1.5f),
                    KeyDef(KeyType.EMOJI, weight = 1f),
                    KeyDef(KeyType.SPACE, label = " ", weight = 4f),
                    c(".", listOf(",")),
                    KeyDef(KeyType.ENTER, label = "enter", weight = 1.5f)
                )
            )
        )
    )

    val symbols2 = KeyboardLayout(
        listOf(
            KeyRow(listOf(c("~"), c("`"), c("|"), c("·"), c("√"), c("π"), c("÷"), c("×"), c("¶"), c("∆"))),
            KeyRow(listOf(c("£"), c("¢"), c("€"), c("¥"), c("^"), c("°"), c("="), c("{"), c("}"), c("\\"))),
            KeyRow(
                listOf(
                    KeyDef(KeyType.EXTRA_SYMBOLS, label = "?123", weight = 1.5f),
                    c("%"), c("©"), c("®"), c("™"), c("["), c("]"), c("<"), c(">"),
                    KeyDef(KeyType.BACKSPACE, weight = 1.5f)
                )
            ),
            KeyRow(
                listOf(
                    KeyDef(KeyType.LETTERS, label = "ABC", weight = 1.5f),
                    KeyDef(KeyType.EMOJI, weight = 1f),
                    KeyDef(KeyType.SPACE, label = " ", weight = 4f),
                    c(".", listOf(",")),
                    KeyDef(KeyType.ENTER, label = "enter", weight = 1.5f)
                )
            )
        )
    )
}
