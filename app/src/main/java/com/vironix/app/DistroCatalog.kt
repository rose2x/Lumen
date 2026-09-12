package com.vironix.app

import android.os.Build

/**
 * DistroCatalog
 *
 * WHAT THIS DOES (for beginners):
 * Defines the list of Linux distributions Vironix can install, and where
 * to download each one's OFFICIAL minimal root filesystem from. These are
 * the exact same rootfs archives those projects publish for servers,
 * Docker, and other container use — we're not repackaging or modifying
 * anything, just downloading and extracting them like any container
 * runtime would.
 *
 * Each distro has a different package manager, which is the whole point:
 *   Alpine -> apk
 *   Debian/Ubuntu -> apt
 *   Arch (via Arch Linux ARM) -> pacman
 *
 * Once installed, "install git" is simply the native command for that
 * package manager (apk add git / apt install git / pacman -S git).
 */
enum class DistroId { ALPINE, UBUNTU, DEBIAN, ARCH }

data class Distro(
    val id: DistroId,
    val displayName: String,
    val packageManagerHint: String, // shown in the UI so users know what to type
    val approxDownloadSize: String,
    val rootfsUrl: (arch: DeviceArch) -> String,
    val postInstallCommands: List<String> // run once after extraction, e.g. to init the package db
)

/** Normalizes Android's ABI names into the naming each distro project uses. */
enum class DeviceArch { ARM64, ARMV7 }

object DistroCatalog {

    fun currentDeviceArch(): DeviceArch {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return if (abi.contains("arm64") || abi.contains("aarch64")) DeviceArch.ARM64 else DeviceArch.ARMV7
    }

    private const val ALPINE_VERSION = "3.20.3"
    private const val ALPINE_BRANCH = "v3.20"

    // Ubuntu's official base-images rootfs tarballs (same ones used for
    // Ubuntu's Docker Official Image), published under Canonical's
    // cloud-images project.
    private const val UBUNTU_RELEASE = "noble" // 24.04 LTS

    // Debian's official docker-brew base rootfs source.
    private const val DEBIAN_RELEASE = "bookworm" // Debian 12

    val all: List<Distro> = listOf(
        Distro(
            id = DistroId.ALPINE,
            displayName = "Alpine Linux",
            packageManagerHint = "apk add <package>",
            approxDownloadSize = "~5-8 MB",
            rootfsUrl = { arch ->
                val a = if (arch == DeviceArch.ARM64) "aarch64" else "armv7"
                "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/releases/$a/alpine-minirootfs-$ALPINE_VERSION-$a.tar.gz"
            },
            postInstallCommands = listOf(
                "apk update"
            )
        ),
        Distro(
            id = DistroId.UBUNTU,
            displayName = "Ubuntu 24.04 LTS",
            packageManagerHint = "apt install <package>",
            approxDownloadSize = "~65-90 MB",
            rootfsUrl = { arch ->
                val a = if (arch == DeviceArch.ARM64) "arm64" else "armhf"
                // Canonical publishes current base rootfs tarballs here:
                "https://cloud-images.ubuntu.com/$UBUNTU_RELEASE/current/$UBUNTU_RELEASE-server-cloudimg-$a-root.tar.xz"
            },
            postInstallCommands = listOf(
                "apt update"
            )
        ),
        Distro(
            id = DistroId.DEBIAN,
            displayName = "Debian 12 (Bookworm)",
            packageManagerHint = "apt install <package>",
            approxDownloadSize = "~50-70 MB",
            rootfsUrl = { arch ->
                val a = if (arch == DeviceArch.ARM64) "arm64" else "armhf"
                // Debian's official docker-brew rootfs mirror:
                "https://github.com/debuerreotype/docker-brew-debian/raw/dist-$DEBIAN_RELEASE/$a/rootfs.tar.xz"
            },
            postInstallCommands = listOf(
                "apt update"
            )
        ),
        Distro(
            id = DistroId.ARCH,
            displayName = "Arch Linux ARM",
            packageManagerHint = "pacman -S <package>",
            approxDownloadSize = "~450-500 MB",
            rootfsUrl = { arch ->
                // NOTE: Mainline Arch Linux doesn't build for ARM at all —
                // Arch Linux ARM (archlinuxarm.org) is the real, separate,
                // long-running project that provides Arch for ARM devices.
                // This is the correct and only legitimate upstream for
                // "Arch on a phone."
                val a = if (arch == DeviceArch.ARM64) "aarch64" else "armv7"
                "http://os.archlinuxarm.org/os/ArchLinuxARM-$a-latest.tar.gz"
            },
            postInstallCommands = listOf(
                "pacman-key --init",
                "pacman-key --populate archlinuxarm",
                "pacman -Sy --noconfirm"
            )
        )
    )

    fun byId(id: DistroId): Distro = all.first { it.id == id }
}
