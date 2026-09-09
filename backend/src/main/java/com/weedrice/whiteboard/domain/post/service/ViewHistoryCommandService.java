package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.actor.UserIdRef;
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
    public ViewHistory getOrCreate(UserIdRef user, Post post) {
        return viewHistoryRepository.findByUserIdAndPost(user.getUserId(), post)
                .orElseGet(() -> insertAndLoad(user, post));
    }

    @Transactional
    public void touchView(UserIdRef user, Post post) {
        touchViewHistory(user, post);
    }

    @Transactional
    public ViewHistory touchAndLoadView(UserIdRef user, Post post) {
        touchViewHistory(user, post);
        return loadViewHistory(user, post);
    }

    private void touchViewHistory(UserIdRef user, Post post) {
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
    public ViewHistory getOrCreateForUpdate(UserIdRef user, Post post) {
        return viewHistoryRepository.findByUserAndPostForUpdate(user.getUserId(), post.getPostId())
                .orElseGet(() -> insertAndLoadForUpdate(user, post));
    }

    private ViewHistory insertAndLoad(UserIdRef user, Post post) {
        viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId());
        return loadViewHistory(user, post);
    }

    private ViewHistory insertAndLoadForUpdate(UserIdRef user, Post post) {
        viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId());
        return viewHistoryRepository.findByUserAndPostForUpdate(user.getUserId(), post.getPostId())
                .orElseThrow(this::viewHistoryUnavailable);
    }

    private ViewHistory loadViewHistory(UserIdRef user, Post post) {
        return viewHistoryRepository.findByUserIdAndPost(user.getUserId(), post)
                .orElseThrow(this::viewHistoryUnavailable);
    }

    private BusinessException viewHistoryUnavailable() {
        return new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
}
