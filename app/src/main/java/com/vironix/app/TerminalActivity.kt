package com.vironix.app

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vironix.app.databinding.ActivityTerminalBinding
import kotlin.concurrent.thread

/**
 * TerminalActivity
 *
 * Runs a DISTRO'S session pool — one or more concurrent shell tabs
 * (see SessionManager), all backed by the same distro's rootfs, but each
 * with its own independent shell process and screen. Which distro, and
 * whether to use root (real chroot) or no-root (proot), is passed in from
 * DistroPickerActivity via Intent extras.
 *
 * On first open of a given distro it runs the bootstrap (downloads +
 * extracts that distro's rootfs), shows progress, then starts the first
 * shell session. Additional tabs can be opened from the tab bar at any
 * time, exactly like Termux's "new session" option.
 */
class TerminalActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DISTRO_ID = "distro_id"
        const val EXTRA_USE_ROOT = "use_root"
        const val EXTRA_SU_PATH = "su_path"
    }

    private lateinit var binding: ActivityTerminalBinding
    private lateinit var extraKeysBar: ExtraKeysBar
    private lateinit var sessionTabsBar: SessionTabsBar
    private val sessionManager = SessionManager()

    private lateinit var distroId: DistroId
    private var useRoot: Boolean = false
    private var suPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val distroName = intent.getStringExtra(EXTRA_DISTRO_ID) ?: DistroId.ALPINE.name
        distroId = DistroId.valueOf(distroName)
        useRoot = intent.getBooleanExtra(EXTRA_USE_ROOT, false)
        suPath = intent.getStringExtra(EXTRA_SU_PATH)

        setupExtraKeys()
        setupSessionTabs()
        setupTerminalView()
        binding.terminalView.applySettings(TerminalPreferences(this))

        val bootstrap = BootstrapInstaller(this)
        if (bootstrap.isInstalled(distroId)) {
            startFirstSession()
        } else {
            runBootstrap(bootstrap)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply settings every time this screen becomes visible again —
        // e.g. the user went to Settings, changed the font size or color
        // scheme, and pressed back. Cheap to call even if nothing changed.
        binding.terminalView.applySettings(TerminalPreferences(this))
    }

    private fun setupExtraKeys() {
        extraKeysBar = ExtraKeysBar(binding.extraKeysRow) { bytes ->
            sessionManager.activeSession()?.session?.write(bytes)
        }
        extraKeysBar.build()
    }

    private fun setupSessionTabs() {
        sessionTabsBar = SessionTabsBar(
            container = binding.sessionTabsRow,
            onSwitchTo = { id -> switchToSession(id) },
            onCloseSession = { id -> closeSession(id) },
            onNewSession = { startNewSession() }
        )

        sessionManager.onSessionsChanged = {
            runOnUiThread {
                sessionTabsBar.render(sessionManager.sessions, sessionManager.activeSessionId)
                refreshActiveEmulatorAttachment()
            }
        }
        sessionManager.onActiveSessionDirty = {
            runOnUiThread { binding.terminalView.invalidate() }
        }
    }

    private fun setupTerminalView() {
        binding.terminalView.onKeyTyped = { bytes ->
            val text = String(bytes, Charsets.UTF_8)
            if (!extraKeysBar.interceptIfCtrlArmed(text)) {
                sessionManager.activeSession()?.session?.write(bytes)
            }
        }
        binding.terminalView.onMouseEvent = { bytes ->
            sessionManager.activeSession()?.session?.write(bytes)
        }
        binding.terminalView.onSizeChangedListener = { rows, cols ->
            // Resize EVERY open session to match the view, not just the active
            // one — otherwise switching to a background tab after rotating
            // the screen would show stale wrapping until its next line feed.
            sessionManager.sessions.forEach {
                it.emulator.resize(rows, cols)
                it.session.updateSize(rows, cols)
            }
        }
        binding.terminalView.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.terminalView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun runBootstrap(bootstrap: BootstrapInstaller) {
        binding.statusText.visibility = TextView.VISIBLE
        binding.statusText.text = "Setting up ${DistroCatalog.byId(distroId).displayName}..."

        thread {
            try {
                bootstrap.install(DistroCatalog.byId(distroId), useRoot) { message ->
                    runOnUiThread { binding.statusText.text = message }
                }
                if (StorageAccess.hasAccess()) {
                    StorageAccess.setupStorageSymlinks(bootstrap.rootfsDir(distroId))
                }
                runOnUiThread {
                    binding.statusText.visibility = TextView.GONE
                    startFirstSession()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.statusText.text = "Setup failed: ${e.message}\n\nCheck your internet connection and reopen the app."
                }
            }
        }
    }

    private fun startFirstSession() {
        startNewSession()
    }

    /** Starts a brand-new tab running the same distro/root configuration as the others. */
    private fun startNewSession() {
        thread {
            try {
                val spec = ShellLauncher.buildCommand(this, distroId, useRoot, suPath)
                sessionManager.createSession(
                    title = DistroCatalog.byId(distroId).displayName.take(6),
                    launchSpec = spec,
                    initialRows = binding.terminalView.rows(),
                    initialCols = binding.terminalView.columns()
                )
            } catch (e: Exception) {
                runOnUiThread { binding.statusText.text = "Failed to start shell: ${e.message}" }
            }
        }
    }

    private fun switchToSession(id: Int) {
        sessionManager.switchTo(id)
        sessionTabsBar.render(sessionManager.sessions, sessionManager.activeSessionId)
        refreshActiveEmulatorAttachment()
    }

    private fun closeSession(id: Int) {
        sessionManager.closeSession(id)
        // If that was the last tab, land back on the distro picker rather
        // than showing an empty terminal with nothing running.
        if (sessionManager.sessions.isEmpty()) {
            finish()
        } else {
            sessionTabsBar.render(sessionManager.sessions, sessionManager.activeSessionId)
            refreshActiveEmulatorAttachment()
        }
    }

    /** Points TerminalView at whichever session is now active, so its screen actually shows. */
    private fun refreshActiveEmulatorAttachment() {
        sessionManager.activeSession()?.let { binding.terminalView.attachEmulator(it.emulator) }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.closeAll()
    }
}
