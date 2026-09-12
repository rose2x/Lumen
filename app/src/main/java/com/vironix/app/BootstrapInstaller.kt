package com.vironix.app

import android.content.Context
import android.util.Log
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * BootstrapInstaller
 *
 * WHAT THIS CLASS DOES (for beginners):
 * A phone doesn't come with a full Linux filesystem (/bin, /etc, /usr, etc.)
 * or a real package manager built in. So the first time the user picks a
 * distro, we:
 *
 *   1. Download a small pre-built "proot" binary for the phone's CPU type
 *      (used only if the device is NOT rooted — see RootDetector).
 *   2. Download that distro's OFFICIAL minimal root filesystem tarball —
 *      the exact same archive the distro project publishes for
 *      servers/containers. Nothing about it is modified.
 *   3. Extract it into the app's private storage, one folder per distro,
 *      so multiple distros can be installed side-by-side.
 *
 * After this runs once, the real package manager for that distro
 * (apk / apt / pacman) works exactly as it would on a real machine,
 * because it genuinely IS that distro's files — we're just choosing how
 * to run them (proot emulation vs a real chroot when rooted).
 */
class BootstrapInstaller(private val context: Context) {

    companion object {
        private const val TAG = "BootstrapInstaller"

        // Statically-linked proot builds for Android, published by the
        // termux/proot project (open source, widely used, GPLv2).
        private fun prootUrl(arch: DeviceArch) =
            if (arch == DeviceArch.ARM64)
                "https://github.com/termux/proot/releases/latest/download/proot-aarch64"
            else
                "https://github.com/termux/proot/releases/latest/download/proot-arm"
    }

    /** Each distro gets its own subfolder, so several can coexist. */
    fun rootfsDir(distro: DistroId): File =
        File(context.filesDir, "rootfs-${distro.name.lowercase()}")

    fun prootBinary(): File = File(context.filesDir, "bin/proot")

    fun isInstalled(distro: DistroId): Boolean {
        val dir = rootfsDir(distro)
        // "bin/sh" existing is a good proxy for "extraction completed successfully"
        // across all four distros (all of them ship a shell at that path).
        return dir.exists() && (File(dir, "bin/sh").exists() || File(dir, "usr/bin/sh").exists())
    }

    fun installedDistros(): List<DistroId> = DistroId.entries.filter { isInstalled(it) }

    /**
     * Runs the full bootstrap for one distro. Call this from a background
     * thread — it does network I/O and disk extraction, both slow.
     *
     * @param useRoot whether to skip downloading proot (rooted devices use
     *        a real `chroot`/`su` instead — see ShellLauncher).
     * @param onProgress called with human-readable status updates for the UI.
     */
    fun install(distro: Distro, useRoot: Boolean, onProgress: (String) -> Unit) {
        val arch = DistroCatalog.currentDeviceArch()
        Log.i(TAG, "Bootstrapping ${distro.displayName} for arch=$arch, root=$useRoot")

        if (!useRoot && !prootBinary().exists()) {
            onProgress("Downloading proot (no-root mode)...")
            prootBinary().parentFile?.mkdirs()
            downloadFile(prootUrl(arch), prootBinary())
            prootBinary().setExecutable(true, false)
        }

        onProgress("Downloading ${distro.displayName} (${distro.approxDownloadSize})...")
        val url = distro.rootfsUrl(arch)
        val cacheFile = File(context.cacheDir, "rootfs-${distro.id.name.lowercase()}.tmp")
        downloadFile(url, cacheFile)

        onProgress("Extracting ${distro.displayName} filesystem...")
        val target = rootfsDir(distro.id)
        target.mkdirs()
        extractArchive(cacheFile, target, isXz = url.endsWith(".xz"))
        cacheFile.delete()

        onProgress("Configuring network and package manager...")
        writeResolvConf(target)

        onProgress("${distro.displayName} ready.")
        Log.i(TAG, "Bootstrap complete for ${distro.displayName}")
    }

    private fun downloadFile(urlStr: String, dest: File) {
        val connection = URL(urlStr).openConnection()
        connection.connectTimeout = 20000
        connection.readTimeout = 20000
        connection.getInputStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, bufferSize = 16384)
            }
        }
    }

    /**
     * Extracts a .tar.gz or .tar.xz archive.
     *
     * Gzip decoding uses Java's built-in GZIPInputStream. Xz decoding uses
     * the small, pure-Java org.tukaani:xz library (added as a Gradle
     * dependency) since the JVM has no built-in XZ support — Ubuntu and
     * Debian's official rootfs archives are shipped as .tar.xz.
     *
     * The TAR parsing itself is a minimal hand-written POSIX (ustar)
     * reader, kept dependency-free and readable for learning purposes.
     */
    private fun extractArchive(archiveFile: File, outputDir: File, isXz: Boolean) {
        val rawStream = archiveFile.inputStream()
        val decompressed: InputStream = if (isXz) XZInputStream(rawStream) else GZIPInputStream(rawStream)

        decompressed.use { stream ->
            val buffer = ByteArray(512)
            while (true) {
                val headerRead = readFully(stream, buffer, 512)
                if (headerRead < 512) break
                if (buffer.all { it == 0.toByte() }) break // end-of-archive marker

                val name = String(buffer, 0, 100).trimEnd('\u0000')
                if (name.isEmpty()) break

                val sizeOctal = String(buffer, 124, 12).trim('\u0000', ' ')
                val size = if (sizeOctal.isEmpty()) 0L else sizeOctal.toLong(8)
                val typeFlag = buffer[156].toInt().toChar()

                // ustar "prefix" field (offset 345, 155 bytes) holds the rest
                // of long paths — some distro tarballs (Debian's especially)
                // use this for deeply nested paths.
                val prefix = String(buffer, 345, 155).trimEnd('\u0000')
                val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

                val outFile = File(outputDir, fullName)

                when (typeFlag) {
                    '5' -> outFile.mkdirs() // directory
                    '0', '\u0000' -> { // regular file
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var remaining = size
                            val chunk = ByteArray(16384)
                            while (remaining > 0) {
                                val toRead = minOf(chunk.size.toLong(), remaining).toInt()
                                val read = readFully(stream, chunk, toRead)
                                if (read <= 0) break
                                out.write(chunk, 0, read)
                                remaining -= read
                            }
                        }
                    }
                    '1', '2' -> {
                        // Hard link / symlink: skip content (none follows for these types).
                    }
                    else -> {
                        // Char/block devices, fifos, etc. — not needed for a base rootfs,
                        // and Android's filesystem generally can't create them anyway.
                    }
                }

                val padding = (512 - (size % 512)) % 512
                if (padding > 0 && (typeFlag == '0' || typeFlag == '\u0000')) skipFully(stream, padding)
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, len: Int): Int {
        var total = 0
        while (total < len) {
            val read = input.read(buffer, total, len - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (read < 0) break
            remaining -= read
        }
    }

    /** Every distro needs a working DNS config inside the rootfs to reach package mirrors. */
    private fun writeResolvConf(rootfsDir: File) {
        File(rootfsDir, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
    }
}
