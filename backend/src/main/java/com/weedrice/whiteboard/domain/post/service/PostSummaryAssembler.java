package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class PostSummaryAssembler {

    private static final int FEED_EXCERPT_MAX_LENGTH = 800;

    private final FileService fileService;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final CommentRepository commentRepository;
    private final BoardAccessPolicy boardAccessPolicy;

    Page<PostSummary> assembleBoardPage(Page<Post> posts, Pageable pageable, boolean includeImages,
            boolean includeInquiryAnswered) {
        List<Long> postIds = posts.getContent().stream()
                .map(Post::getPostId)
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = includeImages ? getPostIdsWithImages(postIds) : Collections.emptySet();
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
        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

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
        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);
        UserInteractionContext interactionContext = resolveUserInteractionContext(posts, currentUserId);

        return posts.stream()
                .map(post -> buildFeedSummary(post, postIdsWithImages, interactionContext, FeedSummaryOptions.trending()))
                .collect(Collectors.toList());
    }

    List<PostSummary> assembleLatestPosts(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);
        UserInteractionContext interactionContext = resolveUserInteractionContext(posts, currentUserId);

        return posts.stream()
                .map(post -> buildFeedSummary(post, postIdsWithImages, interactionContext, FeedSummaryOptions.latest()))
                .collect(Collectors.toList());
    }

    private PostSummary buildFeedSummary(Post post, Set<Long> postIdsWithImages, UserInteractionContext interactionContext,
                                         FeedSummaryOptions options) {
        String summaryText = extractSummary(post);
        ThumbnailInfo thumbnailInfo = resolveThumbnail(post, postIdsWithImages);

        String firstMediaType = null;
        String firstMediaUrl = null;
        String contentsExcerpt = null;
        if (options.includeContentsExcerpt()) {
            contentsExcerpt = truncateHtmlForExcerpt(post.getContents(), FEED_EXCERPT_MAX_LENGTH);
        }
        if (options.includeFirstMedia()) {
            String firstVideoUrl = extractFirstVideoEmbedFromContent(post.getContents());
            int imgPos = indexOfFirstImageInContent(post.getContents());
            int videoPos = indexOfFirstVideoInContent(post.getContents());
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

    private UserInteractionContext resolveUserInteractionContext(List<Post> posts, Long currentUserId) {
        if (currentUserId == null || posts.isEmpty()) {
            return UserInteractionContext.empty();
        }

        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null) {
            return UserInteractionContext.empty();
        }

        List<Board> boards = posts.stream().map(Post::getBoard).distinct().collect(Collectors.toList());
        Set<Long> likedPostIds = postLikeRepository.findByUserAndPostIn(user, posts).stream()
                .map(like -> like.getPost().getPostId())
                .collect(Collectors.toSet());
        Set<Long> scrappedPostIds = scrapRepository.findByUserAndPostIn(user, posts).stream()
                .map(scrap -> scrap.getPost().getPostId())
                .collect(Collectors.toSet());
        Set<String> subscribedBoardUrls = boardSubscriptionRepository.findByUserAndBoardIn(user, boards).stream()
                .map(subscription -> subscription.getBoard().getBoardUrl())
                .collect(Collectors.toSet());

        return new UserInteractionContext(likedPostIds, scrappedPostIds, subscribedBoardUrls);
    }

    private Set<Long> getPostIdsWithImages(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(fileService.getRelatedIdsWithImages(postIds, "POST_CONTENT"));
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

        Map<Long, Long> latestAuthorIdsByPostId = commentRepository.findLatestNonDeletedAuthorsByPostIds(
                        inquiryPosts.stream().map(Post::getPostId).toList())
                .stream()
                .collect(Collectors.toMap(
                        CommentRepository.LatestCommentAuthorProjection::getPostId,
                        CommentRepository.LatestCommentAuthorProjection::getAuthorUserId));

        Map<Long, Boolean> inquiryAnsweredStatuses = new HashMap<>();
        for (Post inquiryPost : inquiryPosts) {
            Long latestAuthorId = latestAuthorIdsByPostId.get(inquiryPost.getPostId());
            inquiryAnsweredStatuses.put(
                    inquiryPost.getPostId(),
                    latestAuthorId != null && !Objects.equals(latestAuthorId, inquiryPost.getUser().getUserId()));
        }
        return inquiryAnsweredStatuses;
    }

    private String extractSummary(Post post) {
        String summary = post.getContents().replaceAll("<[^>]*>", "").trim();
        if (summary.length() > 1000) {
            return summary.substring(0, 1000);
        }
        return summary;
    }

    private ThumbnailInfo resolveThumbnail(Post post, Set<Long> postIdsWithImages) {
        String thumbnailUrl = null;
        boolean hasImage = false;

        if (postIdsWithImages.contains(post.getPostId())) {
            Long fileId = fileService.getOneImageFileIdForPost(post.getPostId());
            if (fileId != null) {
                thumbnailUrl = "/api/v1/files/" + fileId;
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

        return new ThumbnailInfo(thumbnailUrl, hasImage);
    }

    private String truncateHtmlForExcerpt(String content, int maxLen) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        content = content.trim();
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

    private String extractFirstVideoEmbedFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile(
                "<iframe[^>]+src\\s*=\\s*[\"']([^\"']*(?:youtube\\.com/embed|vimeo\\.com)[^\"']*)[\"']",
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

    private int indexOfFirstImageInContent(String content) {
        return content == null ? -1 : content.toLowerCase().indexOf("<img");
    }

    private int indexOfFirstVideoInContent(String content) {
        return content == null ? -1 : content.toLowerCase().indexOf("<iframe");
    }

    private String extractFirstImageUrlFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record UserInteractionContext(Set<Long> likedPostIds, Set<Long> scrappedPostIds,
                                          Set<String> subscribedBoardUrls) {
        private static UserInteractionContext empty() {
            return new UserInteractionContext(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        }
    }

    private record ThumbnailInfo(String thumbnailUrl, boolean hasImage) {
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
