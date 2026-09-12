package com.lumen.keyboard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Small Java utility for copying picked files (fonts, background images)
 * from a content:// stream into the app's private storage, so they keep
 * working even if the original file/URI becomes unavailable later.
 */
public final class FileCopyUtils {

    private FileCopyUtils() {
        // static utility, no instances
    }

    /**
     * Copies all bytes from {@code input} into {@code destination}, overwriting
     * it if it already exists. The caller is responsible for closing {@code input}.
     *
     * @return true if the copy succeeded.
     */
    public static boolean copyStreamToFile(InputStream input, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        try (OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
