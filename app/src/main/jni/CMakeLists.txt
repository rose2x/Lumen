# CMakeLists.txt
# Tells the Android NDK how to compile our native (C) terminal code
# into a shared library (.so) that Kotlin can call into via JNI.

cmake_minimum_required(VERSION 3.22.1)
project("vironix")

# Builds libvironix.so from termux-pty.c.
# This library's only job is: create a pseudo-terminal (PTY) pair and
# fork/exec a process (proot -> alpine shell) attached to it, so Kotlin
# can read/write to that process like a terminal.
add_library(
        vironix
        SHARED
        termux-pty.c
)

find_library(log-lib log)

target_link_libraries(
        vironix
        ${log-lib}
)
