package com.weedrice.whiteboard.domain.inquiry;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InquiryArchitectureTest {
    @Test
    void inquiryCoreDoesNotImportBoardPostOrCommentDomains() throws Exception {
        Path inquiryRoot = Path.of("src/main/java/com/weedrice/whiteboard/domain/inquiry");
        try (var files = Files.walk(inquiryRoot)) {
            var violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String normalized = path.toString().replace('\\', '/');
                        return !normalized.contains("/inquiry/legacy/")
                                && !normalized.contains("/inquiry/integration/");
                    })
                    .filter(path -> {
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            return source.contains("domain.board.")
                                    || source.contains("domain.post.")
                                    || source.contains("domain.comment.")
                                    || source.contains("domain.user.")
                                    || source.contains("domain.file.")
                                    || source.contains("domain.notification.");
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .map(Path::toString)
                    .toList();
            assertThat(violations).isEmpty();
        }
    }
}
