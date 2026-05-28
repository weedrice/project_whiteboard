package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
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
public class PostDetailViewCommandService {

    private final PostRepository postRepository;
    private final ViewHistoryCommandService viewHistoryCommandService;
    private final PostReadContextResolver postReadContextResolver;
    private final PostAccessPolicy postAccessPolicy;
    private final PostViewCountWriter postViewCountWriter;

    @Transactional
    public int recordReadableView(@NonNull Long postId, Long userId) {
        PostReadContext readContext = postReadContextResolver.resolveForExistingUser(userId);
        Post post = findPost(postId);
        readContext = postReadContextResolver.withAdminBoardIds(readContext, List.of(post.getBoard()));
        validateReadable(post, readContext);

        postViewCountWriter.incrementReadablePostViewCount(postId);

        if (readContext.viewer() != null) {
            viewHistoryCommandService.touchView(readContext.viewer(), post);
        }

        return getReadablePostViewCount(postId);
    }

    private Post findPost(@NonNull Long postId) {
        return postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private int getReadablePostViewCount(Long postId) {
        Integer viewCount = postRepository.findViewCountByPostId(postId);
        if (viewCount == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return viewCount;
    }

    private void validateReadable(Post post, PostReadContext context) {
        postAccessPolicy.validateReadable(
                post,
                context.viewer(),
                context.isAuthorBlocked(post),
                context.activeAdminBoardIds());
    }
}
