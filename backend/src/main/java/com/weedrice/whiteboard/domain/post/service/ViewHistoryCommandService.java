package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
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

    private ViewHistory insertAndLoad(User user, Post post) {
        viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId());
        return viewHistoryRepository.findByUserAndPost(user, post)
                .orElseThrow(() -> new IllegalStateException("View history must exist after insert."));
    }
}
