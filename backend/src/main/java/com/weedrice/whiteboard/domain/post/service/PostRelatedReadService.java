package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.semantic.SemanticRelatedPostService;
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

        List<Long> relatedPostIds = semanticRelatedPostService.findRelatedPostIds(
                sourcePost.getPostId(),
                sourcePost.getBoard().getBoardUrl(),
                currentUserId,
                normalizedSize);
        if (relatedPostIds.isEmpty()) {
            relatedPostIds = postRepository.findPublicRelatedFallbackPostIds(
                    sourcePost.getBoard().getBoardId(),
                    sourcePost.getPostId(),
                    PageRequest.of(0, normalizedSize));
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
