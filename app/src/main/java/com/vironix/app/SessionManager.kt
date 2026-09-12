package com.vironix.app

import com.vironix.app.terminal.TerminalEmulator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * SessionManager
 *
 * WHAT THIS DOES (for beginners):
 * Termux lets you have several independent terminal "tabs" open at once —
 * e.g. one running a build, another with `htop`, another for editing a
 * file — and switch between them without losing any of their state or
 * output. Each tab is a fully separate shell process with its own screen.
 *
 * This class owns that list of sessions. Each SessionEntry bundles:
 *   - A TerminalSession (the actual shell process + PTY, see TerminalSession.kt)
 *   - A TerminalEmulator (that session's OWN character grid / cursor / colors —
 *     switching tabs must not mix up what's on each tab's screen)
 *   - A background thread continuously pumping the shell's output into
 *     that emulator, so a session keeps producing/updating its screen
 *     even while a DIFFERENT tab is the one currently visible.
 *
 * TerminalActivity asks this class for the "active" session's emulator to
 * display, and switches which one is active when the user taps a tab.
 */
class SessionManager {

    data class SessionEntry(
        val id: Int,
        val title: String,
        val session: TerminalSession,
        val emulator: TerminalEmulator,
        var isAlive: Boolean = true
    )

    private val idCounter = AtomicInteger(0)
    private val _sessions = CopyOnWriteArrayList<SessionEntry>()
    val sessions: List<SessionEntry> get() = _sessions

    var activeSessionId: Int = -1
        private set

    /** Fired whenever the session list changes (created/closed) or the active tab switches. */
    var onSessionsChanged: (() -> Unit)? = null

    /** Fired when the CURRENTLY ACTIVE session's screen updates, so the view knows to redraw. */
    var onActiveSessionDirty: (() -> Unit)? = null

    /**
     * Starts a brand-new session (tab) running the given distro/root
     * configuration, and makes it the active tab.
     */
    fun createSession(
        title: String,
        launchSpec: LaunchSpec,
        initialRows: Int,
        initialCols: Int
    ): SessionEntry {
        val id = idCounter.incrementAndGet()
        val emulator = TerminalEmulator(initialRows, initialCols)

        lateinit var entry: SessionEntry

        val terminalSession = TerminalSession(
            shellPath = launchSpec.executable,
            workingDirectory = launchSpec.workingDirectory,
            args = launchSpec.args,
            environment = launchSpec.environment,
            onSessionFinished = { _ ->
                entry.isAlive = false
                onSessionsChanged?.invoke()
            }
        )

        entry = SessionEntry(id, title, terminalSession, emulator)
        _sessions.add(entry)
        activeSessionId = id

        terminalSession.start(initialRows, initialCols)
        pumpOutput(entry)

        onSessionsChanged?.invoke()
        return entry
    }

    /** Continuously reads one session's shell output into its OWN emulator, forever, on a background thread. */
    private fun pumpOutput(entry: SessionEntry) {
        thread {
            val buffer = ByteArray(4096)
            val input = entry.session.inputStream
            while (entry.isAlive) {
                val read = try {
                    input.read(buffer)
                } catch (e: Exception) {
                    break
                }
                if (read <= 0) break
                entry.emulator.feed(buffer, read)
                if (entry.id == activeSessionId) onActiveSessionDirty?.invoke()
            }
            entry.isAlive = false
        }
    }

    fun activeSession(): SessionEntry? = _sessions.firstOrNull { it.id == activeSessionId }

    fun switchTo(id: Int) {
        if (_sessions.any { it.id == id }) {
            activeSessionId = id
            onSessionsChanged?.invoke()
        }
    }

    /** Closes one session's shell process and removes its tab. */
    fun closeSession(id: Int) {
        val entry = _sessions.firstOrNull { it.id == id } ?: return
        entry.isAlive = false
        entry.session.finish()
        _sessions.remove(entry)

        if (activeSessionId == id) {
            activeSessionId = _sessions.lastOrNull()?.id ?: -1
        }
        onSessionsChanged?.invoke()
    }

    /** Closes every session — called when the whole app/Activity is being destroyed. */
    fun closeAll() {
        _sessions.forEach {
            it.isAlive = false
            it.session.finish()
        }
        _sessions.clear()
        activeSessionId = -1
    }
}
