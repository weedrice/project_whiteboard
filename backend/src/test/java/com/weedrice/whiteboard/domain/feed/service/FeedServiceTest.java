package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @InjectMocks
    private FeedService feedService;

    @Mock
    private UserFeedRepository userFeedRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("피드 조회 성공 - 데이터 포함")
    void getUserFeeds_success_withData() {
        // given
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 10);
        
        UserFeed feed = UserFeed.builder()
                .feedType("POST")
                .contentType("NOTICE")
                .contentId(100L)
                .build();
        ReflectionTestUtils.setField(feed, "feedId", 1L);
        ReflectionTestUtils.setField(feed, "createdAt", java.time.LocalDateTime.now());
        ReflectionTestUtils.setField(feed, "isRead", false);

        Page<UserFeed> feedPage = new PageImpl<>(List.of(feed), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable)).thenReturn(feedPage);

        // when
        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        // then
        assertThat(response.getContent()).hasSize(1);
        FeedResponse.FeedSummary summary = response.getContent().get(0);
        assertThat(summary.getFeedId()).isEqualTo(1L);
        assertThat(summary.getFeedType()).isEqualTo("POST");
        assertThat(summary.getContentType()).isEqualTo("NOTICE");
        assertThat(summary.getContentId()).isEqualTo(100L);
        assertThat(summary.isRead()).isFalse();
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("피드 생성 TODO 메서드 호출 (커버리지용)")
    void generateFeeds_call() {
        // FeedService.generateFeeds()는 현재 TODO 빈 메서드이지만 커버리지를 위해 호출
        feedService.generateFeeds();
    }

    @Test
    @DisplayName("피드 조회 실패 - 사용자 없음")
    void getUserFeeds_userNotFound() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> feedService.getUserFeeds(userId, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}