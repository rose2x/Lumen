package com.vironix.app

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * SettingsActivity
 *
 * Lets the user customize how the terminal looks and behaves: font size,
 * cursor style, terminal bell, and color scheme. All changes are saved
 * immediately via TerminalPreferences and take effect the next time a
 * TerminalView is shown (see TerminalActivity, which reads these on
 * create/resume).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: TerminalPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = TerminalPreferences(this)

        setupFontSize()
        setupCursorStyle()
        setupBellToggle()
        setupColorSchemeList()
    }

    private fun setupFontSize() {
        val seekBar = findViewById<SeekBar>(R.id.fontSizeSeekBar)
        val valueLabel = findViewById<TextView>(R.id.fontSizeValue)

        // SeekBar only supports integer progress starting at 0, so we map
        // 0..20 onto the actual MIN_FONT_SIZE..MAX_FONT_SIZE range.
        val range = TerminalPreferences.MAX_FONT_SIZE - TerminalPreferences.MIN_FONT_SIZE
        fun progressToSize(progress: Int) = TerminalPreferences.MIN_FONT_SIZE + (range * progress / 20f)
        fun sizeToProgress(size: Float) = (((size - TerminalPreferences.MIN_FONT_SIZE) / range) * 20).toInt()

        seekBar.progress = sizeToProgress(prefs.fontSize)
        valueLabel.text = "${prefs.fontSize.toInt()}sp"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val size = progressToSize(progress)
                valueLabel.text = "${size.toInt()}sp"
                prefs.fontSize = size
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupCursorStyle() {
        val group = findViewById<RadioGroup>(R.id.cursorStyleGroup)
        val blockButton = findViewById<android.widget.RadioButton>(R.id.cursorBlock)
        val underlineButton = findViewById<android.widget.RadioButton>(R.id.cursorUnderline)
        val barButton = findViewById<android.widget.RadioButton>(R.id.cursorBar)

        when (prefs.cursorStyle) {
            CursorStyle.BLOCK -> blockButton.isChecked = true
            CursorStyle.UNDERLINE -> underlineButton.isChecked = true
            CursorStyle.BAR -> barButton.isChecked = true
        }

        group.setOnCheckedChangeListener { _, checkedId ->
            prefs.cursorStyle = when (checkedId) {
                R.id.cursorUnderline -> CursorStyle.UNDERLINE
                R.id.cursorBar -> CursorStyle.BAR
                else -> CursorStyle.BLOCK
            }
        }
    }

    private fun setupBellToggle() {
        val switchView = findViewById<Switch>(R.id.bellSwitch)
        switchView.isChecked = prefs.bellEnabled
        switchView.setOnCheckedChangeListener { _, isChecked -> prefs.bellEnabled = isChecked }
    }

    private fun setupColorSchemeList() {
        val recyclerView = findViewById<RecyclerView>(R.id.colorSchemeList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ColorSchemeAdapter(ColorScheme.all, prefs.colorScheme.id) { scheme ->
            prefs.colorScheme = scheme
        }
    }
}
