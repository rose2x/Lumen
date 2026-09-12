package com.vironix.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

/**
 * DistroPickerActivity
 *
 * The app's entry point (launcher activity). Responsibilities:
 *   1. Detect root access in the background (RootDetector) and show the
 *      result to the user, since it affects how fast/capable the shell
 *      will be.
 *   2. Show the list of supported distros (Alpine / Ubuntu / Debian /
 *      Arch) with install status.
 *   3. On tap: if not installed yet, hand off to TerminalActivity which
 *      will run the bootstrap; if already installed, jump straight to
 *      the shell.
 */
class DistroPickerActivity : AppCompatActivity() {

    private lateinit var rootStatusText: TextView
    private var isRooted = false
    private var suPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_distro_picker)

        rootStatusText = findViewById(R.id.rootStatusText)
        val recyclerView = findViewById<RecyclerView>(R.id.distroList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<TextView>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupStorageStatus()

        refreshDistroList(recyclerView)
        detectRootInBackground(recyclerView)
    }

    override fun onResume() {
        super.onResume()
        // The user may have just come back from the system settings screen
        // after granting storage access — refresh the status text and,
        // if now granted, set up the ~/storage symlinks for every distro.
        updateStorageStatusText()
    }

    private fun setupStorageStatus() {
        updateStorageStatusText()
        findViewById<TextView>(R.id.storageStatusText).setOnClickListener {
            if (!StorageAccess.hasAccess()) {
                startActivity(StorageAccess.buildPermissionIntent(this))
            }
        }
    }

    private fun updateStorageStatusText() {
        val statusView = findViewById<TextView>(R.id.storageStatusText)
        if (StorageAccess.hasAccess()) {
            statusView.text = "Storage access: granted (~/storage available in each distro)"
            linkStorageForInstalledDistros()
        } else {
            statusView.text = "Storage access: not granted — tap to enable"
        }
    }

    /** Once permission is granted, wire up ~/storage symlinks for every distro already installed. */
    private fun linkStorageForInstalledDistros() {
        thread {
            val bootstrap = BootstrapInstaller(this)
            bootstrap.installedDistros().forEach { distroId ->
                StorageAccess.setupStorageSymlinks(bootstrap.rootfsDir(distroId))
            }
        }
    }

    private fun refreshDistroList(recyclerView: RecyclerView) {
        val bootstrap = BootstrapInstaller(this)
        val installed = bootstrap.installedDistros().toSet()
        recyclerView.adapter = DistroAdapter(DistroCatalog.all, installed) { distro ->
            openDistro(distro)
        }
    }

    private fun detectRootInBackground(recyclerView: RecyclerView) {
        thread {
            val status = RootDetector.detect()
            isRooted = status.isRooted
            suPath = status.suPath
            runOnUiThread {
                rootStatusText.text = if (isRooted) {
                    "Root detected — using fast chroot mode"
                } else {
                    "No root detected — using proot (no-root) mode"
                }
                // Re-render so any downstream state depending on root status is fresh.
                refreshDistroList(recyclerView)
            }
        }
    }

    private fun openDistro(distro: Distro) {
        val intent = Intent(this, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_DISTRO_ID, distro.id.name)
            putExtra(TerminalActivity.EXTRA_USE_ROOT, isRooted)
            putExtra(TerminalActivity.EXTRA_SU_PATH, suPath)
        }
        startActivity(intent)
    }
}
