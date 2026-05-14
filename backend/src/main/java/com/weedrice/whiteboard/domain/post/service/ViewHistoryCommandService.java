package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ViewHistoryCommandService {

    private final ViewHistoryRepository viewHistoryRepository;

    @Transactional
    public ViewHistory getOrCreate(User user, Post post) {
        return viewHistoryRepository.findByUserAndPost(user, post)
                .orElseGet(() -> insertAndLoad(user, post));
    }

    @Transactional
    public void touchView(User user, Post post) {
        int insertedCount = viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId());
        if (insertedCount > 0) {
            return;
        }
        int touchedCount = viewHistoryRepository.touchModifiedAt(user.getUserId(), post.getPostId());
        if (touchedCount == 0) {
            throw viewHistoryUnavailable();
        }
    }

    @Transactional
    public ViewHistory getOrCreateForUpdate(User user, Post post) {
        return viewHistoryRepository.findByUserAndPostForUpdate(user.getUserId(), post.getPostId())
                .orElseGet(() -> insertAndLoadForUpdate(user, post));
    }

    private ViewHistory insertAndLoad(User user, Post post) {
        viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId());
        return viewHistoryRepository.findByUserAndPost(user, post)
                .orElseThrow(this::viewHistoryUnavailable);
    }

    private ViewHistory insertAndLoadForUpdate(User user, Post post) {
        viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId());
        return viewHistoryRepository.findByUserAndPostForUpdate(user.getUserId(), post.getPostId())
                .orElseThrow(this::viewHistoryUnavailable);
    }

    private BusinessException viewHistoryUnavailable() {
        return new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
}
