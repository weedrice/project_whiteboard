package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.support.PostContentCodec;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PostContentSummaryExtractor {

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
        String thumbnailUrl = null;
        boolean hasImage = false;

        if (postIdsWithImages.contains(post.getPostId())) {
            Long fileId = thumbnailFileIdsByPostId.get(post.getPostId());
            if (fileId != null) {
                thumbnailUrl = FileUrlResolver.resolveThumbnail(fileId);
                hasImage = true;
            }
        }

        if (thumbnailUrl == null) {
            String contentImageUrl = extractFirstImageUrlFromContent(post.getContents());
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

    int indexOfFirstVideoInContent(String content) {
        return content == null ? -1 : PostContentCodec.expandPreservedHtml(content).toLowerCase().indexOf("<iframe");
    }

    String extractFirstImageUrlFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        content = PostContentCodec.expandPreservedHtml(content);
        Pattern pattern = Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }
}
