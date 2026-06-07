package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileExtensionUtilsTest {

    @Test
    void extractLowerCaseExtensionReturnsDotExtensionUsingRootLocale() {
        assertThat(FileExtensionUtils.extractLowerCaseExtension("photo.JPG"))
                .isEqualTo(".jpg");
    }

    @Test
    void extractLowerCaseExtensionUsesLastDot() {
        assertThat(FileExtensionUtils.extractLowerCaseExtension("archive.preview.PNG"))
                .isEqualTo(".png");
    }

    @Test
    void extractLowerCaseExtensionReturnsEmptyStringForMissingFilenameOrExtension() {
        assertThat(FileExtensionUtils.extractLowerCaseExtension(null)).isEmpty();
        assertThat(FileExtensionUtils.extractLowerCaseExtension("filename")).isEmpty();
    }
}
