package com.weedrice.whiteboard.domain.comment.integration;

import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.port.CommentPostPort;
import com.weedrice.whiteboard.domain.comment.port.CommentPostSnapshot;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentPostIntegrationAdapter implements CommentPostPort {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final PostAccessPolicy postAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final InquiryLegacyWritePolicy inquiryLegacyWritePolicy;
    private final UserReadableResolver userReadableResolver;
    private final UserWritableResolver userWritableResolver;
    private final UserBlockService userBlockService;

    @Override
    public CommentPostSnapshot getRequired(Long postId) {
        return snapshot(load(postId));
    }

    @Override
    public Map<Long, CommentPostSnapshot> getAll(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getPostId, this::snapshot, (left, right) -> left));
    }

    @Override
    @Transactional
    public CommentPostSnapshot lockForWrite(Long postId) {
        Post initial = load(postId);
        Long boardId = initial.getBoard().getBoardId();
        boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Post locked = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!Objects.equals(boardId, locked.getBoard().getBoardId())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return snapshot(locked);
    }

    @Override
    public void validateReadable(Long postId, Long viewerUserId, Collection<Long> knownBlockedUserIds) {
        Post post = load(postId);
        User viewer = viewerUserId == null ? null : userReadableResolver.resolve(viewerUserId);
        Set<Long> blockedUserIds = viewer == null ? Set.of()
                : knownBlockedUserIds != null ? Set.copyOf(knownBlockedUserIds)
                : Set.copyOf(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(viewerUserId));
        postAccessPolicy.validateReadable(post, viewer, blockedUserIds.contains(post.getUserId()));
    }

    @Override
    public void validateWritable(Long postId, Long writerUserId) {
        Post post = load(postId);
        User writer = userWritableResolver.resolve(writerUserId);
        inquiryLegacyWritePolicy.requireBoardWritable(post.getBoard());
        postAuthorCommandPolicy.validateWritableCommand(post, writer, post.getCategory());
    }

    @Override
    @Transactional
    public void incrementCommentCount(Long postId) {
        if (postRepository.incrementCommentCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void decrementCommentCount(Long postId) {
        if (postRepository.decrementCommentCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private Post load(Long postId) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.getBoard() == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private CommentPostSnapshot snapshot(Post post) {
        return new CommentPostSnapshot(
                post.getPostId(), post.getBoard().getBoardId(), post.getBoard().getBoardUrl(),
                post.getBoard().getBoardName(),
                post.getCategory() != null ? post.getCategory().getCategoryId() : null,
                post.getTitle(), post.getUserId(), post.getAgentId(), Boolean.TRUE.equals(post.getIsDeleted()),
                Boolean.TRUE.equals(post.getBoard().getIsActive()),
                Boolean.TRUE.equals(post.getBoard().getIsPublic()));
    }
}
