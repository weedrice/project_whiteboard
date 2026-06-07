package com.weedrice.whiteboard.global.common.util;

import java.util.Locale;

public final class FileExtensionUtils {

    private FileExtensionUtils() {
    }

    public static String extractLowerCaseExtension(String filename) {
        if (filename == null) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }
}
