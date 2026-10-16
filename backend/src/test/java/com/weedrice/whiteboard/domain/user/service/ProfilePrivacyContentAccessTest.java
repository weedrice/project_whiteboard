package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.badge.repository.BadgeRepository;
import com.weedrice.whiteboard.domain.badge.repository.UserBadgeRepository;
import com.weedrice.whiteboard.domain.badge.service.BadgeService;
import com.weedrice.whiteboard.domain.comment.integration.CommentUserIntegrationAdapter;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentQueryService;
import com.weedrice.whiteboard.domain.post.integration.PostUserReadIntegrationAdapter;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostListReadService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfilePrivacyContentAccessTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final UserBlockService blockService = mock(UserBlockService.class);
    private final ProfilePrivacyPolicy privacy = new ProfilePrivacyPolicy(settingsRepository);
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final BadgeRepository badges = mock(BadgeRepository.class);
    private final UserBadgeRepository userBadges = mock(UserBadgeRepository.class);
    private PostListReadService postService;
    private CommentQueryService commentService;
    private BadgeService badgeService;

    @BeforeEach
    void setUp() {
        User user = User.builder().displayName("Private").build();
        ReflectionTestUtils.setField(user, "userId", 2L);
        UserSettings settings = UserSettings.builder().user(user).build();
        settings.updateProfilePrivacy(false, false, false);
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(2L, User.STATUS_ACTIVE)).thenReturn(Optional.of(user));
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));
        postService = new PostListReadService(posts, null,
                new PostUserReadIntegrationAdapter(privacy, users, blockService),
                null, null, null, null, null, null, null, null);
        commentService = new CommentQueryService(comments, null, null,
                new CommentUserIntegrationAdapter(privacy, new UserReadableResolver(users), blockService, blocks),
                null, null, null, null, null);
        badgeService = new BadgeService(privacy, blocks, badges, userBadges, users, null, null, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 1L)
    void privateListsDenyAnonymousAndOtherViewersBeforeFetchingContent(Long viewer) {
        assertPrivate(() -> postService.getPublicProfilePosts(2L, viewer, PageRequest.of(0, 10)));
        assertPrivate(() -> commentService.getPublicProfileComments(2L, viewer, PageRequest.of(0, 10)));
        assertPrivate(() -> badgeService.getUserBadges(2L, viewer));
        verifyNoInteractions(posts, comments, badges, userBadges);
    }

    @Test
    void blockRestrictionsTakePrecedenceOverPrivacy() {
        when(blockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);
        when(blocks.existsEitherDirection(1L, 2L)).thenReturn(true);
        assertThat(postService.getPublicProfilePosts(2L, 1L, PageRequest.of(0, 10))).isEmpty();
        assertThat(commentService.getPublicProfileComments(2L, 1L, PageRequest.of(0, 10))).isEmpty();
        assertThat(badgeService.getUserBadges(2L, 1L)).isEmpty();
        verifyNoInteractions(posts, comments, badges, userBadges);
    }

    @Test
    void ownerCanReadPrivateBadges() {
        when(badges.findAllByOrderBySortOrderAscBadgeCodeAsc()).thenReturn(List.of());
        when(userBadges.findByUser_UserIdOrderByBadge_SortOrderAscBadge_BadgeCodeAsc(2L)).thenReturn(List.of());
        assertThatCode(() -> badgeService.getUserBadges(2L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void missingUserRemainsNotFoundBeforePrivacy() {
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(2L, User.STATUS_ACTIVE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> postService.getPublicProfilePosts(2L, null, PageRequest.of(0, 10)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        assertThatThrownBy(() -> commentService.getPublicProfileComments(2L, null, PageRequest.of(0, 10)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        assertThatThrownBy(() -> badgeService.getUserBadges(2L, null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    private void assertPrivate(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFILE_SECTION_PRIVATE);
    }
}
