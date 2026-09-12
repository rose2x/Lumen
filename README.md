# Vironix

A from-scratch Android terminal app that gives you a **real Linux
environment** with a genuine package manager, with support for multiple
distributions and automatic root detection — built and documented so you
can learn how it works, not just use it.

## How it works

Android doesn't ship a full Linux userland or root access by default. Vironix
uses the same approach several established "Linux on Android" projects use,
extended in two directions: **distro choice** and **root-aware execution**.

### 1. Choose your distro

On first launch you pick one of:

| Distro | Package manager | Upstream source |
|---|---|---|
| Alpine Linux | `apk` | Alpine's official minirootfs |
| Ubuntu 24.04 LTS | `apt` | Canonical's official cloud-image rootfs |
| Debian 12 (Bookworm) | `apt` | Debian's official docker-brew rootfs |
| Arch Linux ARM | `pacman` | Arch Linux ARM (the real, separate ARM build of Arch — mainline Arch has no official ARM builds) |

Each is the **exact, unmodified official rootfs archive** that distro
publishes for servers/containers. Once extracted, `apt install git` /
`apk add git` / `pacman -S git` are genuinely that distro's real package
manager doing real work — not a reimplementation.

### 2. Root-aware execution

On launch, `RootDetector` actually tests (not just guesses) whether the
device has working root access, by running `su -c id` and checking for
`uid=0` in the output:

- **Rooted device** → Vironix uses `su -c "chroot <rootfs> ..."` — a real
  kernel-level `chroot`. Faster, and avoids the handful of syscalls `proot`
  can't perfectly emulate.
- **Non-rooted device** → Vironix falls back to **proot**, which fakes
  chroot/root behavior via `ptrace`, entirely in userspace. No special
  permissions needed, works on any Android device.

Both paths land you in the same place: a real interactive shell inside the
distro you picked.

### 3. Real terminal emulation — true color, alt-screen, mouse, scroll regions

Vironix has an actual VT100/xterm-class terminal emulator (`terminal/TerminalEmulator.kt`),
separated from the Android rendering code (`TerminalView.kt`) — the same
split real terminal emulators use:

- Full 16/256-color **and 24-bit true color** ANSI support (`ls --color`,
  colored prompts, `htop`, neovim's true-color themes, etc. all look right)
- Bold, underline, reverse-video text attributes
- Cursor positioning, line/screen clearing, scrolling, **scroll regions**
  (`ESC[<top>;<bottom>r`) — used by `less`, status-bar apps, etc.
- **Alternate screen buffer**: `vim`, `htop`, `less`, and `tmux` take over
  the full screen and hand your shell scrollback back exactly as it was
  when you quit — the same mechanism real terminals use
- **Mouse reporting**: tapping the screen sends real xterm mouse-report
  escape sequences to apps that request them (`vim` mouse mode, `tmux`
  pane selection, etc.), both legacy and modern SGR encoding
- A blinking cursor, with a choice of block/underline/bar style (see Settings)
- A row of extra keys (Ctrl, Esc, Tab, arrows, Home/End) docked below the
  screen, since phone keyboards don't have these — tap Ctrl then a letter
  to send Ctrl+C, Ctrl+D, etc., the same interaction Termux uses
- Screen resizing (rotation, keyboard open/close) is passed through to the
  shell via `TIOCSWINSZ`, so `vim`/`htop`/etc. reflow correctly

### 4. Multiple sessions (tabs)

Open several independent shell tabs at once, each with its own process,
cursor, colors, and scrollback — start a build in one, run `htop` in
another, edit a file in a third. Switching tabs is instant; background
tabs keep running and producing output the whole time (`SessionManager.kt`).
Tap **+** in the tab bar for a new session, tap the active tab's **×** to
close it.

### 5. Settings — fonts, color schemes, cursor style

A real settings screen (`SettingsActivity.kt`): adjustable font size,
five color schemes (Classic Green, Solarized Dark, Monokai, Dracula,
Light), three cursor styles (block/underline/bar), and a terminal-bell
toggle. Changes are saved immediately and apply the moment you return to
the terminal.

### 6. Shared storage access

Tap "Storage access" on the main screen to grant Vironix broad file
access, the same permission Termux's own `termux-setup-storage` requests.
Once granted, every installed distro automatically gets a `~/storage/`
folder full of **symlinks** into your phone's real storage
(`StorageAccess.kt`):

```
~/storage/shared      -> /storage/emulated/0
~/storage/dcim        -> /storage/emulated/0/DCIM
~/storage/downloads   -> /storage/emulated/0/Download
~/storage/pictures    -> /storage/emulated/0/Pictures
~/storage/music       -> /storage/emulated/0/Music
~/storage/movies      -> /storage/emulated/0/Movies
```

`cp report.pdf ~/storage/downloads/` inside the shell puts the file
exactly where your phone's Downloads app shows it — because it's a
symlink to that same real folder, not a copy.

## Project structure

```
app/src/main/jni/            Native C: PTY creation, process exec (termux-pty.c)
app/src/main/java/.../
  RootDetector.kt             Tests for real, working root access
  DistroCatalog.kt            Supported distros + their official rootfs URLs
  BootstrapInstaller.kt       Downloads/extracts a chosen distro's rootfs (gzip + xz)
  ShellLauncher.kt            Builds the chroot (rooted) or proot (unrooted) command
  StorageAccess.kt            Requests storage permission, sets up ~/storage symlinks
  DistroPickerActivity.kt     Entry screen: pick a distro, see root/storage status
  DistroAdapter.kt            RecyclerView adapter for the distro list
  SessionManager.kt           Owns all open shell tabs, each with its own emulator
  SessionTabsBar.kt           Tappable tab-chip UI row (switch/close/new)
  TerminalSession.kt          Kotlin <-> native JNI bridge for one shell process
  TerminalView.kt             Renders the terminal grid, handles keyboard/mouse input
  ExtraKeysBar.kt             Ctrl/Esc/Tab/arrow key row for phone keyboards
  TerminalActivity.kt         Hosts the session pool + tab bar for one distro
  TerminalService.kt          Keeps shells alive in background (foreground service)
  TerminalPreferences.kt      Persisted settings: font size, color scheme, cursor style
  SettingsActivity.kt         Settings screen UI
  ColorSchemeAdapter.kt       RecyclerView adapter for the color scheme list
  terminal/TerminalCell.kt    One character cell: char + palette/true color + style
  terminal/TerminalEmulator.kt VT100/xterm parser: grid, alt-screen, scroll regions, mouse
.github/workflows/build.yml   CI: builds a debug APK on every push
```

## Building locally

Requires Android Studio (or the command-line SDK) with:
- JDK 17
- Android SDK 34
- NDK 26.1.10909125
- CMake 3.22.1

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Building via GitHub Actions

Every push to `main` (or manual trigger from the Actions tab) builds a debug
APK automatically. Download it from the workflow run's **Artifacts** section.

## Current limitations (good next steps if you want to extend it)

- The terminal emulator covers the escape sequences real-world shells,
  `vim`, `htop`, `tmux`, and package managers actually use — it is not a
  complete xterm implementation (no bracketed-paste mode, no Sixel/image
  graphics, no terminfo/termcap database — Vironix advertises itself as
  `xterm-256color` which works almost everywhere but isn't a byte-perfect
  match). See TODOs in `terminal/TerminalEmulator.kt`.
- No app signing configured (CI produces a debug-signed APK — fine for
  personal installs, not for Play Store distribution).
- Arch Linux ARM's rootfs is large (~450-500MB) and `pacman-key --init` /
  `--populate` can take a while on first run — this is normal for Arch, not
  a bug.
- Rooted `chroot` mode assumes the device's `su` accepts the standard
  `su -c "<command>"` calling convention (true for Magisk, SuperSU, and
  KernelSU — the three most common root solutions).
- `MANAGE_EXTERNAL_STORAGE` (used for `~/storage`) is a sensitive
  permission Google Play restricts heavily for apps distributed through
  the Play Store — fine for a personal/sideloaded APK like this one, but
  worth knowing if you ever plan to publish it there.
- Each session tab currently uses the same distro/root configuration
  chosen on the picker screen; there's no per-tab distro switching yet
  (would be a natural next step in `SessionManager.kt`).

## Legal note

This app downloads and runs the *official, unmodified* root filesystems
published by Alpine, Canonical (Ubuntu), Debian, and Arch Linux ARM, plus
the open-source `proot` tool — each under its own upstream license. No
proprietary Termux code is used or redistributed.
