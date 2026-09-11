package com.lumen.keyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import com.lumen.keyboard.model.KeyDef
import com.lumen.keyboard.model.KeyType
import com.lumen.keyboard.model.Layouts

class LumenInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var prefs: PreferencesManager
    private lateinit var container: LinearLayout
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var keyboardView: KeyboardView
    private lateinit var emojiPanel: EmojiPanelView

    private var capsOn = false
    private var capsLocked = false
    private var lastShiftTapTime = 0L
    private var onSymbolsPage2 = false
    private var emojiVisible = false
    private val wordBuffer = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onCreateInputView(): View {
        container = LayoutInflater.from(this)
            .inflate(R.layout.view_keyboard_container, null) as LinearLayout

        suggestionBar = container.findViewById(R.id.suggestionBar)
        keyboardView = container.findViewById(R.id.keyboardView)
        emojiPanel = container.findViewById(R.id.emojiPanel)

        keyboardView.listener = this
        suggestionBar.onSuggestionClick = { word -> commitSuggestion(word) }
        emojiPanel.onEmojiSelected = { emoji -> currentInputConnection?.commitText(emoji, 1) }

        applyPreferences()
        keyboardView.setKeyboardLayout(Layouts.qwerty)
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyPreferences()

        onSymbolsPage2 = false
        emojiVisible = false
        capsOn = false
        capsLocked = false

        keyboardView.setKeyboardLayout(Layouts.qwerty)
        keyboardView.setCaps(false, false)
        keyboardView.setEnterLabel(enterLabelFor(info))
        keyboardView.visibility = View.VISIBLE
        emojiPanel.visibility = View.GONE

        wordBuffer.clear()
        suggestionBar.setSuggestions(emptyList())
    }

    private fun applyPreferences() {
        val mode = try {
            ThemeMode.valueOf(prefs.themeMode)
        } catch (e: IllegalArgumentException) {
            ThemeMode.AUTO
        }
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = Themes.forMode(mode, isDark, prefs.accentColor)

        keyboardView.setTheme(theme)
        keyboardView.showPopupPreview = prefs.keyPopupEnabled
        suggestionBar.applyTheme(theme)
        emojiPanel.applyTheme(theme)
    }

    private fun enterLabelFor(info: EditorInfo?): String {
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        return when (action) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "enter"
        }
    }

    override fun onKey(key: KeyDef, outputChar: String) {
        val ic = currentInputConnection ?: return
        when (key.type) {
            KeyType.CHARACTER -> {
                ic.commitText(outputChar, 1)
                if (capsOn && !capsLocked) {
                    capsOn = false
                    keyboardView.setCaps(false, false)
                }
                updateWordBuffer(outputChar)
            }
            KeyType.SPACE -> {
                ic.commitText(" ", 1)
                commitWord()
            }
            KeyType.ENTER -> {
                sendEnterKey(ic)
                commitWord()
            }
            KeyType.BACKSPACE -> {
                ic.deleteSurroundingText(1, 0)
                if (wordBuffer.isNotEmpty()) wordBuffer.deleteCharAt(wordBuffer.length - 1)
                updateSuggestions()
            }
            KeyType.SHIFT -> toggleShift()
            KeyType.SYMBOLS -> {
                onSymbolsPage2 = false
                keyboardView.setKeyboardLayout(Layouts.symbols1)
            }
            KeyType.LETTERS -> {
                keyboardView.setKeyboardLayout(Layouts.qwerty)
                keyboardView.setCaps(capsOn, capsLocked)
            }
            KeyType.EXTRA_SYMBOLS -> {
                onSymbolsPage2 = !onSymbolsPage2
                keyboardView.setKeyboardLayout(if (onSymbolsPage2) Layouts.symbols2 else Layouts.symbols1)
            }
            KeyType.EMOJI -> toggleEmojiPanel()
            KeyType.LANGUAGE -> {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }

        if (prefs.vibrationEnabled) SoundFeedback.vibrate(this)
        if (prefs.soundEnabled) SoundFeedback.playClick(this)
    }

    private fun sendEnterKey(ic: InputConnection) {
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val noEnterFlag = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val hasUsableAction = action != null && action != EditorInfo.IME_ACTION_NONE && !noEnterFlag

        if (hasUsableAction) {
            ic.performEditorAction(action!!)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        when {
            capsOn && now - lastShiftTapTime < 300 -> {
                capsLocked = true
                capsOn = true
            }
            capsLocked -> {
                capsLocked = false
                capsOn = false
            }
            else -> capsOn = !capsOn
        }
        lastShiftTapTime = now
        keyboardView.setCaps(capsOn, capsLocked)
    }

    private fun toggleEmojiPanel() {
        emojiVisible = !emojiVisible
        if (emojiVisible) {
            emojiPanel.visibility = View.VISIBLE
            keyboardView.visibility = View.GONE
        } else {
            emojiPanel.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
            keyboardView.setKeyboardLayout(Layouts.qwerty)
        }
    }

    private fun updateWordBuffer(char: String) {
        if (char.length == 1 && char[0].isLetter()) {
            wordBuffer.append(char)
            updateSuggestions()
        } else {
            commitWord()
        }
    }

    private fun updateSuggestions() {
        if (!prefs.suggestionsEnabled) {
            suggestionBar.setSuggestions(emptyList())
            return
        }
        suggestionBar.setSuggestions(WordDictionary.suggestionsFor(wordBuffer.toString()))
    }

    private fun commitWord() {
        wordBuffer.clear()
        suggestionBar.setSuggestions(emptyList())
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (wordBuffer.isNotEmpty()) {
            ic.deleteSurroundingText(wordBuffer.length, 0)
        }
        ic.commitText("$word ", 1)
        commitWord()
    }
}
