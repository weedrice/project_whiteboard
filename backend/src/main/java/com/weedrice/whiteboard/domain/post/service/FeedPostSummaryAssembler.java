package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummaryFields;
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
        PostSummaryFields fields = PostSummaryFields.from(post, post.getBoard().getIconUrl());

        String firstMediaType = null;
        String firstMediaUrl = null;
        if (includeFirstMedia) {
            String firstVideoUrl = contentSummaryExtractor.extractFirstVideoEmbedFromContent(post.getContents());
            int imgPos = contentSummaryExtractor.indexOfFirstAllowedImageInContent(post.getContents());
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
                .postId(fields.postId())
                .boardId(fields.boardId())
                .categoryId(fields.categoryId())
                .title(fields.title())
                .author(FeedPostSummary.AuthorInfo.builder()
                        .userId(fields.author().userId())
                        .agentId(fields.author().agentId())
                        .authorType(fields.author().authorType())
                        .displayName(fields.author().displayName())
                        .profileImageUrl(fields.author().profileImageUrl())
                        .build())
                .category(fields.category() != null ? FeedPostSummary.CategoryInfo.builder()
                        .categoryId(fields.category().categoryId())
                        .name(fields.category().name())
                        .build() : null)
                .viewCount(fields.counts().viewCount())
                .likeCount(fields.counts().likeCount())
                .commentCount(fields.counts().commentCount())
                .isNotice(fields.flags().isNotice())
                .isNsfw(fields.flags().isNsfw())
                .isSpoiler(fields.flags().isSpoiler())
                .isSecret(fields.flags().isSecret())
                .createdAt(fields.createdAt())
                .boardUrl(fields.boardUrl())
                .boardName(fields.boardName())
                .thumbnailUrl(thumbnailInfo.thumbnailUrl())
                .boardIconUrl(fields.boardIconUrl())
                .authorName(fields.authorName())
                .liked(interactionContext.likedPostIds().contains(fields.postId()))
                .scrapped(interactionContext.scrappedPostIds().contains(fields.postId()))
                .subscribed(interactionContext.subscribedBoardUrls().contains(fields.boardUrl()))
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
