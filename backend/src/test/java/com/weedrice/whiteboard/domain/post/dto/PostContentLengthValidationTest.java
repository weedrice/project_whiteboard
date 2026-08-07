package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.constant.PostContentConstraints;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PostContentLengthValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPreservedHtmlAtTheSourceLimitDespiteBase64Overhead() {
        String prefix = "<style>.card{display:grid}</style>";
        String source = prefix + "한".repeat(PostContentConstraints.MAX_SOURCE_LENGTH - prefix.length());
        String stored = preservedMarker(source);
        PostDraftRequest request = PostDraftRequest.builder().contents(stored).build();

        assertThat(stored.length()).isGreaterThan(PostContentConstraints.MAX_SOURCE_LENGTH);
        assertThat(validator.validateProperty(request, "contents")).isEmpty();
    }

    @Test
    void rejectsPreservedHtmlBeyondTheSourceLimit() {
        String prefix = "<style>.card{display:grid}</style>";
        String source = prefix + "한".repeat(PostContentConstraints.MAX_SOURCE_LENGTH - prefix.length() + 1);
        PostDraftRequest request = PostDraftRequest.builder().contents(preservedMarker(source)).build();

        assertThat(validator.validateProperty(request, "contents")).isNotEmpty();
    }

    @Test
    void validatesMixedMarkersRegardlessOfAttributeOrderAndQuotes() {
        String surroundingHtml = "<p></p>";
        String source = "한".repeat(PostContentConstraints.MAX_SOURCE_LENGTH - surroundingHtml.length());
        String encoded = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
        String stored = surroundingHtml + "<div data-value='" + encoded
                + "' class='extra noviis-sandboxed-post-html'></div>";
        PostDraftRequest request = PostDraftRequest.builder().contents(stored).build();
        PostDraftRequest tooLongRequest = PostDraftRequest.builder().contents(stored + "한").build();

        assertThat(validator.validateProperty(request, "contents")).isEmpty();
        assertThat(validator.validateProperty(tooLongRequest, "contents")).isNotEmpty();
    }

    @Test
    void rejectsRawAndStoredRepresentationsBeyondTheirOwnLimits() {
        PostDraftRequest rawRequest = PostDraftRequest.builder()
                .contents("a".repeat(PostContentConstraints.MAX_SOURCE_LENGTH + 1))
                .build();
        PostDraftRequest storedRequest = PostDraftRequest.builder()
                .contents("a".repeat(PostContentConstraints.MAX_STORED_LENGTH + 1))
                .build();

        assertThat(validator.validateProperty(rawRequest, "contents")).isNotEmpty();
        assertThat(validator.validateProperty(storedRequest, "contents")).isNotEmpty();
    }

    private static String preservedMarker(String source) {
        String encoded = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
        return "<div class=\"noviis-sandboxed-post-html\" data-value=\"" + encoded + "\"></div>";
    }
}
