package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class FeedPostSummaryAssembler {

    private static final int FEED_EXCERPT_MAX_LENGTH = 800;

    private final FileService fileService;
    private final PostInteractionContextResolver interactionContextResolver;
    private final PostContentSummaryExtractor contentSummaryExtractor;

    List<FeedPostSummary> assembleTrendingPosts(List<Post> posts, Long currentUserId) {
        return assembleFeedPosts(posts, currentUserId, true, true);
    }

    List<FeedPostSummary> assembleLatestPosts(List<Post> posts, Long currentUserId) {
        return assembleFeedPosts(posts, currentUserId, false, false);
    }

    private List<FeedPostSummary> assembleFeedPosts(List<Post> posts, Long currentUserId,
            boolean includeContentsExcerpt, boolean includeFirstMedia) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        Map<Long, Long> thumbnailFileIdsByPostId = getThumbnailFileIdsByPostId(postIds);
        Set<Long> postIdsWithImages = thumbnailFileIdsByPostId.keySet();
        PostUserInteractionContext interactionContext = interactionContextResolver.resolve(posts, currentUserId);

        return posts.stream()
                .map(post -> buildFeedSummary(post, postIdsWithImages, thumbnailFileIdsByPostId, interactionContext,
                        includeContentsExcerpt, includeFirstMedia))
                .collect(Collectors.toList());
    }

    private FeedPostSummary buildFeedSummary(Post post, Set<Long> postIdsWithImages,
            Map<Long, Long> thumbnailFileIdsByPostId, PostUserInteractionContext interactionContext,
            boolean includeContentsExcerpt, boolean includeFirstMedia) {
        String summaryText = contentSummaryExtractor.extractSummary(post);
        PostThumbnailInfo thumbnailInfo = contentSummaryExtractor.resolveThumbnail(
                post,
                postIdsWithImages,
                thumbnailFileIdsByPostId);

        String firstMediaType = null;
        String firstMediaUrl = null;
        if (includeFirstMedia) {
            String firstVideoUrl = contentSummaryExtractor.extractFirstVideoEmbedFromContent(post.getContents());
            int imgPos = contentSummaryExtractor.indexOfFirstImageInContent(post.getContents());
            int videoPos = contentSummaryExtractor.indexOfFirstVideoInContent(post.getContents());
            if (imgPos >= 0 && (videoPos < 0 || imgPos < videoPos)) {
                firstMediaType = "image";
                firstMediaUrl = thumbnailInfo.thumbnailUrl();
            } else if (videoPos >= 0) {
                firstMediaType = "video";
                firstMediaUrl = firstVideoUrl;
            } else if (thumbnailInfo.thumbnailUrl() != null) {
                firstMediaType = "image";
                firstMediaUrl = thumbnailInfo.thumbnailUrl();
            }
        }

        return FeedPostSummary.builder()
                .postId(post.getPostId())
                .boardId(post.getBoard().getBoardId())
                .categoryId(post.getCategory() != null ? post.getCategory().getCategoryId() : null)
                .title(post.getTitle())
                .author(FeedPostSummary.AuthorInfo.builder()
                        .userId(post.getUser().getUserId())
                        .agentId(post.getAgent() != null ? post.getAgent().getAgentId() : null)
                        .authorType(post.getAgent() != null ? "AGENT" : "USER")
                        .displayName(post.getAgent() != null ? post.getAgent().getName()
                                : post.getUser().getDisplayName())
                        .profileImageUrl(post.getAgent() != null ? null : post.getUser().getProfileImageUrl())
                        .build())
                .category(post.getCategory() != null ? FeedPostSummary.CategoryInfo.builder()
                        .categoryId(post.getCategory().getCategoryId())
                        .name(post.getCategory().getName())
                        .build() : null)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isNotice(post.getIsNotice())
                .isNsfw(post.getIsNsfw())
                .isSpoiler(post.getIsSpoiler())
                .isSecret(post.getIsSecret())
                .createdAt(post.getCreatedAt())
                .boardUrl(post.getBoard().getBoardUrl())
                .boardName(post.getBoard().getBoardName())
                .thumbnailUrl(thumbnailInfo.thumbnailUrl())
                .boardIconUrl(post.getBoard().getIconUrl())
                .authorName(post.getAgent() != null ? post.getAgent().getName() : post.getUser().getDisplayName())
                .liked(interactionContext.likedPostIds().contains(post.getPostId()))
                .scrapped(interactionContext.scrappedPostIds().contains(post.getPostId()))
                .subscribed(interactionContext.subscribedBoardUrls().contains(post.getBoard().getBoardUrl()))
                .hasImage(thumbnailInfo.hasImage())
                .summary(summaryText)
                .contentsExcerpt(includeContentsExcerpt
                        ? contentSummaryExtractor.truncateHtmlForExcerpt(post.getContents(), FEED_EXCERPT_MAX_LENGTH)
                        : null)
                .firstMediaType(firstMediaType)
                .firstMediaUrl(firstMediaUrl)
                .build();
    }

    private Map<Long, Long> getThumbnailFileIdsByPostId(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return fileService.getFirstImageFileIdsForPosts(postIds);
    }
}
