package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PostSummaryAssembler {

    private static final int FEED_EXCERPT_MAX_LENGTH = 800;

    private final FileService fileService;
    private final CommentRepository commentRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostInteractionContextResolver interactionContextResolver;
    private final PostContentSummaryExtractor contentSummaryExtractor;

    public PostSummaryAssembler(FileService fileService,
                                CommentRepository commentRepository,
                                BoardAccessPolicy boardAccessPolicy,
                                PostInteractionContextResolver interactionContextResolver,
                                PostContentSummaryExtractor contentSummaryExtractor) {
        this.fileService = fileService;
        this.commentRepository = commentRepository;
        this.boardAccessPolicy = boardAccessPolicy;
        this.interactionContextResolver = interactionContextResolver;
        this.contentSummaryExtractor = contentSummaryExtractor;
    }

    Page<PostSummary> assembleBoardPage(Page<Post> posts, Pageable pageable, boolean includeImages,
                                        boolean includeInquiryAnswered) {
        return assemblePage(posts, pageable, includeImages, includeInquiryAnswered);
    }

    public Page<PostSummary> assembleSearchPage(Page<Post> posts) {
        return assemblePage(posts, posts.getPageable(), true, false);
    }

    Page<PostSummary> assembleTagPage(Page<Post> posts) {
        List<Long> postIds = posts.getContent().stream()
                .map(Post::getPostId)
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = new HashSet<>(getThumbnailFileIdsByPostId(postIds).keySet());

        return posts.map(post -> {
            PostSummary summary = PostSummary.from(post);
            summary.setHasImage(postIdsWithImages.contains(post.getPostId()));
            return summary;
        });
    }

    private Page<PostSummary> assemblePage(Page<Post> posts, Pageable pageable, boolean includeImages,
                                           boolean includeInquiryAnswered) {
        List<Long> postIds = posts.getContent().stream()
                .map(Post::getPostId)
                .collect(Collectors.toList());
        Map<Long, Long> thumbnailFileIdsByPostId = includeImages
                ? getThumbnailFileIdsByPostId(postIds)
                : Collections.emptyMap();
        Set<Long> postIdsWithImages = includeImages
                ? new HashSet<>(thumbnailFileIdsByPostId.keySet())
                : Collections.emptySet();
        Map<Long, Boolean> inquiryAnsweredStatuses = includeInquiryAnswered
                ? resolveInquiryAnsweredStatuses(posts.getContent())
                : Collections.emptyMap();

        long totalElements = posts.getTotalElements();
        int pageNumber = posts.getNumber();
        int pageSize = posts.getSize();
        boolean isAscending = pageable.getSort().stream()
                .anyMatch(order -> order.getProperty().equals("createdAt") && order.isAscending()
                        || order.getProperty().equals("postId") && order.isAscending());

        List<PostSummary> summaries = new ArrayList<>();
        for (int i = 0; i < posts.getContent().size(); i++) {
            Post post = posts.getContent().get(i);
            PostSummary summary = PostSummary.from(post);
            if (includeImages) {
                summary.setHasImage(postIdsWithImages.contains(post.getPostId()));
            }
            if (includeInquiryAnswered) {
                summary.setInquiryAnswered(inquiryAnsweredStatuses.get(post.getPostId()));
            }

            if (isAscending) {
                summary.setRowNum(((long) pageNumber * pageSize) + i + 1);
            } else {
                summary.setRowNum(totalElements - ((long) pageNumber * pageSize) - i);
            }
            summaries.add(summary);
        }

        return new PageImpl<>(summaries, pageable, totalElements);
    }

    Page<PostSummary> assembleHistoryPage(Page<ViewHistory> historyPage) {
        List<Long> postIds = historyPage.getContent().stream()
                .map(history -> history.getPost().getPostId())
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = new HashSet<>(getThumbnailFileIdsByPostId(postIds).keySet());

        return historyPage.map(viewHistory -> {
            PostSummary summary = PostSummary.from(viewHistory.getPost());
            summary.setHasImage(postIdsWithImages.contains(viewHistory.getPost().getPostId()));
            return summary;
        });
    }

    List<PostSummary> assembleTrendingPosts(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        Map<Long, Long> thumbnailFileIdsByPostId = getThumbnailFileIdsByPostId(postIds);
        Set<Long> postIdsWithImages = thumbnailFileIdsByPostId.keySet();
        PostUserInteractionContext interactionContext = interactionContextResolver.resolve(posts, currentUserId);

        return posts.stream()
                .map(post -> buildFeedSummary(post, postIdsWithImages, thumbnailFileIdsByPostId, interactionContext,
                        FeedSummaryOptions.trending()))
                .collect(Collectors.toList());
    }

    List<PostSummary> assembleLatestPosts(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        Map<Long, Long> thumbnailFileIdsByPostId = getThumbnailFileIdsByPostId(postIds);
        Set<Long> postIdsWithImages = thumbnailFileIdsByPostId.keySet();
        PostUserInteractionContext interactionContext = interactionContextResolver.resolve(posts, currentUserId);

        return posts.stream()
                .map(post -> buildFeedSummary(post, postIdsWithImages, thumbnailFileIdsByPostId, interactionContext,
                        FeedSummaryOptions.latest()))
                .collect(Collectors.toList());
    }

    private PostSummary buildFeedSummary(Post post, Set<Long> postIdsWithImages,
                                         Map<Long, Long> thumbnailFileIdsByPostId,
                                         PostUserInteractionContext interactionContext,
                                         FeedSummaryOptions options) {
        String summaryText = contentSummaryExtractor.extractSummary(post);
        PostThumbnailInfo thumbnailInfo = contentSummaryExtractor.resolveThumbnail(
                post,
                postIdsWithImages,
                thumbnailFileIdsByPostId);

        String firstMediaType = null;
        String firstMediaUrl = null;
        String contentsExcerpt = null;
        if (options.includeContentsExcerpt()) {
            contentsExcerpt = contentSummaryExtractor.truncateHtmlForExcerpt(post.getContents(), FEED_EXCERPT_MAX_LENGTH);
        }
        if (options.includeFirstMedia()) {
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

        return PostSummary.from(
                post,
                thumbnailInfo.thumbnailUrl(),
                post.getBoard().getIconUrl(),
                interactionContext.likedPostIds().contains(post.getPostId()),
                interactionContext.scrappedPostIds().contains(post.getPostId()),
                interactionContext.subscribedBoardUrls().contains(post.getBoard().getBoardUrl()),
                thumbnailInfo.hasImage(),
                summaryText,
                contentsExcerpt,
                firstMediaType,
                firstMediaUrl);
    }

    private Map<Long, Long> getThumbnailFileIdsByPostId(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return fileService.getFirstImageFileIdsForPosts(postIds);
    }

    private Map<Long, Boolean> resolveInquiryAnsweredStatuses(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Post> inquiryPosts = posts.stream()
                .filter(post -> boardAccessPolicy.isInquiryBoard(post.getBoard()))
                .toList();
        if (inquiryPosts.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> answeredPostIds = new HashSet<>(commentRepository.findPostIdsWithNonAuthorCommentsByPostIds(
                inquiryPosts.stream().map(Post::getPostId).toList()));

        Map<Long, Boolean> inquiryAnsweredStatuses = new HashMap<>();
        for (Post inquiryPost : inquiryPosts) {
            inquiryAnsweredStatuses.put(
                    inquiryPost.getPostId(),
                    answeredPostIds.contains(inquiryPost.getPostId()));
        }
        return inquiryAnsweredStatuses;
    }

    private record FeedSummaryOptions(boolean includeContentsExcerpt, boolean includeFirstMedia) {
        private static FeedSummaryOptions trending() {
            return new FeedSummaryOptions(true, true);
        }

        private static FeedSummaryOptions latest() {
            return new FeedSummaryOptions(false, false);
        }
    }
}
