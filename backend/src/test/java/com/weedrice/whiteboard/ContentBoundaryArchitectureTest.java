package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBoundaryArchitectureTest {

    private static final Set<String> BOUNDED_DOMAINS = Set.of("post", "comment", "user", "agent");
    private static final Pattern CROSS_IMPORT = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?com\\.weedrice\\.whiteboard\\.domain\\."
                    + "(post|comment|user|agent)\\.(entity|repository|service)(?:\\.|;)");

    @Test
    void contentCoreImportsOnlyItsOwnDomainOrExplicitBoundaryContracts() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/weedrice/whiteboard/domain");
        List<String> violations = new ArrayList<>();

        for (String ownerDomain : BOUNDED_DOMAINS) {
            Path domainRoot = sourceRoot.resolve(ownerDomain);
            try (var paths = Files.walk(domainRoot)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(this::isCoreSource)
                        .forEach(path -> collectViolations(sourceRoot, ownerDomain, path, violations));
            }
        }

        assertThat(violations)
                .as("cross-domain entity/repository/service imports outside integration and explicit transition list")
                .isEmpty();
    }

    private boolean isCoreSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return !normalized.contains("/integration/")
                && (normalized.contains("/entity/")
                || normalized.contains("/repository/")
                || normalized.contains("/service/"));
    }

    private void collectViolations(
            Path sourceRoot, String ownerDomain, Path path, List<String> violations) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = CROSS_IMPORT.matcher(source);
            while (matcher.find()) {
                String importedDomain = matcher.group(1);
                if (!ownerDomain.equals(importedDomain)) {
                    violations.add(sourceRoot.relativize(path).toString().replace('\\', '/')
                            + " -> " + matcher.group().trim());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read source as UTF-8: " + path, exception);
        }
    }
}
