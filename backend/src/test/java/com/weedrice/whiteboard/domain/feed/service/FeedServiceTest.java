package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
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
import static org.mockito.Mockito.never;
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

    @Mock
    private UserBlockService userBlockService;

    @Test
    @DisplayName("POST feeds hydrate post summaries in page order")
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
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(user, List.of(), pageable)).thenReturn(feedPage);
        when(postService.getPostSummariesByIds(List.of(101L, 202L), userId)).thenReturn(postSummaries);

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getContentId()).isEqualTo(101L);
        assertThat(response.getContent().get(0).getPost()).isEqualTo(firstPost);
        assertThat(response.getContent().get(1).getContentId()).isEqualTo(202L);
        assertThat(response.getContent().get(1).getPost()).isEqualTo(secondPost);
    }

    @Test
    @DisplayName("POST feed without resolved post summary is excluded")
    void getUserFeeds_excludesStalePostFeedWhenSummaryMissing() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        UserFeed staleFeed = createFeed(1L, user, "SUBSCRIPTION_POST", "POST", 101L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now());
        UserFeed validFeed = createFeed(2L, user, "SUBSCRIPTION_POST", "POST", 202L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now().minusMinutes(1));
        Page<UserFeed> feedPage = new PageImpl<>(List.of(staleFeed, validFeed), pageable, 2);
        PostSummary validPost = PostSummary.builder().postId(202L).title("valid").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(user, List.of(), pageable)).thenReturn(feedPage);
        when(postService.getPostSummariesByIds(List.of(101L, 202L), userId)).thenReturn(Map.of(202L, validPost));

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getContentId()).isEqualTo(202L);
        assertThat(response.getContent().getFirst().getPost()).isEqualTo(validPost);
        assertThat(response.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Unsupported feed content types keep null post")
    void getUserFeeds_keepsUnsupportedTypePostNull() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        UserFeed feed = createFeed(1L, user, "BOARD_NOTICE", "NOTICE", 100L, "BOARD", 1L, LocalDateTime.now());
        Page<UserFeed> feedPage = new PageImpl<>(List.of(feed), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(user, List.of(), pageable)).thenReturn(feedPage);

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPost()).isNull();
    }

    @Test
    @DisplayName("Invisible POST feeds are excluded before response metadata is calculated")
    void getUserFeeds_usesVisibleFeedPageMetadata() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        UserFeed validFeed = createFeed(2L, user, "SUBSCRIPTION_POST", "POST", 101L,
                "BOARD_SUBSCRIPTION", 10L, LocalDateTime.now().minusMinutes(1));
        Page<UserFeed> feedPage = new PageImpl<>(List.of(validFeed), pageable, 1);
        PostSummary validPost = PostSummary.builder().postId(101L).title("first").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(List.of(99L));
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(user, List.of(99L), pageable)).thenReturn(feedPage);
        when(postService.getPostSummariesByIds(List.of(101L), userId)).thenReturn(Map.of(101L, validPost));

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getContentId()).isEqualTo(101L);
        assertThat(response.getContent().getFirst().getPost()).isEqualTo(validPost);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();
        verify(userFeedRepository, never()).deleteAllInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(userFeedRepository).findVisibleByTargetUserOrderByCreatedAtDesc(user, List.of(99L), pageable);
    }

    @Test
    @DisplayName("When all POST feeds are invisible the visible page metadata is empty")
    void getUserFeeds_allInvisiblePostFeedsReturnEmptyVisibleMetadata() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = PageRequest.of(0, 1);
        Page<UserFeed> feedPage = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(user, List.of(), pageable)).thenReturn(feedPage);

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(1);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();
        verify(userFeedRepository, never()).deleteAllInBatch(org.mockito.ArgumentMatchers.anyList());
        verify(postService, never()).getPostSummariesByIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("Feed user not found returns USER_NOT_FOUND")
    void getUserFeeds_userNotFound() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedService.getUserFeeds(userId, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("generateFeeds delegates to generation service")
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
