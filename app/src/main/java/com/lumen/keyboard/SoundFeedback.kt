package com.lumen.keyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Click sounds are built from Android's stock system sound-effect set
 * (no bundled audio assets needed). Each "style" simply maps typing
 * actions to a different effect constant / volume so they feel distinct.
 */
object SoundFeedback {

    const val STYLE_STANDARD = "STANDARD"
    const val STYLE_SOFT = "SOFT"
    const val STYLE_MECHANICAL = "MECHANICAL"

    fun playClick(context: Context, style: String = STYLE_STANDARD) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        when (style) {
            STYLE_SOFT -> am.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.15f)
            STYLE_MECHANICAL -> am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 0.5f)
            else -> am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.3f)
        }
    }

    fun vibrate(context: Context) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
