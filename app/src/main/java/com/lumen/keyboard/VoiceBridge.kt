package com.lumen.keyboard

/**
 * The IME service can't request a runtime permission itself (only an
 * Activity can), so [VoicePermissionActivity] does it and reports back
 * here. Safe because the activity and the service run in the same
 * process, in the same app.
 */
object VoiceBridge {
    var onPermissionResult: ((granted: Boolean) -> Unit)? = null
}
