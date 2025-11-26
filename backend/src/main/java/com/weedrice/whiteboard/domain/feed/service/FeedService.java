package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private final UserFeedRepository userFeedRepository;
    private final UserRepository userRepository;

    public Page<UserFeed> getUserFeeds(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable);
    }

    @Transactional
    public void generateFeeds() {
        // TODO: 피드 생성 로직 구현
        // 1. 구독한 게시판의 새 글
        // 2. 관심 태그의 새 글
        // 3. 팔로우하는 사용자의 활동 (글 작성, 댓글 등)
        // 4. 이 로직은 비동기 배치 작업으로 처리하는 것이 적합
    }
}
