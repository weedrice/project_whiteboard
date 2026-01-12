package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private UserFeedRepository userFeedRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FeedService feedService;

    @Test
    @DisplayName("사용자 피드 조회 성공")
    void getUserFeeds_success() {
        // given
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = Pageable.unpaged();
        Page<com.weedrice.whiteboard.domain.feed.entity.UserFeed> feedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable)).thenReturn(feedPage);

        // when
        feedService.getUserFeeds(userId, pageable);

        // then
        verify(userFeedRepository).findByTargetUserOrderByCreatedAtDesc(user, pageable);
    }
}
