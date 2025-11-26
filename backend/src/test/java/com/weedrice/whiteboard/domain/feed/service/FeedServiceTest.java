package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        Pageable pageable = PageRequest.of(0, 10);
        UserFeed feed = UserFeed.builder().targetUser(user).build();
        Page<UserFeed> feedPage = new PageImpl<>(Collections.singletonList(feed), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable)).thenReturn(feedPage);

        // when
        Page<UserFeed> result = feedService.getUserFeeds(userId, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0)).isEqualTo(feed);
    }
}
