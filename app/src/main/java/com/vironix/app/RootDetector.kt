package com.vironix.app

import java.io.File

/**
 * RootDetector
 *
 * WHAT THIS DOES (for beginners):
 * "Rooted" means the Android device has an app like Magisk or SuperSU
 * installed that grants processes real root (uid 0) privileges via the
 * `su` command — the same `su` you'd use on a normal Linux machine.
 *
 * We don't assume anything about the device; we actually TEST whether
 * `su` exists AND actually works (some ROMs ship a broken/fake su),
 * by running `su -c id` and checking the output says "uid=0".
 *
 * Why this matters for the app: if we have real root, we can `chroot`
 * directly into the Linux rootfs — this is faster and supports things
 * proot can't (some ioctls, certain network operations, etc.), because
 * it's the real kernel-level chroot instead of an emulated one.
 * If we don't have root, we transparently fall back to proot, which
 * needs no special permissions at all.
 */
object RootDetector {

    data class RootStatus(val isRooted: Boolean, val suPath: String?)

    // Common locations `su` is installed at across different root solutions
    // (Magisk, SuperSU, KernelSU, etc.) — we check all of them since there's
    // no single standard path.
    private val knownSuPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /**
     * Runs the actual detection. This does a real process execution
     * (running `su -c id`), so call it from a background thread —
     * never from the main/UI thread.
     */
    fun detect(): RootStatus {
        // First, find a candidate su binary either on PATH or at a known location.
        val candidate = knownSuPaths.firstOrNull { File(it).exists() }
            ?: findOnPath("su")
            ?: return RootStatus(isRooted = false, suPath = null)

        return if (verifySuWorks(candidate)) {
            RootStatus(isRooted = true, suPath = candidate)
        } else {
            RootStatus(isRooted = false, suPath = null)
        }
    }

    private fun findOnPath(binary: String): String? {
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(":")) {
            val f = File(dir, binary)
            if (f.exists()) return f.absolutePath
        }
        return null
    }

    /**
     * Actually attempts to run a command as root and checks the result.
     * This is the only reliable way to know root truly works — merely
     * finding a file named "su" is not proof (it could be a stub, or
     * the user could deny the root permission prompt).
     */
    private fun verifySuWorks(suPath: String): Boolean {
        return try {
            val process = ProcessBuilder(suPath, "-c", "id").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            exited && process.exitValue() == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
}
