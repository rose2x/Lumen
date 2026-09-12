package com.vironix.app

import android.content.Context
import java.io.File

/**
 * ShellLauncher
 *
 * Builds the exact command line needed to enter a distro's root filesystem,
 * using one of two strategies depending on whether the device is rooted
 * (see RootDetector):
 *
 * ROOTED (real chroot):
 *   su -c "chroot <rootfs> /usr/bin/env -i HOME=/root TERM=... /bin/sh -l"
 *   This uses the Linux kernel's real chroot() syscall via root privileges.
 *   Faster and more capable than proot (fewer syscall-emulation quirks),
 *   but only works if the user has granted root access.
 *
 * NOT ROOTED (proot emulation):
 *   proot --link2symlink -0 -r <rootfs> -b /dev -b /proc -b /sys \
 *         -w /root /usr/bin/env -i HOME=/root TERM=... /bin/sh -l
 *   proot fakes chroot/root behavior using ptrace, entirely in userspace —
 *   no special permissions needed at all. This is the default path and
 *   works on any Android device.
 *
 * Both paths land the user in the exact same place: an interactive shell
 * inside the chosen distro's real filesystem.
 */
object ShellLauncher {

    fun buildCommand(context: Context, distroId: DistroId, useRoot: Boolean, suPath: String?): LaunchSpec {
        val bootstrap = BootstrapInstaller(context)
        val rootfs = bootstrap.rootfsDir(distroId).absolutePath
        val tmpDir = ensureDir(context, "tmp").absolutePath

        return if (useRoot && suPath != null) {
            buildRootedCommand(suPath, rootfs)
        } else {
            buildProotCommand(bootstrap.prootBinary().absolutePath, rootfs, tmpDir)
        }
    }

    /**
     * Rooted path: use the device's real `su` to gain root, then use the
     * real kernel `chroot` command. This is the same thing you'd do by
     * hand on a rooted device's terminal.
     */
    private fun buildRootedCommand(suPath: String, rootfs: String): LaunchSpec {
        val innerCommand = listOf(
            "chroot", rootfs,
            "/usr/bin/env", "-i",
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh", "-l"
        ).joinToString(" ")

        return LaunchSpec(
            executable = suPath,
            // `su -c "<command string>"` is the standard way every root
            // solution (Magisk, SuperSU, KernelSU) accepts a command to
            // run with elevated privileges.
            args = arrayOf(suPath, "-c", innerCommand),
            environment = arrayOf(),
            workingDirectory = rootfs
        )
    }

    /**
     * No-root path: proot, exactly as before, just now parameterized by
     * whichever distro's rootfs the user picked.
     */
    private fun buildProotCommand(proot: String, rootfs: String, tmpDir: String): LaunchSpec {
        val args = mutableListOf(
            proot,
            "--link2symlink",
            "-0",                                  // fake root inside sandbox (not real device root)
            "-r", rootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "$tmpDir:/tmp",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh", "-l"
        )

        return LaunchSpec(
            executable = proot,
            args = args.toTypedArray(),
            environment = arrayOf("PROOT_TMP_DIR=$tmpDir"),
            workingDirectory = tmpDir
        )
    }

    /** Returns (creating if needed) a subdirectory inside the app's private storage. */
    private fun ensureDir(context: Context, name: String): File {
        val dir = File(context.filesDir, name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

/** Bundles everything TerminalSession needs to start the process. */
data class LaunchSpec(
    val executable: String,
    val args: Array<String>,
    val environment: Array<String>,
    val workingDirectory: String
)
