package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.support.PostContentCodec;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PostContentSummaryExtractor {

    private static final String DEFAULT_FRONTEND_URL = "https://noviis.kr";
    private static final String DEFAULT_EXTERNAL_THUMBNAIL_HOSTS = "www.noviis.kr,cdn.noviis.kr";
    private static final Pattern IMAGE_SRC_PATTERN = Pattern.compile(
            "<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private final URI frontendOrigin;
    private final Set<String> allowedExternalThumbnailHosts;

    public PostContentSummaryExtractor() {
        this(DEFAULT_FRONTEND_URL, DEFAULT_EXTERNAL_THUMBNAIL_HOSTS);
    }

    @Autowired
    public PostContentSummaryExtractor(
            @Value("${app.frontend-url:https://noviis.kr}") String frontendUrl,
            @Value("${app.thumbnail.allowed-external-hosts:www.noviis.kr,cdn.noviis.kr}")
            String allowedExternalThumbnailHosts) {
        this.frontendOrigin = parseFrontendOrigin(frontendUrl);
        this.allowedExternalThumbnailHosts = parseAllowedHosts(allowedExternalThumbnailHosts);
    }

    String extractSummary(Post post) {
        return extractSummary(post.getContents());
    }

    String extractSummary(String contents) {
        String summary = PostContentCodec.toPlainText(contents);
        if (summary.length() > 1000) {
            return summary.substring(0, 1000);
        }
        return summary;
    }

    PostThumbnailInfo resolveThumbnail(Post post, Set<Long> postIdsWithImages, Map<Long, Long> thumbnailFileIdsByPostId) {
        return resolveThumbnail(post.getPostId(), post.getContents(), postIdsWithImages, thumbnailFileIdsByPostId);
    }

    PostThumbnailInfo resolveThumbnail(Long postId, String contents, Set<Long> postIdsWithImages,
            Map<Long, Long> thumbnailFileIdsByPostId) {
        String thumbnailUrl = null;
        boolean hasImage = false;

        if (postIdsWithImages.contains(postId)) {
            Long fileId = thumbnailFileIdsByPostId.get(postId);
            if (fileId != null) {
                thumbnailUrl = FileUrlResolver.resolveThumbnail(fileId);
                hasImage = true;
            }
        }

        if (thumbnailUrl == null) {
            String contentImageUrl = extractFirstAllowedImageUrlFromContent(contents);
            if (contentImageUrl != null) {
                thumbnailUrl = contentImageUrl;
                hasImage = true;
            }
        }

        return new PostThumbnailInfo(thumbnailUrl, hasImage);
    }

    String truncateHtmlForExcerpt(String content, int maxLen) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        content = PostContentCodec.expandPreservedHtml(content).trim();
        if (content.length() <= maxLen) {
            return content;
        }
        String cut = content.substring(0, maxLen);
        int lastClose = cut.lastIndexOf('>');
        int lastTag = cut.lastIndexOf('<');
        if (lastClose > lastTag && lastClose >= 0) {
            return cut.substring(0, lastClose + 1);
        }
        return cut;
    }

    String extractFirstVideoEmbedFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        content = PostContentCodec.expandPreservedHtml(content);
        Pattern pattern = Pattern.compile(
                "<iframe[^>]+src\\s*=\\s*[\"']([^\"']*(?:youtube(?:-nocookie)?\\.com/embed|vimeo\\.com)[^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1)
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'");
        }
        return null;
    }

    int indexOfFirstImageInContent(String content) {
        return content == null ? -1 : PostContentCodec.expandPreservedHtml(content).toLowerCase().indexOf("<img");
    }

    int indexOfFirstAllowedImageInContent(String content) {
        if (content == null || content.isEmpty()) {
            return -1;
        }
        Matcher matcher = IMAGE_SRC_PATTERN.matcher(PostContentCodec.expandPreservedHtml(content));
        while (matcher.find()) {
            if (isAllowedThumbnailUrl(matcher.group(1))) {
                return matcher.start();
            }
        }
        return -1;
    }

    int indexOfFirstVideoInContent(String content) {
        return content == null ? -1 : PostContentCodec.expandPreservedHtml(content).toLowerCase().indexOf("<iframe");
    }

    String extractFirstImageUrlFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        content = PostContentCodec.expandPreservedHtml(content);
        Matcher matcher = IMAGE_SRC_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractFirstAllowedImageUrlFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Matcher matcher = IMAGE_SRC_PATTERN.matcher(PostContentCodec.expandPreservedHtml(content));
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (isAllowedThumbnailUrl(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isAllowedThumbnailUrl(String source) {
        if (source == null || source.isBlank() || source.startsWith("//")) {
            return false;
        }
        try {
            URI uri = URI.create(source);
            if (!uri.isAbsolute()) {
                return source.startsWith("/") && uri.getRawPath() != null;
            }
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null) {
                return false;
            }
            if (hasSameOrigin(uri, frontendOrigin)) {
                return true;
            }
            return "https".equalsIgnoreCase(uri.getScheme())
                    && normalizePort(uri) == 443
                    && allowedExternalThumbnailHosts.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static URI parseFrontendOrigin(String frontendUrl) {
        URI uri = URI.create(frontendUrl);
        if (uri.getScheme() == null
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("app.frontend-url must be an absolute HTTP(S) URL");
        }
        return uri;
    }

    private static Set<String> parseAllowedHosts(String hosts) {
        if (hosts == null || hosts.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(hosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasSameOrigin(URI candidate, URI expected) {
        return candidate.getScheme().equalsIgnoreCase(expected.getScheme())
                && candidate.getHost().equalsIgnoreCase(expected.getHost())
                && normalizePort(candidate) == normalizePort(expected);
    }

    private static int normalizePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
