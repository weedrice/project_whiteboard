package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PostDetailContextResolver {

    private final PostRepository postRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final PostReadContextResolver postReadContextResolver;
    private final PostAccessPolicy postAccessPolicy;

    PostDetailContext resolve(@NonNull Long postId, Long userId) {
        PostReadContext readContext = postReadContextResolver.resolveForExistingUser(userId);
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        readContext = postReadContextResolver.withAdminBoardIds(readContext, List.of(post.getBoard()));
        postAccessPolicy.validateReadable(
                post,
                readContext.viewer(),
                readContext.isAuthorBlocked(post),
                readContext.activeAdminBoardIds());

        ViewHistory viewHistory = null;
        if (readContext.viewer() != null) {
            viewHistory = viewHistoryRepository.findByUserAndPost(readContext.viewer(), post).orElse(null);
        }

        return new PostDetailContext(post, readContext, viewHistory);
    }
}
