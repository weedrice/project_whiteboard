package com.weedrice.whiteboard.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostHtmlEditorContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("게시글 HTML sanitizer와 시각 에디터가 공통 미디어·표 계약을 유지한다")
    void sanitizePostHtml_preservesSharedEditorContract() throws IOException {
        JsonNode contract = OBJECT_MAPPER.readTree(Files.readString(resolveContractPath()));

        assertThat(contract.path("version").asInt()).isEqualTo(1);
        for (JsonNode contractCase : contract.path("cases")) {
            String name = contractCase.path("name").asText();
            String sanitized = InputSanitizer.sanitizePostHtml(contractCase.path("html").asText());
            Document document = Jsoup.parseBodyFragment(sanitized);
            Element element = document.selectFirst(contractCase.path("selector").asText());

            assertThat(element).as(name).isNotNull();
            contractCase.path("attributes").properties().forEach(attribute -> {
                String attributeName = attribute.getKey();
                assertThat(normalizeAttributeValue(attributeName, element.attr(attributeName)))
                            .as(name + "[" + attribute.getKey() + "]")
                            .isEqualTo(normalizeAttributeValue(attributeName, attribute.getValue().asText()));
            });
        }
    }

    private String normalizeAttributeValue(String name, String value) {
        if (!"style".equals(name)) {
            return value;
        }
        return String.join("; ", List.of(value.split(";"))
                .stream()
                .map(String::strip)
                .filter(declaration -> !declaration.isEmpty())
                .toList());
    }

    private Path resolveContractPath() {
        return List.of(
                        Path.of("..", "docs", "contracts", "post-html-editor.json"),
                        Path.of("docs", "contracts", "post-html-editor.json"))
                .stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("post HTML editor contract file not found"));
    }
}
