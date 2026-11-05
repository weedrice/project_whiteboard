package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.semantic.SemanticRelatedPostService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticRelatedPostResult;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PostRelatedReadService {

    private static final int DEFAULT_RELATED_SIZE = 5;
    private static final int MAX_RELATED_SIZE = 10;

    private final PostRepository postRepository;
    private final PostReadContextResolver postReadContextResolver;
    private final PostAccessPolicy postAccessPolicy;
    private final BoardAccessPolicy boardAccessPolicy;
    private final SemanticRelatedPostService semanticRelatedPostService;
    private final PostFacadeReadService postFacadeReadService;

    public List<PostSummary> getRelatedPosts(Long postId, Long currentUserId, Integer size) {
        int normalizedSize = normalizeSize(size);
        Post sourcePost = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        PostReadContext context = postReadContextResolver.withAdminBoardIdsForPosts(
                postReadContextResolver.resolve(currentUserId),
                List.of(sourcePost));
        postAccessPolicy.validateReadable(sourcePost, context.viewer(), context.isAuthorBlocked(sourcePost),
                context.activeAdminBoardIds());

        Board sourceBoard = sourcePost.getBoard();
        if (!Boolean.TRUE.equals(sourceBoard.getIsActive())
                || !Boolean.TRUE.equals(sourceBoard.getIsPublic())
                || boardAccessPolicy.isInquiryBoard(sourceBoard)) {
            return List.of();
        }

        SemanticRelatedPostResult semanticResult = semanticRelatedPostService.findRelatedPostIds(
                sourcePost.getPostId(),
                sourceBoard.getBoardUrl(),
                currentUserId,
                normalizedSize);
        List<Long> relatedPostIds = semanticResult.postIds();
        if (!semanticResult.sourceEmbeddingAvailable()) {
            relatedPostIds = postRepository.findPublicRelatedFallbackPostIds(
                    sourceBoard.getBoardId(),
                    sourcePost.getPostId(),
                    context.blockedUserIds() == null || context.blockedUserIds().isEmpty()
                            ? List.of(-1L) : context.blockedUserIds(),
                    PageRequest.of(0, normalizedSize));
        }

        if (relatedPostIds.isEmpty()) {
            return List.of();
        }

        Map<Long, PostSummary> summariesById = postFacadeReadService.getPostSummariesByIds(relatedPostIds,
                currentUserId);
        return relatedPostIds.stream()
                .map(summariesById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_RELATED_SIZE;
        }
        return PageRequestUtils.of(0, Math.min(size, MAX_RELATED_SIZE)).getPageSize();
    }
}
