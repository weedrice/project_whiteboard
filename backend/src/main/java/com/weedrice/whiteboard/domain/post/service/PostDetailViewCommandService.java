package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDetailViewCommandService {

    private final PostRepository postRepository;
    private final ViewHistoryCommandService viewHistoryCommandService;
    private final PostDetailContextResolver postDetailContextResolver;
    private final PostViewCountWriter postViewCountWriter;

    @Transactional
    public int recordReadableView(@NonNull Long postId, Long userId) {
        return recordReadableView(postDetailContextResolver.resolve(postId, userId));
    }

    @Transactional
    int recordReadableView(@NonNull PostDetailContext context) {
        Long postId = context.post().getPostId();
        postViewCountWriter.incrementReadablePostViewCount(postId);

        if (context.readContext().viewer() != null) {
            viewHistoryCommandService.touchView(context.readContext().viewer(), context.post());
        }

        return getReadablePostViewCount(postId);
    }

    private int getReadablePostViewCount(Long postId) {
        Integer viewCount = postRepository.findViewCountByPostId(postId);
        if (viewCount == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return viewCount;
    }

}
