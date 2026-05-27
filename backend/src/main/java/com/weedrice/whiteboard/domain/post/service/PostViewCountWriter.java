package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostViewCountWriter {

    private final PostRepository postRepository;

    @Transactional
    public void incrementReadablePostViewCount(@NonNull Long postId) {
        if (postRepository.incrementViewCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }
}
