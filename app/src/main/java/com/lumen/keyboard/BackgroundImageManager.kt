package com.lumen.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream

object BackgroundImageManager {

    fun imageFile(context: Context): File = File(context.filesDir, "background/keyboard_bg.jpg")

    fun hasImage(context: Context): Boolean = imageFile(context).exists()

    /** Copies a picked image stream into private storage. Returns true on success. */
    fun importImage(context: Context, input: InputStream): Boolean =
        FileCopyUtils.copyStreamToFile(input, imageFile(context))

    fun clearImage(context: Context) {
        val file = imageFile(context)
        if (file.exists()) file.delete()
    }

    /** Loads a bitmap downsampled to roughly fit the given target size, to keep memory use sane. */
    fun loadBitmap(context: Context, targetWidth: Int, targetHeight: Int): Bitmap? {
        val file = imageFile(context)
        if (!file.exists() || targetWidth <= 0 || targetHeight <= 0) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth && bounds.outHeight / (sample * 2) >= targetHeight) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }
}
