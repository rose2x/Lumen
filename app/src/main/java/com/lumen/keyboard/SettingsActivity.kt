package com.lumen.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager.beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var prefs: PreferencesManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            prefs = PreferencesManager(requireContext())

            findPreference<Preference>("font_picker_launch")?.apply {
                summary = FontManager.displayName(prefs.fontId)
                setOnPreferenceClickListener { showFontChooser(); true }
            }
            findPreference<Preference>("background_image_launch")?.setOnPreferenceClickListener {
                launchImagePicker(); true
            }
            findPreference<Preference>("background_image_clear")?.setOnPreferenceClickListener {
                BackgroundImageManager.clearImage(requireContext())
                Toast.makeText(requireContext(), R.string.bg_cleared, Toast.LENGTH_SHORT).show()
                true
            }
        }

        private fun showFontChooser() {
            val builtInNames = FontManager.builtIns.map { it.displayName }
            val options = builtInNames + getString(R.string.pref_font_import)
            val currentIndex = FontManager.builtIns.indexOfFirst { it.id == prefs.fontId }.let {
                if (it >= 0) it else -1
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_font_title)
                .setSingleChoiceItems(options.toTypedArray(), currentIndex) { dialog, which ->
                    if (which < FontManager.builtIns.size) {
                        prefs.fontId = FontManager.builtIns[which].id
                        findPreference<Preference>("font_picker_launch")?.summary =
                            FontManager.displayName(prefs.fontId)
                        dialog.dismiss()
                    } else {
                        dialog.dismiss()
                        launchFontPicker()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun launchFontPicker() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream"))
            }
            startActivityForResult(intent, REQUEST_FONT)
        }

        private fun launchImagePicker() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_BACKGROUND_IMAGE)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode != Activity.RESULT_OK) return
            val uri = data?.data ?: return

            when (requestCode) {
                REQUEST_FONT -> {
                    val input = try {
                        requireContext().contentResolver.openInputStream(uri)
                    } catch (e: Exception) {
                        null
                    }
                    val ok = input != null && FontManager.importCustomFont(requireContext(), input)
                    input?.close()
                    if (ok) {
                        prefs.fontId = FontManager.CUSTOM_FONT_ID
                        findPreference<Preference>("font_picker_launch")?.summary =
                            FontManager.displayName(prefs.fontId)
                        Toast.makeText(requireContext(), R.string.font_imported, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), R.string.font_import_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                REQUEST_BACKGROUND_IMAGE -> {
                    val input = try {
                        requireContext().contentResolver.openInputStream(uri)
                    } catch (e: Exception) {
                        null
                    }
                    val ok = input != null && BackgroundImageManager.importImage(requireContext(), input)
                    input?.close()
                    Toast.makeText(
                        requireContext(),
                        if (ok) R.string.bg_imported else R.string.bg_import_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        companion object {
            private const val REQUEST_FONT = 4101
            private const val REQUEST_BACKGROUND_IMAGE = 4102
        }
    }
}
