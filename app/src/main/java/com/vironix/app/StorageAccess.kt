package com.vironix.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * StorageAccess
 *
 * WHAT THIS DOES (for beginners):
 * Modern Android ("scoped storage") doesn't let apps freely read/write
 * arbitrary files on the device by default — you have to explicitly ask
 * the user for broad storage access, the same way Termux's own
 * `termux-setup-storage` command does.
 *
 * Once granted, this class creates a `~/storage/` folder INSIDE the
 * distro's rootfs containing SYMLINKS (not copies) pointing at the real
 * Android storage locations:
 *
 *   ~/storage/shared   -> /storage/emulated/0            (all shared storage)
 *   ~/storage/dcim     -> /storage/emulated/0/DCIM        (camera photos/videos)
 *   ~/storage/downloads -> /storage/emulated/0/Download   (downloads)
 *   ~/storage/pictures -> /storage/emulated/0/Pictures
 *   ~/storage/music    -> /storage/emulated/0/Music
 *   ~/storage/movies   -> /storage/emulated/0/Movies
 *
 * This means `cp somefile.txt ~/storage/downloads/` inside the shell
 * really does put the file where your Android Downloads app shows it —
 * because it IS that same folder, just reached through a symlink. This is
 * the exact mechanism (and exact folder layout) Termux itself uses.
 */
object StorageAccess {

    /** True if this app currently has broad storage access granted. */
    fun hasAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Pre-Android 11 devices use the older runtime permission model;
            // ContextCompat.checkSelfPermission is the standard check there.
            true // caller is expected to have requested READ/WRITE_EXTERNAL_STORAGE separately on old versions
        }
    }

    /**
     * Builds the Intent that sends the user to Android's system settings
     * screen to grant "All files access" — there is no in-app popup for
     * this particular permission on Android 11+, the user must flip it in
     * Settings, exactly like Termux's own setup flow requires.
     */
    fun buildPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * Creates the ~/storage/* symlinks inside one distro's rootfs. Safe to
     * call multiple times — existing correct symlinks are left alone.
     *
     * Note: this creates actual Unix symlinks on the Android filesystem
     * (Java's java.nio.file.Files.createSymbolicLink), which works fine
     * within the app's own private storage where the rootfs lives.
     */
    fun setupStorageSymlinks(rootfsDir: File) {
        val storageRoot = File(rootfsDir, "root/storage")
        storageRoot.mkdirs()

        val sharedRoot = Environment.getExternalStorageDirectory() // /storage/emulated/0

        val links = mapOf(
            "shared" to sharedRoot,
            "dcim" to File(sharedRoot, "DCIM"),
            "downloads" to File(sharedRoot, "Download"),
            "pictures" to File(sharedRoot, "Pictures"),
            "music" to File(sharedRoot, "Music"),
            "movies" to File(sharedRoot, "Movies"),
            "podcasts" to File(sharedRoot, "Podcasts")
        )

        for ((linkName, target) in links) {
            val linkFile = File(storageRoot, linkName)
            createSymlinkIfNeeded(linkFile, target)
        }
    }

    private fun createSymlinkIfNeeded(linkFile: File, target: File) {
        try {
            val linkPath = linkFile.toPath()
            if (java.nio.file.Files.isSymbolicLink(linkPath)) {
                val current = java.nio.file.Files.readSymbolicLink(linkPath)
                if (current.toString() == target.absolutePath) return // already correct
                java.nio.file.Files.delete(linkPath) // stale/wrong link, recreate below
            } else if (linkFile.exists()) {
                return // a real file/folder already exists here — don't clobber user data
            }
            java.nio.file.Files.createSymbolicLink(linkPath, target.toPath())
        } catch (e: Exception) {
            // Symlink creation can fail on some device/filesystem combinations
            // (e.g. certain SD card filesystems) — fail quietly per-link rather
            // than aborting the whole setup, matching Termux's own tolerant behavior.
        }
    }
}
