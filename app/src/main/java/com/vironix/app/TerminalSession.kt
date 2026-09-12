package com.vironix.app

import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * TerminalSession
 *
 * This is the Kotlin "wrapper" around our native C code (termux-pty.c).
 * It's responsible for:
 *  1. Starting the shell process (proot or chroot, into whichever distro
 *     was selected) attached to a PTY.
 *  2. Giving the UI a stream to write user keystrokes into.
 *  3. Giving the UI a stream to read shell output from.
 *
 * Think of this class as "the phone line" connecting the on-screen
 * terminal to the actual Linux process running underneath.
 */
class TerminalSession(
    private val shellPath: String,
    private val workingDirectory: String,
    private val args: Array<String>,
    private val environment: Array<String>,
    private val onSessionFinished: (exitCode: Int) -> Unit
) {
    companion object {
        private const val TAG = "TerminalSession"

        // Loads libvironix.so, which contains the native functions below.
        init {
            System.loadLibrary("vironix")
        }

        // These are implemented in termux-pty.c. JNI matches them by name.
        @JvmStatic
        external fun createSubprocess(
            cmd: String,
            cwd: String,
            args: Array<String>,
            env: Array<String>,
            processIdOut: IntArray,
            rows: Int,
            cols: Int
        ): Int

        @JvmStatic
        external fun setWindowSize(fd: Int, rows: Int, cols: Int)

        @JvmStatic
        external fun waitFor(pid: Int): Int

        @JvmStatic
        external fun closeFd(fd: Int)
    }

    var terminalFileDescriptor: Int = -1
        private set
    private var shellPid: Int = -1

    lateinit var inputStream: FileInputStream   // Read shell output from here
        private set
    lateinit var outputStream: FileOutputStream // Write user keystrokes here

    /** Starts the shell. Call this once, from a background thread. */
    fun start(initialRows: Int = 24, initialCols: Int = 80) {
        val pidHolder = IntArray(1)
        terminalFileDescriptor = createSubprocess(
            shellPath, workingDirectory, args, environment, pidHolder, initialRows, initialCols
        )
        shellPid = pidHolder[0]

        if (terminalFileDescriptor < 0) {
            throw IOException("Failed to start shell process (native createSubprocess returned $terminalFileDescriptor)")
        }

        // Raw int file descriptors from JNI can't be wrapped directly in a
        // FileInputStream/FileOutputStream on Android — ParcelFileDescriptor
        // is the standard bridge that takes ownership of the raw fd and
        // exposes a real java.io.FileDescriptor for both streams to share.
        val pfd = android.os.ParcelFileDescriptor.adoptFd(terminalFileDescriptor)
        inputStream = FileInputStream(pfd.fileDescriptor)
        outputStream = FileOutputStream(pfd.fileDescriptor)

        Log.i(TAG, "Shell started, pid=$shellPid fd=$terminalFileDescriptor")

        // Wait for the shell to exit on a background thread, then notify the UI.
        Thread {
            val exitCode = waitFor(shellPid)
            Log.i(TAG, "Shell exited with code $exitCode")
            onSessionFinished(exitCode)
        }.start()
    }

    /** Call when the on-screen terminal is resized (rotation, keyboard, etc). */
    fun updateSize(rows: Int, cols: Int) {
        if (terminalFileDescriptor >= 0) {
            setWindowSize(terminalFileDescriptor, rows, cols)
        }
    }

    /** Sends user input (typed text) to the shell. */
    fun write(data: ByteArray) {
        try {
            outputStream.write(data)
            outputStream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed writing to shell", e)
        }
    }

    fun finish() {
        if (terminalFileDescriptor >= 0) {
            closeFd(terminalFileDescriptor)
            terminalFileDescriptor = -1
        }
    }
}
