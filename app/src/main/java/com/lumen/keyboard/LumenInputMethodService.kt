package com.lumen.keyboard

import android.Manifest
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.lumen.keyboard.model.KeyDef
import com.lumen.keyboard.model.KeyType
import com.lumen.keyboard.model.Layouts
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class LumenInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private enum class Panel { NONE, EMOJI, CLIPBOARD, GIF, VOICE }

    private lateinit var prefs: PreferencesManager
    private lateinit var clipboardHistory: ClipboardHistoryManager
    private lateinit var container: LinearLayout
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var keyboardView: KeyboardView
    private lateinit var emojiPanel: EmojiPanelView
    private lateinit var clipboardPanel: ClipboardPanelView
    private lateinit var gifResultsStrip: GifResultsStrip
    private lateinit var voiceInputStrip: VoiceInputStrip

    private val mainHandler = Handler(Looper.getMainLooper())

    private var panel = Panel.NONE
    private var capsOn = false
    private var capsLocked = false
    private var lastShiftTapTime = 0L
    private var onSymbolsPage2 = false
    private val wordBuffer = StringBuilder()
    private var lastAutocorrect: Pair<String, String>? = null // original -> corrected

    private val gifQuery = StringBuilder()
    private var gifSearchRunnable: Runnable? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        clipboardHistory = ClipboardHistoryManager(this)
        if (prefs.clipboardHistoryEnabled) clipboardHistory.start()
    }

    override fun onDestroy() {
        clipboardHistory.stop()
        stopVoiceRecognition()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        container = LayoutInflater.from(this)
            .inflate(R.layout.view_keyboard_container, null) as LinearLayout

        suggestionBar = container.findViewById(R.id.suggestionBar)
        keyboardView = container.findViewById(R.id.keyboardView)
        emojiPanel = container.findViewById(R.id.emojiPanel)
        clipboardPanel = container.findViewById(R.id.clipboardPanel)
        gifResultsStrip = container.findViewById(R.id.gifResultsStrip)
        voiceInputStrip = container.findViewById(R.id.voiceInputStrip)

        keyboardView.listener = this
        keyboardView.onOneHandedExit = {
            prefs.oneHandedSide = OneHandedSide.OFF
            keyboardView.setOneHandedSide(OneHandedSide.OFF)
        }

        suggestionBar.onSuggestionClick = { word -> commitSuggestion(word) }
        suggestionBar.onClipboardClick = { togglePanel(Panel.CLIPBOARD) }
        suggestionBar.onMicClick = { togglePanel(Panel.VOICE) }
        suggestionBar.onGifClick = { togglePanel(Panel.GIF) }

        emojiPanel.onEmojiSelected = { emoji -> currentInputConnection?.commitText(emoji, 1) }

        clipboardPanel.onItemSelected = { text ->
            currentInputConnection?.commitText(text, 1)
            showPanel(Panel.NONE)
        }
        clipboardPanel.onItemDeleted = { text ->
            clipboardHistory.remove(text)
            refreshClipboardPanel()
        }

        gifResultsStrip.onClose = { showPanel(Panel.NONE) }
        gifResultsStrip.onGifSelected = { gif -> commitGif(gif) }

        voiceInputStrip.onCancel = {
            stopVoiceRecognition()
            showPanel(Panel.NONE)
        }
        voiceInputStrip.onDone = { speechRecognizer?.stopListening() }

        applyPreferences()
        keyboardView.setKeyboardLayout(currentLetterLayout(), swipeEligible = true)
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyPreferences()

        onSymbolsPage2 = false
        capsOn = false
        capsLocked = false
        lastAutocorrect = null

        keyboardView.setKeyboardLayout(currentLetterLayout(), swipeEligible = true)
        keyboardView.setCaps(false, false)
        keyboardView.setEnterLabel(enterLabelFor(info))
        showPanel(Panel.NONE)

        wordBuffer.clear()
        gifQuery.clear()
        suggestionBar.setSuggestions(emptyList())
    }

    private fun currentLetterLayout() =
        if (prefs.numberRowEnabled) Layouts.qwertyWithNumberRow else Layouts.qwerty

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

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
        keyboardView.swipeTypingEnabled = prefs.swipeTypingEnabled
        keyboardView.setOneHandedSide(prefs.oneHandedSide)
        keyboardView.setSplitEnabled(prefs.splitKeyboardEnabled)
        suggestionBar.applyTheme(theme)
        emojiPanel.applyTheme(theme)
        clipboardPanel.applyTheme(theme)
        gifResultsStrip.applyTheme(theme)
        voiceInputStrip.applyTheme(theme)

        val typeface = FontManager.resolveTypeface(this, prefs.fontId)
        keyboardView.setTypeface(typeface)
        suggestionBar.applyTypeface(typeface)
        clipboardPanel.applyTypeface(typeface)
        gifResultsStrip.applyTypeface(typeface)
        voiceInputStrip.applyTypeface(typeface)

        if (prefs.clipboardHistoryEnabled) clipboardHistory.start() else clipboardHistory.stop()

        applyKeyboardHeight()
        loadBackgroundImageAsync()
    }

    private fun applyKeyboardHeight() {
        val baseDp = if (prefs.numberRowEnabled) 276f else 230f
        val px = (baseDp * (prefs.keyboardHeightPercent / 100f) * resources.displayMetrics.density).toInt()
        for (v in listOf(keyboardView, emojiPanel, clipboardPanel)) {
            val lp = v.layoutParams
            lp.height = px
            v.layoutParams = lp
        }
    }

    private fun loadBackgroundImageAsync() {
        if (!BackgroundImageManager.hasImage(this)) {
            keyboardView.setBackgroundImage(null)
            return
        }
        val targetW = resources.displayMetrics.widthPixels
        val targetH = dpToPx(260f)
        Thread {
            val bmp = BackgroundImageManager.loadBitmap(this, targetW, targetH)
            mainHandler.post { keyboardView.setBackgroundImage(bmp) }
        }.start()
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

    // ---------------------------------------------------------------
    // KeyboardView.OnKeyboardActionListener
    // ---------------------------------------------------------------

    override fun onKey(key: KeyDef, outputChar: String) {
        val ic = currentInputConnection ?: return

        if (panel == Panel.GIF) {
            handleGifSearchKey(key, outputChar)
        } else {
            handleTypingKey(ic, key, outputChar)
        }

        if (prefs.vibrationEnabled) SoundFeedback.vibrate(this)
        if (prefs.soundEnabled) SoundFeedback.playClick(this, prefs.soundStyle)
    }

    override fun onSwipeGesture(letters: List<Char>) {
        val ic = currentInputConnection ?: return
        val candidates = GestureTypingEngine.candidatesFor(letters)
        if (candidates.isEmpty()) return

        val best = candidates.first()
        val display = if (capsOn || capsLocked) best.replaceFirstChar { it.uppercase() } else best
        ic.commitText("$display ", 1)

        if (capsOn && !capsLocked) {
            capsOn = false
            keyboardView.setCaps(false, false)
        }
        lastAutocorrect = null
        wordBuffer.clear()
        if (prefs.suggestionsEnabled) {
            suggestionBar.setSuggestions(candidates.drop(1).take(3))
        }
        if (prefs.vibrationEnabled) SoundFeedback.vibrate(this)
        if (prefs.soundEnabled) SoundFeedback.playClick(this, prefs.soundStyle)
    }

    override fun onCursorMove(steps: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    override fun onDeleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString()
        if (before.isNullOrEmpty()) return

        var end = before.length
        while (end > 0 && before[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && !before[start - 1].isWhitespace()) start--
        val deleteCount = before.length - start

        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
            wordBuffer.clear()
            lastAutocorrect = null
            updateSuggestions()
        }
    }

    override fun onSpaceLongPress() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun handleTypingKey(ic: InputConnection, key: KeyDef, outputChar: String) {
        when (key.type) {
            KeyType.CHARACTER -> {
                if (outputChar.length == 1 && outputChar[0].isLetter()) lastAutocorrect = null
                ic.commitText(outputChar, 1)
                if (capsOn && !capsLocked) {
                    capsOn = false
                    keyboardView.setCaps(false, false)
                }
                updateWordBuffer(outputChar)
            }
            KeyType.SPACE -> {
                maybeAutocorrect(ic)
                ic.commitText(" ", 1)
                commitWord()
            }
            KeyType.ENTER -> {
                maybeAutocorrect(ic)
                sendEnterKey(ic)
                commitWord()
            }
            KeyType.BACKSPACE -> handleBackspace(ic)
            KeyType.SHIFT -> toggleShift()
            KeyType.SYMBOLS -> {
                onSymbolsPage2 = false
                keyboardView.setKeyboardLayout(Layouts.symbols1, swipeEligible = false)
            }
            KeyType.LETTERS -> {
                keyboardView.setKeyboardLayout(currentLetterLayout(), swipeEligible = true)
                keyboardView.setCaps(capsOn, capsLocked)
            }
            KeyType.EXTRA_SYMBOLS -> {
                onSymbolsPage2 = !onSymbolsPage2
                keyboardView.setKeyboardLayout(
                    if (onSymbolsPage2) Layouts.symbols2 else Layouts.symbols1,
                    swipeEligible = false
                )
            }
            KeyType.EMOJI -> togglePanel(Panel.EMOJI)
            KeyType.LANGUAGE -> {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
    }

    private fun handleGifSearchKey(key: KeyDef, outputChar: String) {
        when (key.type) {
            KeyType.CHARACTER -> {
                gifQuery.append(outputChar)
                gifResultsStrip.setQuery(gifQuery.toString())
                scheduleGifSearch()
            }
            KeyType.SPACE -> {
                gifQuery.append(' ')
                gifResultsStrip.setQuery(gifQuery.toString())
                scheduleGifSearch()
            }
            KeyType.BACKSPACE -> {
                if (gifQuery.isNotEmpty()) gifQuery.deleteCharAt(gifQuery.length - 1)
                gifResultsStrip.setQuery(gifQuery.toString())
                scheduleGifSearch()
            }
            KeyType.ENTER -> {
                gifSearchRunnable?.let { mainHandler.removeCallbacks(it) }
                performGifSearchNow()
            }
            else -> { /* other keys are ignored while searching GIFs */ }
        }
    }

    private fun maybeAutocorrect(ic: InputConnection) {
        lastAutocorrect = null
        if (!prefs.autocorrectEnabled) return
        val word = wordBuffer.toString()
        if (word.length < 3) return
        val correction = AutoCorrector.correct(word) ?: return
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(correction, 1)
        lastAutocorrect = word to correction
    }

    private fun handleBackspace(ic: InputConnection) {
        val autocorrect = lastAutocorrect
        if (autocorrect != null && wordBuffer.isEmpty()) {
            val (original, corrected) = autocorrect
            ic.deleteSurroundingText(corrected.length + 1, 0) // corrected word + the space after it
            ic.commitText(original, 1)
            wordBuffer.append(original)
            lastAutocorrect = null
            updateSuggestions()
            return
        }
        lastAutocorrect = null
        ic.deleteSurroundingText(1, 0)
        if (wordBuffer.isNotEmpty()) wordBuffer.deleteCharAt(wordBuffer.length - 1)
        updateSuggestions()
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

    // ---------------------------------------------------------------
    // Panels: Emoji / Clipboard / GIF / Voice
    // ---------------------------------------------------------------

    private fun togglePanel(target: Panel) {
        showPanel(if (panel == target) Panel.NONE else target)
    }

    private fun showPanel(target: Panel) {
        if (panel == Panel.VOICE && target != Panel.VOICE) stopVoiceRecognition()
        panel = target

        keyboardView.visibility = if (target == Panel.EMOJI || target == Panel.CLIPBOARD) View.GONE else View.VISIBLE
        emojiPanel.visibility = if (target == Panel.EMOJI) View.VISIBLE else View.GONE
        clipboardPanel.visibility = if (target == Panel.CLIPBOARD) View.VISIBLE else View.GONE
        suggestionBar.visibility = if (target == Panel.GIF || target == Panel.VOICE) View.GONE else View.VISIBLE
        gifResultsStrip.visibility = if (target == Panel.GIF) View.VISIBLE else View.GONE
        voiceInputStrip.visibility = if (target == Panel.VOICE) View.VISIBLE else View.GONE

        when (target) {
            Panel.CLIPBOARD -> refreshClipboardPanel()
            Panel.GIF -> {
                gifQuery.clear()
                gifResultsStrip.setQuery("")
                gifResultsStrip.setStatus(
                    if (prefs.tenorApiKey.isBlank()) getString(R.string.pref_tenor_key_summary)
                    else getString(R.string.gif_search_hint)
                )
            }
            Panel.VOICE -> startVoiceRecognition()
            Panel.EMOJI, Panel.NONE -> {}
        }
    }

    private fun refreshClipboardPanel() {
        clipboardPanel.setItems(clipboardHistory.history())
    }

    // ---------------------------------------------------------------
    // GIF search + insertion
    // ---------------------------------------------------------------

    private fun scheduleGifSearch() {
        gifSearchRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { performGifSearchNow() }
        gifSearchRunnable = r
        mainHandler.postDelayed(r, 500)
    }

    private fun performGifSearchNow() {
        val query = gifQuery.toString().trim()
        val key = prefs.tenorApiKey

        if (key.isBlank()) {
            gifResultsStrip.setStatus(getString(R.string.pref_tenor_key_summary))
            return
        }
        if (query.isEmpty()) {
            gifResultsStrip.setStatus(getString(R.string.gif_search_hint))
            return
        }

        gifResultsStrip.setStatus("\u2026")
        TenorApiClient.search(
            apiKey = key,
            query = query,
            onResult = { results -> if (panel == Panel.GIF) gifResultsStrip.setResults(results) },
            onError = { message -> if (panel == Panel.GIF) gifResultsStrip.setStatus(message) }
        )
    }

    private fun commitGif(gif: GifResult) {
        val ic = currentInputConnection
        val editorInfo = currentInputEditorInfo
        if (ic == null || editorInfo == null) {
            showPanel(Panel.NONE)
            return
        }

        val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val supportsGif = mimeTypes.any { it.equals("image/gif", ignoreCase = true) || it == "image/*" }
        if (!supportsGif) {
            Toast.makeText(this, R.string.gif_not_supported, Toast.LENGTH_SHORT).show()
            showPanel(Panel.NONE)
            return
        }

        Thread {
            try {
                val connection = URL(gif.fullUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val bytes = connection.inputStream.use { it.readBytes() }
                connection.disconnect()

                val dir = File(cacheDir, "gifs").apply { mkdirs() }
                val file = File(dir, "gif_${System.currentTimeMillis()}.gif")
                file.writeBytes(bytes)

                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

                mainHandler.post {
                    val info = InputContentInfoCompat(uri, ClipDescription("GIF", arrayOf("image/gif")), null)
                    val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                    val ok = InputConnectionCompat.commitContent(ic, editorInfo, info, flags, null)
                    if (!ok) Toast.makeText(this, R.string.gif_not_supported, Toast.LENGTH_SHORT).show()
                    showPanel(Panel.NONE)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Couldn't insert GIF (${e.message ?: "error"})", Toast.LENGTH_SHORT).show()
                    showPanel(Panel.NONE)
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------
    // Voice typing
    // ---------------------------------------------------------------

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceInputStrip.setError(getString(R.string.voice_not_available))
            return
        }

        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            voiceInputStrip.setListening(false, getString(R.string.voice_requesting_permission))
            VoiceBridge.onPermissionResult = { grantedNow ->
                VoiceBridge.onPermissionResult = null
                mainHandler.post {
                    if (panel == Panel.VOICE) {
                        if (grantedNow) startVoiceRecognitionInternal()
                        else voiceInputStrip.setError(getString(R.string.voice_permission_denied))
                    }
                }
            }
            startActivity(
                Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        startVoiceRecognitionInternal()
    }

    private fun startVoiceRecognitionInternal() {
        voiceInputStrip.setListening(true, "")
        stopVoiceRecognition()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                mainHandler.post {
                    if (panel == Panel.VOICE) voiceInputStrip.setError(voiceErrorMessage(error))
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                mainHandler.post {
                    if (!text.isNullOrBlank()) currentInputConnection?.commitText("$text ", 1)
                    showPanel(Panel.NONE)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                mainHandler.post {
                    if (panel == Panel.VOICE && !text.isNullOrBlank()) voiceInputStrip.setListening(true, text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    private fun stopVoiceRecognition() {
        speechRecognizer?.let {
            try {
                it.stopListening()
                it.destroy()
            } catch (e: Exception) {
                // best-effort cleanup only
            }
        }
        speechRecognizer = null
    }

    private fun voiceErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.voice_no_match)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.voice_permission_denied)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> getString(R.string.voice_network_error)
        else -> getString(R.string.voice_generic_error)
    }

    // ---------------------------------------------------------------
    // Suggestions
    // ---------------------------------------------------------------

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
        lastAutocorrect = null
        commitWord()
    }
}
