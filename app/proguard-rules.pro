# ProGuard rules
# Keep native method signatures intact so JNI calls from termux-pty.c
# can still find their matching Kotlin/Java methods after minification.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.vironix.app.TerminalSession { *; }
