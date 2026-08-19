package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.support.PostContentCodec;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.global.util.VideoEmbedUrlPolicy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PostContentSummaryExtractor {

    private static final String DEFAULT_FRONTEND_URL = "https://noviis.kr";
    private static final String DEFAULT_EXTERNAL_THUMBNAIL_HOSTS = "noviis.kr,www.noviis.kr,cdn.noviis.kr";
    private final URI frontendOrigin;
    private final Set<String> allowedExternalThumbnailHosts;

    @Autowired
    public PostContentSummaryExtractor(
            @Value("${app.frontend-url:" + DEFAULT_FRONTEND_URL + "}") String frontendUrl,
            @Value("${app.thumbnail.allowed-external-hosts:}")
            String allowedExternalThumbnailHosts) {
        this.frontendOrigin = parseFrontendOrigin(frontendUrl);
        this.allowedExternalThumbnailHosts = parseAllowedHosts(
                allowedExternalThumbnailHosts == null || allowedExternalThumbnailHosts.isBlank()
                        ? DEFAULT_EXTERNAL_THUMBNAIL_HOSTS
                        : allowedExternalThumbnailHosts);
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
        Document document = parseContent(content);
        if (document == null) {
            return null;
        }
        for (Element iframe : document.select("iframe[src]")) {
            String source = iframe.attr("src");
            if (VideoEmbedUrlPolicy.isAllowed(source)) {
                return source;
            }
        }
        return null;
    }

    String extractFirstImageUrlFromContent(String content) {
        Document document = parseContent(content);
        Element image = document == null ? null : document.selectFirst("img[src]");
        return image == null ? null : image.attr("src");
    }

    PostMediaCandidate extractFirstAllowedMediaFromContent(String content) {
        Document document = parseContent(content);
        if (document == null) {
            return null;
        }
        for (Element media : document.select("img[src], iframe[src]")) {
            String source = media.attr("src");
            if (media.normalName().equals("img") && isAllowedThumbnailUrl(source)) {
                return new PostMediaCandidate(PostMediaCandidate.Type.IMAGE, source);
            }
            if (media.normalName().equals("iframe") && VideoEmbedUrlPolicy.isAllowed(source)) {
                return new PostMediaCandidate(PostMediaCandidate.Type.VIDEO, source);
            }
        }
        return null;
    }

    private String extractFirstAllowedImageUrlFromContent(String content) {
        Document document = parseContent(content);
        if (document == null) {
            return null;
        }
        for (Element image : document.select("img[src]")) {
            String candidate = image.attr("src");
            if (isAllowedThumbnailUrl(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Document parseContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return Jsoup.parseBodyFragment(PostContentCodec.expandPreservedHtml(content));
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
