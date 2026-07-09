package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminInquirySummaryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostListSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            PostSummary summary = PostSummary.from(post, contentSummaryExtractor.extractSummary(post));
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
        boolean isAscending = isAscendingRowNumberSort(pageable.getSort());

        List<PostSummary> summaries = new ArrayList<>();
        for (int i = 0; i < posts.getContent().size(); i++) {
            Post post = posts.getContent().get(i);
            PostSummary summary = PostSummary.from(post, contentSummaryExtractor.extractSummary(post));
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

    Page<PostSummary> assembleBoardListProjectionPage(Page<PostListSummaryProjection> posts, Pageable pageable,
            boolean includeImages, boolean includeInquiryAnswered) {
        List<Long> postIds = posts.getContent().stream()
                .map(PostListSummaryProjection::postId)
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = includeImages
                ? new HashSet<>(getThumbnailFileIdsByPostId(postIds).keySet())
                : Collections.emptySet();
        Map<Long, Boolean> inquiryAnsweredStatuses = includeInquiryAnswered
                ? resolveInquiryAnsweredStatusesFromProjection(posts.getContent())
                : Collections.emptyMap();

        long totalElements = posts.getTotalElements();
        int pageNumber = posts.getNumber();
        int pageSize = posts.getSize();
        boolean isAscending = isAscendingRowNumberSort(pageable.getSort());

        List<PostSummary> summaries = new ArrayList<>();
        for (int i = 0; i < posts.getContent().size(); i++) {
            PostListSummaryProjection post = posts.getContent().get(i);
            PostSummary summary = buildListSummary(post);
            if (includeImages) {
                summary.setHasImage(postIdsWithImages.contains(post.postId()));
            }
            if (includeInquiryAnswered) {
                summary.setInquiryAnswered(inquiryAnsweredStatuses.get(post.postId()));
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

    private PostSummary buildListSummary(PostListSummaryProjection post) {
        boolean agentAuthored = post.agentId() != null;
        return PostSummary.builder()
                .postId(post.postId())
                .boardId(post.boardId())
                .categoryId(post.categoryId())
                .title(post.title())
                .author(PostSummary.AuthorInfo.builder()
                        .userId(post.userId())
                        .agentId(post.agentId())
                        .authorType(agentAuthored ? "AGENT" : "USER")
                        .displayName(agentAuthored ? post.agentName() : post.userDisplayName())
                        .profileImageUrl(agentAuthored ? null : post.userProfileImageUrl())
                        .build())
                .category(post.categoryId() != null ? PostSummary.CategoryInfo.builder()
                        .categoryId(post.categoryId())
                        .name(post.categoryName())
                        .build() : null)
                .viewCount(post.viewCount() != null ? post.viewCount() : 0)
                .likeCount(post.likeCount() != null ? post.likeCount() : 0)
                .commentCount(post.commentCount() != null ? post.commentCount() : 0)
                .isNotice(Boolean.TRUE.equals(post.isNotice()))
                .isNsfw(Boolean.TRUE.equals(post.isNsfw()))
                .isSpoiler(Boolean.TRUE.equals(post.isSpoiler()))
                .isSecret(Boolean.TRUE.equals(post.isSecret()))
                .pinnedAt(post.pinnedAt())
                .createdAt(post.createdAt())
                .boardUrl(post.boardUrl())
                .boardName(post.boardName())
                .boardIconUrl(post.boardIconUrl())
                .summary(contentSummaryExtractor.extractSummary(post.contentPreview()))
                .build();
    }

    private boolean isAscendingRowNumberSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return false;
        }
        for (Sort.Order order : sort) {
            if ("createdAt".equals(order.getProperty()) || "postId".equals(order.getProperty())) {
                return order.isAscending();
            }
        }
        return false;
    }

    Page<PostSummary> assembleHistoryPage(Page<ViewHistory> historyPage) {
        List<Long> postIds = historyPage.getContent().stream()
                .map(history -> history.getPost().getPostId())
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = new HashSet<>(getThumbnailFileIdsByPostId(postIds).keySet());

        return historyPage.map(viewHistory -> {
            PostSummary summary = PostSummary.from(
                    viewHistory.getPost(),
                    contentSummaryExtractor.extractSummary(viewHistory.getPost()));
            summary.setHasImage(postIdsWithImages.contains(viewHistory.getPost().getPostId()));
            return summary;
        });
    }

    Page<AdminInquirySummaryResponse> assembleAdminInquiryPage(Page<Post> posts) {
        Map<Long, Boolean> inquiryAnsweredStatuses = resolveInquiryAnsweredStatuses(posts.getContent());
        return posts.map(post -> AdminInquirySummaryResponse.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .summary(contentSummaryExtractor.extractSummary(post))
                .author(AdminInquirySummaryResponse.AuthorInfo.builder()
                        .userId(post.getUser().getUserId())
                        .agentId(post.getAgent() != null ? post.getAgent().getAgentId() : null)
                        .authorType(post.getAgent() != null ? "AGENT" : "USER")
                        .displayName(post.getAgent() != null ? post.getAgent().getName()
                                : post.getUser().getDisplayName())
                        .profileImageUrl(post.getAgent() != null ? null : post.getUser().getProfileImageUrl())
                        .build())
                .createdAt(post.getCreatedAt())
                .inquiryAnswered(inquiryAnsweredStatuses.getOrDefault(post.getPostId(), false))
                .build());
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
                .map(post -> buildInteractionSummary(post, postIdsWithImages, thumbnailFileIdsByPostId,
                        interactionContext))
                .collect(Collectors.toList());
    }

    List<PostSummary> assembleLatestPosts(List<Post> posts, Long currentUserId) {
        return assembleLatestPosts(posts, currentUserId, false);
    }

    List<PostSummary> assembleLatestPostsForExistingUser(List<Post> posts, Long currentUserId) {
        return assembleLatestPosts(posts, currentUserId, true);
    }

    private List<PostSummary> assembleLatestPosts(List<Post> posts, Long currentUserId, boolean existingUser) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        Map<Long, Long> thumbnailFileIdsByPostId = getThumbnailFileIdsByPostId(postIds);
        Set<Long> postIdsWithImages = thumbnailFileIdsByPostId.keySet();
        PostUserInteractionContext interactionContext = existingUser
                ? interactionContextResolver.resolveForExistingUser(posts, currentUserId)
                : interactionContextResolver.resolve(posts, currentUserId);

        return posts.stream()
                .map(post -> buildInteractionSummary(post, postIdsWithImages, thumbnailFileIdsByPostId,
                        interactionContext))
                .collect(Collectors.toList());
    }

    private PostSummary buildInteractionSummary(Post post, Set<Long> postIdsWithImages,
                                                Map<Long, Long> thumbnailFileIdsByPostId,
                                                PostUserInteractionContext interactionContext) {
        String summaryText = contentSummaryExtractor.extractSummary(post);
        PostThumbnailInfo thumbnailInfo = contentSummaryExtractor.resolveThumbnail(
                post,
                postIdsWithImages,
                thumbnailFileIdsByPostId);

        return PostSummary.from(
                post,
                thumbnailInfo.thumbnailUrl(),
                post.getBoard().getIconUrl(),
                interactionContext.likedPostIds().contains(post.getPostId()),
                interactionContext.scrappedPostIds().contains(post.getPostId()),
                interactionContext.subscribedBoardUrls().contains(post.getBoard().getBoardUrl()),
                thumbnailInfo.hasImage(),
                summaryText);
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

    private Map<Long, Boolean> resolveInquiryAnsweredStatusesFromProjection(List<PostListSummaryProjection> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyMap();
        }

        List<PostListSummaryProjection> inquiryPosts = posts.stream()
                .filter(post -> boardAccessPolicy.isInquiryBoardUrl(post.boardUrl()))
                .toList();
        if (inquiryPosts.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> answeredPostIds = new HashSet<>(commentRepository.findPostIdsWithNonAuthorCommentsByPostIds(
                inquiryPosts.stream().map(PostListSummaryProjection::postId).toList()));

        Map<Long, Boolean> inquiryAnsweredStatuses = new HashMap<>();
        for (PostListSummaryProjection inquiryPost : inquiryPosts) {
            inquiryAnsweredStatuses.put(
                    inquiryPost.postId(),
                    answeredPostIds.contains(inquiryPost.postId()));
        }
        return inquiryAnsweredStatuses;
    }

}
