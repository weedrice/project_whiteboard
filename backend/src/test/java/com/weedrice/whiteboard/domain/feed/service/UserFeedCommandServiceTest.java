package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserFeedCommandServiceTest {

    @InjectMocks
    private UserFeedCommandService userFeedCommandService;

    @Mock
    private UserFeedRepository userFeedRepository;

    @Test
    @DisplayName("feed insert uses conflict-ignore query")
    void saveFeedIfAbsent_usesInsertIgnore() {
        UserFeed feed = createFeed(1L, 10L);

        userFeedCommandService.saveFeedIfAbsent(feed);

        verify(userFeedRepository).insertIgnore(
                1L,
                "SUBSCRIPTION_POST",
                "POST",
                100L,
                "BOARD_SUBSCRIPTION",
                10L);
    }

    private UserFeed createFeed(Long userId, Long boardId) {
        User user = User.builder().loginId("user").build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return UserFeed.builder()
                .targetUser(user)
                .feedType("SUBSCRIPTION_POST")
                .contentType("POST")
                .contentId(100L)
                .sourceCriteria("BOARD_SUBSCRIPTION")
                .criteriaId(boardId)
                .build();
    }
}
