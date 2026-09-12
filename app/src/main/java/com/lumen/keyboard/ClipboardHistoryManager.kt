package com.lumen.keyboard

import android.content.ClipboardManager
import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray

/**
 * Keeps a small, persisted history of copied text so it survives the IME
 * process being recreated. Stored as a JSON array of strings in the same
 * SharedPreferences file the rest of the app uses -- no database needed
 * for a list this small.
 */
class ClipboardHistoryManager(private val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    fun start() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (listener != null) return
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val text = try {
                cm.primaryClip?.let { clip ->
                    if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
                }
            } catch (e: Exception) {
                null
            }
            if (!text.isNullOrBlank()) add(text)
        }
        listener = l
        cm.addPrimaryClipChangedListener(l)
    }

    fun stop() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        listener?.let { cm.removePrimaryClipChangedListener(it) }
        listener = null
    }

    fun add(text: String) {
        val trimmed = text.take(MAX_ITEM_LENGTH)
        val current = history().toMutableList()
        current.removeAll { it == trimmed }
        current.add(0, trimmed)
        while (current.size > MAX_ITEMS) current.removeAt(current.size - 1)
        save(current)
    }

    fun remove(text: String) {
        val current = history().toMutableList()
        current.remove(text)
        save(current)
    }

    fun clear() {
        save(emptyList())
    }

    fun history(): List<String> {
        return try {
            val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "clipboard_history_json"
        private const val MAX_ITEMS = 25
        private const val MAX_ITEM_LENGTH = 500
    }
}
