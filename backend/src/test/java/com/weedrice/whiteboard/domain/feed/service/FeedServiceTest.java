package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedVisibilityCondition;
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
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
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

    private static final Sort FEED_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("feedId"));

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

    @Mock
    private AdminRepository adminRepository;

    @Test
    @DisplayName("POST feeds hydrate post summaries in page order")
    void getUserFeeds_hydratesPostsInPageOrder() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 10);

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
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), pageable)).thenReturn(feedPage);
        when(postService.getPostSummariesByIds(List.of(101L, 202L), userId)).thenReturn(postSummaries);

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getContentId()).isEqualTo(101L);
        assertThat(response.getContent().get(0).getPost()).isEqualTo(firstPost);
        assertThat(response.getContent().get(1).getContentId()).isEqualTo(202L);
        assertThat(response.getContent().get(1).getPost()).isEqualTo(secondPost);
        verify(userFeedRepository, never()).findVisibleSliceByTargetUserOrderByCreatedAtDesc(vf(user), pageable);
    }

    @Test
    @DisplayName("POST feed without resolved post summary is excluded from response")
    void getUserFeeds_excludesPostFeedWhenSummaryMissing() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 10);

        UserFeed staleFeed = createFeed(1L, user, "SUBSCRIPTION_POST", "POST", 101L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now());
        UserFeed validFeed = createFeed(2L, user, "SUBSCRIPTION_POST", "POST", 202L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now().minusMinutes(1));
        Page<UserFeed> feedPage = new PageImpl<>(List.of(staleFeed, validFeed), pageable, 2);
        PostSummary validPost = PostSummary.builder().postId(202L).title("valid").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), pageable)).thenReturn(feedPage);
        when(postService.getPostSummariesByIds(List.of(101L, 202L), userId)).thenReturn(Map.of(202L, validPost));

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getContentId()).isEqualTo(202L);
        assertThat(response.getContent().getFirst().getPost()).isEqualTo(validPost);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    @DisplayName("POST feed filtering refills current page from the next physical feed page")
    void getUserFeeds_refillsCurrentPageFromNextPhysicalPage() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 2);
        Pageable nextPageable = feedPageable(1, 2);

        UserFeed staleFeed = createFeed(1L, user, "SUBSCRIPTION_POST", "POST", 101L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now());
        UserFeed validFeed = createFeed(2L, user, "SUBSCRIPTION_POST", "POST", 202L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now().minusMinutes(1));
        UserFeed refillFeed = createFeed(3L, user, "SUBSCRIPTION_POST", "POST", 303L, "BOARD_SUBSCRIPTION", 10L,
                LocalDateTime.now().minusMinutes(2));
        Page<UserFeed> feedPage = new PageImpl<>(List.of(staleFeed, validFeed), pageable, 3);
        SliceImpl<UserFeed> refillPage = new SliceImpl<>(List.of(refillFeed), nextPageable, false);
        PostSummary validPost = PostSummary.builder().postId(202L).title("valid").build();
        PostSummary refillPost = PostSummary.builder().postId(303L).title("refill").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), pageable)).thenReturn(feedPage);
        when(userFeedRepository.findVisibleSliceByTargetUserOrderByCreatedAtDesc(vf(user), nextPageable))
                .thenReturn(refillPage);
        when(postService.getPostSummariesByIds(List.of(101L, 202L), userId)).thenReturn(Map.of(202L, validPost));
        when(postService.getPostSummariesByIds(List.of(303L), userId)).thenReturn(Map.of(303L, refillPost));

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent()).extracting(FeedResponse.FeedSummary::getContentId)
                .containsExactly(202L, 303L);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
        verify(userFeedRepository, never()).findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), nextPageable);
    }

    @Test
    @DisplayName("POST feed refill stops at the additional page lookup limit")
    void getUserFeeds_refillStopsAtAdditionalPageLookupLimit() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        for (int page = 0; page <= 5; page++) {
            UserFeed staleFeed = createFeed(
                    (long) page + 1,
                    user,
                    "SUBSCRIPTION_POST",
                    "POST",
                    100L + page,
                    "BOARD_SUBSCRIPTION",
                    10L,
                    LocalDateTime.now().minusMinutes(page));
            Pageable currentPageable = feedPageable(page, 1);
            if (page == 0) {
                when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), currentPageable))
                        .thenReturn(new PageImpl<>(List.of(staleFeed), currentPageable, 10));
            } else {
                when(userFeedRepository.findVisibleSliceByTargetUserOrderByCreatedAtDesc(vf(user), currentPageable))
                        .thenReturn(new SliceImpl<>(List.of(staleFeed), currentPageable, true));
            }
            when(postService.getPostSummariesByIds(List.of(100L + page), userId)).thenReturn(Map.of());
        }

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(4);
        assertThat(response.isHasNext()).isTrue();
        verify(userFeedRepository, never()).findVisibleSliceByTargetUserOrderByCreatedAtDesc(
                vf(user),
                feedPageable(6, 1));
    }

    @Test
    @DisplayName("Unsupported feed content types keep null post")
    void getUserFeeds_keepsUnsupportedTypePostNull() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 10);

        UserFeed feed = createFeed(1L, user, "BOARD_NOTICE", "NOTICE", 100L, "BOARD", 1L, LocalDateTime.now());
        Page<UserFeed> feedPage = new PageImpl<>(List.of(feed), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), pageable)).thenReturn(feedPage);

        FeedResponse response = feedService.getUserFeeds(userId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPost()).isNull();
    }

    @Test
    @DisplayName("Either-direction blocked POST feeds are excluded before response metadata is calculated")
    void getUserFeeds_usesEitherDirectionBlockedUserIdsForVisibleFeedPage() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 10);

        UserFeed validFeed = createFeed(2L, user, "SUBSCRIPTION_POST", "POST", 101L,
                "BOARD_SUBSCRIPTION", 10L, LocalDateTime.now().minusMinutes(1));
        Page<UserFeed> feedPage = new PageImpl<>(List.of(validFeed), pageable, 1);
        PostSummary validPost = PostSummary.builder().postId(101L).title("first").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of(99L));
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user, List.of(99L)), pageable))
                .thenReturn(feedPage);
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
        verify(userFeedRepository).findVisibleByTargetUserOrderByCreatedAtDesc(vf(user, List.of(99L)), pageable);
    }

    @Test
    @DisplayName("When all POST feeds are invisible the visible page metadata is empty")
    void getUserFeeds_allInvisiblePostFeedsReturnEmptyVisibleMetadata() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 1);
        Page<UserFeed> feedPage = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), pageable)).thenReturn(feedPage);

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
    @DisplayName("active admin board ids are resolved once and passed to feed repository")
    void getUserFeeds_passesActiveAdminBoardIdsToRepository() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable pageable = feedPageable(0, 10);
        UserFeedVisibilityCondition visibilityCondition = vf(user, List.of(), List.of(10L, 20L));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(adminRepository.findActiveBoardIdsByUser(user)).thenReturn(List.of(10L, 20L));
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(visibilityCondition, pageable))
                .thenReturn(Page.empty(pageable));

        feedService.getUserFeeds(userId, pageable);

        verify(adminRepository).findActiveBoardIdsByUser(user);
        verify(userFeedRepository).findVisibleByTargetUserOrderByCreatedAtDesc(visibilityCondition, pageable);
    }

    @Test
    @DisplayName("super admin feed visibility skips active admin board lookup")
    void getUserFeeds_superAdminSkipsActiveAdminBoardLookup() {
        Long userId = 1L;
        User user = User.builder().build();
        user.grantSuperAdminRole();
        Pageable pageable = feedPageable(0, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), pageable))
                .thenReturn(Page.empty(pageable));

        feedService.getUserFeeds(userId, pageable);

        verify(adminRepository, never()).findActiveBoardIdsByUser(user);
    }

    @Test
    @DisplayName("Feed user not found returns USER_NOT_FOUND")
    void getUserFeeds_userNotFound() {
        Long userId = 1L;
        Pageable pageable = feedPageable(0, 10);

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

    @Test
    @DisplayName("Feed pageable sort is fixed to latest order")
    void getUserFeeds_normalizesPageableSort() {
        Long userId = 1L;
        User user = User.builder().build();
        Pageable requestedPageable = PageRequest.of(2, 1000, Sort.by(Sort.Order.asc("feedType")));
        Pageable normalizedPageable = feedPageable(2, 100);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId)).thenReturn(List.of());
        when(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), normalizedPageable))
                .thenReturn(Page.empty(normalizedPageable));

        FeedResponse response = feedService.getUserFeeds(userId, requestedPageable);

        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(100);
        verify(userFeedRepository).findVisibleByTargetUserOrderByCreatedAtDesc(vf(user), normalizedPageable);
    }

    private UserFeedVisibilityCondition vf(User user) {
        return vf(user, List.of());
    }

    private UserFeedVisibilityCondition vf(User user, List<Long> blockedUserIds) {
        return vf(user, blockedUserIds, List.of());
    }

    private UserFeedVisibilityCondition vf(User user, List<Long> blockedUserIds, List<Long> activeAdminBoardIds) {
        return UserFeedVisibilityCondition.of(user, blockedUserIds, activeAdminBoardIds);
    }

    private Pageable feedPageable(int page, int size) {
        return PageRequest.of(page, size, FEED_LIST_SORT);
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
