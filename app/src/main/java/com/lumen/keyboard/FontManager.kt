package com.lumen.keyboard

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.InputStream

data class BuiltInFont(val id: String, val displayName: String, val assetPath: String)

/**
 * All bundled fonts are real, free, open-source Google Fonts (SIL Open Font
 * License 1.1 -- free for any use, including public/commercial). Their
 * license text ships alongside them in assets/fonts/licenses/.
 *
 * A user-imported .ttf is copied into the app's private storage on import
 * (see [importCustomFont]) so it keeps working even if the original file
 * picked from the device becomes unavailable later.
 */
object FontManager {

    const val SYSTEM_FONT_ID = "SYSTEM"
    const val CUSTOM_FONT_ID = "CUSTOM"

    val builtIns = listOf(
        BuiltInFont(SYSTEM_FONT_ID, "System default", ""),
        BuiltInFont("POPPINS", "Poppins", "fonts/Poppins-Regular.ttf"),
        BuiltInFont("NUNITO", "Nunito", "fonts/Nunito-Variable.ttf"),
        BuiltInFont("LATO", "Lato", "fonts/Lato-Regular.ttf"),
        BuiltInFont("JETBRAINS_MONO", "JetBrains Mono", "fonts/JetBrainsMono-Variable.ttf"),
        BuiltInFont("COMFORTAA", "Comfortaa", "fonts/Comfortaa-Variable.ttf")
    )

    fun customFontFile(context: Context): File = File(context.filesDir, "fonts/custom_font.ttf")

    fun hasCustomFont(context: Context): Boolean = customFontFile(context).exists()

    /** Copies a picked font stream into private storage. Returns true on success. */
    fun importCustomFont(context: Context, input: InputStream): Boolean =
        FileCopyUtils.copyStreamToFile(input, customFontFile(context))

    fun resolveTypeface(context: Context, fontId: String): Typeface {
        if (fontId == CUSTOM_FONT_ID) {
            val file = customFontFile(context)
            if (file.exists()) {
                return try {
                    Typeface.createFromFile(file)
                } catch (e: Exception) {
                    Typeface.DEFAULT
                }
            }
            return Typeface.DEFAULT
        }
        val builtIn = builtIns.firstOrNull { it.id == fontId } ?: return Typeface.DEFAULT
        if (builtIn.assetPath.isEmpty()) return Typeface.DEFAULT
        return try {
            Typeface.createFromAsset(context.assets, builtIn.assetPath)
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    fun displayName(fontId: String): String {
        if (fontId == CUSTOM_FONT_ID) return "Custom (imported)"
        return builtIns.firstOrNull { it.id == fontId }?.displayName ?: "System default"
    }
}
