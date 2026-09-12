/*
 * termux-pty.c
 *
 * WHAT THIS FILE DOES (for beginners):
 * A real terminal app needs a "PTY" (pseudo-terminal) — this is a special
 * Linux kernel feature that acts like a fake keyboard+screen pair. One end
 * (the "master") is held by our Kotlin app. The other end (the "slave") is
 * handed to the shell process (proot + Alpine's /bin/sh) as its stdin,
 * stdout, and stderr.
 *
 * When the user types in our app's UI, we write bytes to the master side,
 * and the shell reads them like real keyboard input. When the shell prints
 * output, we read it from the master side and draw it on screen.
 *
 * This is exactly the mechanism real terminal emulators (including Termux)
 * use. There is no shortcut around it — you cannot run an interactive shell
 * from Android without a PTY.
 */

#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define LOG_TAG "VironixNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/*
 * createSubprocess()
 *
 * Opens a new PTY pair, forks a child process, attaches the child to the
 * PTY's "slave" side, and then exec()s the command the caller asked for
 * (which will be: proot ... /bin/sh, set up by our Kotlin code).
 *
 * Returns the PTY "master" file descriptor to Java, which Kotlin then
 * wraps in a FileInputStream/FileOutputStream to talk to the shell.
 */
JNIEXPORT jint JNICALL
Java_com_vironix_app_TerminalSession_createSubprocess(
        JNIEnv *env,
        jclass clazz,
        jstring j_cmd,
        jstring j_cwd,
        jobjectArray j_args,
        jobjectArray j_env,
        jintArray j_process_id,
        jint rows,
        jint cols) {

    int master_fd = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (master_fd < 0) {
        LOGE("Could not open /dev/ptmx: %s", strerror(errno));
        return -1;
    }

    // Standard dance to set up the slave side of the pty:
    // unlock it, then grant permissions, then find its device path.
    if (grantpt(master_fd) != 0 || unlockpt(master_fd) != 0) {
        LOGE("grantpt/unlockpt failed: %s", strerror(errno));
        close(master_fd);
        return -1;
    }

    char slave_path[256];
    if (ptsname_r(master_fd, slave_path, sizeof(slave_path)) != 0) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(master_fd);
        return -1;
    }

    // Set the initial terminal size (rows/cols) so text wraps correctly.
    struct winsize sz = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols };
    ioctl(master_fd, TIOCSWINSZ, &sz);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        close(master_fd);
        return -1;
    }

    if (pid == 0) {
        // --- CHILD PROCESS: becomes the shell ---
        setsid(); // Start a new session so this process can own the terminal.

        int slave_fd = open(slave_path, O_RDWR);
        if (slave_fd < 0) {
            LOGE("Child could not open slave pty: %s", strerror(errno));
            _exit(1);
        }

        ioctl(slave_fd, TIOCSCTTY, 0);

        // Redirect the child's stdin/stdout/stderr to the pty slave.
        dup2(slave_fd, 0);
        dup2(slave_fd, 1);
        dup2(slave_fd, 2);
        if (slave_fd > 2) close(slave_fd);
        close(master_fd);

        const char *cwd = (*env)->GetStringUTFChars(env, j_cwd, NULL);
        if (cwd && *cwd) chdir(cwd);

        const char *cmd = (*env)->GetStringUTFChars(env, j_cmd, NULL);

        int argc = (*env)->GetArrayLength(env, j_args);
        char **argv = calloc((size_t) argc + 1, sizeof(char *));
        for (int i = 0; i < argc; i++) {
            jstring arg = (jstring) (*env)->GetObjectArrayElement(env, j_args, i);
            const char *arg_str = (*env)->GetStringUTFChars(env, arg, NULL);
            argv[i] = strdup(arg_str);
            (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        }

        int envc = (*env)->GetArrayLength(env, j_env);
        char **envp = calloc((size_t) envc + 1, sizeof(char *));
        for (int i = 0; i < envc; i++) {
            jstring e = (jstring) (*env)->GetObjectArrayElement(env, j_env, i);
            const char *e_str = (*env)->GetStringUTFChars(env, e, NULL);
            envp[i] = strdup(e_str);
            (*env)->ReleaseStringUTFChars(env, e, e_str);
        }

        execve(cmd, argv, envp);

        // If we get here, exec failed.
        LOGE("execve(%s) failed: %s", cmd, strerror(errno));
        _exit(1);
    }

    // --- PARENT PROCESS (our app): return control to Kotlin ---
    jint *pid_arr = (*env)->GetIntArrayElements(env, j_process_id, NULL);
    pid_arr[0] = pid;
    (*env)->ReleaseIntArrayElements(env, j_process_id, pid_arr, 0);

    return master_fd;
}

/*
 * setWindowSize() - called whenever the on-screen terminal view is
 * resized (rotation, keyboard opening, etc.) so the shell's idea of
 * "how wide is my screen" stays correct.
 */
JNIEXPORT void JNICALL
Java_com_vironix_app_TerminalSession_setWindowSize(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    struct winsize sz = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols };
    ioctl(fd, TIOCSWINSZ, &sz);
}

/*
 * waitFor() - blocks until the child shell process exits, then returns
 * its exit code. Called from a background thread in Kotlin.
 */
JNIEXPORT jint JNICALL
Java_com_vironix_app_TerminalSession_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    int status;
    waitpid((pid_t) pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}

/*
 * close() - closes the master pty file descriptor, e.g. when the
 * terminal screen is destroyed.
 */
JNIEXPORT void JNICALL
Java_com_vironix_app_TerminalSession_closeFd(JNIEnv *env, jclass clazz, jint fd) {
    close(fd);
}
