package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.PostVersionResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostFacadeReadService {

    private static final String POST_CONTENT_FILE_TYPE = "POST_CONTENT";
    private static final String FILE_API_PREFIX = "/api/v1/files/";

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagAssignmentService tagAssignmentService;
    private final FileService fileService;
    private final UserBlockService userBlockService;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostInteractionService postInteractionService;
    private final PostAccessPolicy postAccessPolicy;
    private final BoardAccessPolicy boardAccessPolicy;
    private final AdminRepository adminRepository;

    public PostResponse getInquiryPostResponseForAdmin(@NonNull Long postId) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!boardAccessPolicy.isInquiryBoard(post.getBoard())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        List<String> tags = tagAssignmentService.getTagNames(post);
        List<String> imageUrls = getPostImageUrls(postId);
        return PostResponse.from(post, tags, null, false, false, imageUrls, true);
    }

    public List<PostVersionResponse> getPostVersions(@NonNull Long postId, @NonNull Long userId) {
        User viewer = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postInteractionService.getPostById(postId, userId, false);

        boolean isAuthor = post.getUser().getUserId().equals(userId);
        if (!isAuthor && !boardAccessPolicy.hasBoardAdminAccess(post.getBoard(), viewer)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<PostVersion> versions = postVersionRepository.findByPost_PostIdOrderByCreatedAtDesc(postId);
        return PostVersionResponse.from(versions);
    }

    public List<String> getTagsForPost(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return tagAssignmentService.getTagNames(post);
    }

    public List<String> getPostImageUrls(@NonNull Long postId) {
        return fileService.getFilesByRelatedEntity(postId, POST_CONTENT_FILE_TYPE).stream()
                .filter(file -> file.getMimeType().startsWith("image/"))
                .map(file -> FILE_API_PREFIX + file.getFileId())
                .collect(Collectors.toList());
    }

    public Set<Long> getPostIdsWithImages(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(fileService.getRelatedIdsWithImages(postIds, POST_CONTENT_FILE_TYPE));
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, Long currentUserId) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        User viewer = resolveViewer(currentUserId);
        Set<Long> blockedUserIds = resolveBlockedUserIds(currentUserId);
        List<Long> distinctPostIds = postIds.stream().distinct().toList();
        List<Post> posts = postRepository.findByPostIdInAndIsDeletedFalse(distinctPostIds);
        Set<Long> activeAdminBoardIds = resolveActiveAdminBoardIds(viewer, posts);

        Map<Long, Post> postsById = posts.stream()
                .filter(post -> canReadPostSummary(post, viewer, blockedUserIds, activeAdminBoardIds))
                .collect(Collectors.toMap(Post::getPostId, post -> post));

        List<Post> orderedPosts = postIds.stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        if (orderedPosts.isEmpty()) {
            return Collections.emptyMap();
        }

        return postSummaryAssembler.assembleLatestPosts(orderedPosts, currentUserId).stream()
                .collect(Collectors.toMap(PostSummary::getPostId, summary -> summary, (left, right) -> left,
                        LinkedHashMap::new));
    }

    private User resolveViewer(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Set<Long> resolveBlockedUserIds(Long currentUserId) {
        if (currentUserId == null) {
            return Collections.emptySet();
        }

        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(blockedUserIds);
    }

    private Set<Long> resolveActiveAdminBoardIds(User viewer, List<Post> posts) {
        if (viewer == null || posts == null || posts.isEmpty() || Boolean.TRUE.equals(viewer.getIsSuperAdmin())) {
            return Collections.emptySet();
        }

        List<Long> boardIds = posts.stream()
                .filter(this::requiresAdminAccessForSummary)
                .map(Post::getBoard)
                .filter(Objects::nonNull)
                .filter(board -> board.getCreator() == null
                        || !Objects.equals(board.getCreator().getUserId(), viewer.getUserId()))
                .map(Board::getBoardId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (boardIds.isEmpty()) {
            return Collections.emptySet();
        }

        return adminRepository.findByUserAndBoard_BoardIdInAndIsActive(viewer, boardIds, true).stream()
                .map(Admin::getBoard)
                .filter(Objects::nonNull)
                .map(Board::getBoardId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean requiresAdminAccessForSummary(Post post) {
        Board board = post.getBoard();
        return board != null
                && (!Boolean.TRUE.equals(board.getIsActive())
                || !Boolean.TRUE.equals(board.getIsPublic())
                || Boolean.TRUE.equals(post.getIsSecret()));
    }

    private boolean canReadPostSummary(Post post, User viewer, Set<Long> blockedUserIds, Set<Long> activeAdminBoardIds) {
        boolean authorBlocked = viewer != null && blockedUserIds.contains(post.getUser().getUserId());
        try {
            postAccessPolicy.validateReadable(post, viewer, authorBlocked, activeAdminBoardIds);
            return true;
        } catch (BusinessException ex) {
            if (ErrorCode.POST_NOT_FOUND.equals(ex.getErrorCode())) {
                return false;
            }
            throw ex;
        }
    }
}
