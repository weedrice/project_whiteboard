package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @InjectMocks
    private FeedService feedService;

    @Mock
    private UserFeedRepository userFeedRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostService postService;

    @Mock
    private FeedGenerationService feedGenerationService;

    @Test
    @DisplayName("POST 피드는 게시글 요약을 hydrate 하면서 페이지 순서를 유지한다")
    void getUserFeeds_hydratesPostsInPageOrder() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        UserFeed firstFeed = createFeed(1L, user, "SUBSCRIPTION_POST", "POST", 101L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now());
        UserFeed secondFeed = createFeed(2L, user, "SUBSCRIPTION_POST", "POST", 202L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now().minusMinutes(1));
        Page<UserFeed> feedPage = new PageImpl<>(List.of(firstFeed, secondFeed), pageable, 2);

        PostSummary secondPost = PostSummary.builder().postId(202L).title("second").build();
        PostSummary firstPost = PostSummary.builder().postId(101L).title("first").build();
        Map<Long, PostSummary> postSummaries = new LinkedHashMap<>();
        postSummaries.put(202L, secondPost);
        postSummaries.put(101L, firstPost);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable)).thenReturn(feedPage);
        when(postService.getPostSummariesByIds(List.of(101L, 202L), userId)).thenReturn(postSummaries);

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getContentId()).isEqualTo(101L);
        assertThat(response.getContent().get(0).getPost()).isEqualTo(firstPost);
        assertThat(response.getContent().get(1).getContentId()).isEqualTo(202L);
        assertThat(response.getContent().get(1).getPost()).isEqualTo(secondPost);
    }

    @Test
    @DisplayName("미지원 피드 타입은 post 를 null 로 유지한다")
    void getUserFeeds_keepsUnsupportedTypePostNull() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        UserFeed feed = createFeed(1L, user, "BOARD_NOTICE", "NOTICE", 100L, "BOARD", 1L, LocalDateTime.now());
        Page<UserFeed> feedPage = new PageImpl<>(List.of(feed), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable)).thenReturn(feedPage);

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPost()).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 POST 피드는 응답에서 제외한다")
    void getUserFeeds_filtersInvalidPostFeeds() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        UserFeed invalidFeed = createFeed(1L, user, "SUBSCRIPTION_POST", "POST", 999L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now());
        UserFeed validFeed = createFeed(2L, user, "SUBSCRIPTION_POST", "POST", 101L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now().minusMinutes(1));
        Page<UserFeed> feedPage = new PageImpl<>(List.of(invalidFeed, validFeed), pageable, 2);

        PostSummary validPost = PostSummary.builder().postId(101L).title("first").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable))
                .thenReturn(feedPage);
        when(postService.getPostSummariesByIds(List.of(999L, 101L), userId)).thenReturn(Map.of(101L, validPost));

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getContentId()).isEqualTo(101L);
        assertThat(response.getContent().get(0).getPost()).isEqualTo(validPost);
    }

    @Test
    @DisplayName("피드 사용자가 없으면 USER_NOT_FOUND 를 반환한다")
    void getUserFeeds_userNotFound() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedService.getUserFeeds(userId, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("generateFeeds 는 생성 전용 서비스로 위임한다")
    void generateFeeds_delegatesToGenerationService() {
        feedService.generateFeeds();

        verify(feedGenerationService).generateFeeds();
    }

    private UserFeed createFeed(Long feedId, User user, String feedType, String contentType, Long contentId,
                                String sourceCriteria, Long criteriaId, LocalDateTime createdAt) {
        UserFeed feed = UserFeed.builder()
                .targetUser(user)
                .feedType(feedType)
                .contentType(contentType)
                .contentId(contentId)
                .sourceCriteria(sourceCriteria)
                .criteriaId(criteriaId)
                .build();
        ReflectionTestUtils.setField(feed, "feedId", feedId);
        ReflectionTestUtils.setField(feed, "createdAt", createdAt);
        ReflectionTestUtils.setField(feed, "isRead", false);
        return feed;
    }
}
