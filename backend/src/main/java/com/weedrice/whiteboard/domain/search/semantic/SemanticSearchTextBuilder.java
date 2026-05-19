package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Component
class SemanticSearchTextBuilder {
    private static final int MAX_EMBEDDING_TEXT_LENGTH = 8_000;
    private static final int MAX_EXCERPT_LENGTH = 240;

    String buildPostText(Post post) {
        String text = joinParts(
                "post",
                post.getBoard() != null ? post.getBoard().getBoardName() : null,
                post.getTitle(),
                toPlainText(post.getContents()));
        return truncate(text, MAX_EMBEDDING_TEXT_LENGTH);
    }

    String buildCommentText(Comment comment) {
        Post post = comment.getPost();
        String text = joinParts(
                "comment",
                post != null && post.getBoard() != null ? post.getBoard().getBoardName() : null,
                post != null ? post.getTitle() : null,
                toPlainText(comment.getContent()));
        return truncate(text, MAX_EMBEDDING_TEXT_LENGTH);
    }

    String excerpt(String source) {
        return truncate(toPlainText(source), MAX_EXCERPT_LENGTH);
    }

    String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Objects.toString(text, "").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private String joinParts(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private String toPlainText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }
}
