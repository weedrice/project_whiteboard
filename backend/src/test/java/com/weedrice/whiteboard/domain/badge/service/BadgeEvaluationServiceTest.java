package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeEvaluationServiceTest {

    @Mock BadgeAwardService awards;
    @Mock PostRepository posts;
    @Mock CommentRepository comments;
    @Mock UserRepository users;
    BadgeEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new BadgeEvaluationService(awards, posts, comments, users);
    }

    @Test
    void evaluatesPostAndCommentThresholds() {
        User user = mock(User.class);
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(1L, User.STATUS_ACTIVE)).thenReturn(Optional.of(user));
        when(posts.countByUserAndIsDeleted(user, false)).thenReturn(100L);
        when(comments.countByUserAndIsDeleted(user, false)).thenReturn(10L);
        when(awards.awardIfMissing(1L, BadgeCode.FIRST_POST)).thenReturn(true);
        when(awards.awardIfMissing(1L, BadgeCode.POSTS_10)).thenReturn(true);
        when(awards.awardIfMissing(1L, BadgeCode.POSTS_100)).thenReturn(false);
        when(awards.awardIfMissing(1L, BadgeCode.FIRST_COMMENT)).thenReturn(true);
        when(awards.awardIfMissing(1L, BadgeCode.COMMENTS_10)).thenReturn(true);

        assertEquals(2, service.evaluatePostCountBadges(1L));
        assertEquals(2, service.evaluateCommentCountBadges(1L));
        verify(awards).awardIfMissing(1L, BadgeCode.POSTS_100);
    }

    @Test
    void inactiveOrNullUsersAwardNothing() {
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(2L, User.STATUS_ACTIVE)).thenReturn(Optional.empty());
        assertEquals(0, service.evaluatePostCountBadges(null));
        assertEquals(0, service.evaluateCommentCountBadges(2L));
    }

    @Test
    void evaluatesAttendanceAndPopularPostBoundaries() {
        when(awards.awardIfMissing(3L, BadgeCode.ATTENDANCE_7)).thenReturn(true);
        when(awards.awardIfMissing(3L, BadgeCode.ATTENDANCE_30)).thenReturn(true);
        when(awards.awardIfMissing(3L, BadgeCode.POPULAR_POST_10)).thenReturn(true);
        when(awards.awardIfMissing(3L, BadgeCode.POPULAR_POST_50)).thenReturn(false);

        assertEquals(2, service.evaluateAttendanceStreakBadges(3L, 30));
        assertEquals(1, service.evaluatePopularPostBadges(3L, 50));
        assertEquals(0, service.evaluateAttendanceStreakBadges(3L, 6));
    }

    @Test
    void evaluatesContentCountsWithOneAggregateQueryPerRepository() {
        User first = mock(User.class);
        User second = mock(User.class);
        PostRepository.UserPostCountProjection firstPostCount = mock(PostRepository.UserPostCountProjection.class);
        CommentRepository.UserCommentCountProjection secondCommentCount = mock(CommentRepository.UserCommentCountProjection.class);
        when(first.isActiveAccount()).thenReturn(true);
        when(first.getUserId()).thenReturn(1L);
        when(second.isActiveAccount()).thenReturn(true);
        when(second.getUserId()).thenReturn(2L);
        when(firstPostCount.getUserId()).thenReturn(1L);
        when(firstPostCount.getPostCount()).thenReturn(10L);
        when(secondCommentCount.getUserId()).thenReturn(2L);
        when(secondCommentCount.getCommentCount()).thenReturn(1L);
        when(posts.countActiveByUserIds(Set.of(1L, 2L))).thenReturn(List.of(firstPostCount));
        when(comments.countActiveByUserIds(Set.of(1L, 2L))).thenReturn(List.of(secondCommentCount));
        when(awards.awardMissingContentBadges(anyMap(), anyMap())).thenReturn(3);

        assertEquals(3, service.evaluateContentCountBadges(List.of(first, second)));

        verify(posts).countActiveByUserIds(Set.of(1L, 2L));
        verify(comments).countActiveByUserIds(Set.of(1L, 2L));
        verify(awards).awardMissingContentBadges(anyMap(), argThat(eligible ->
                eligible.get(1L).equals(Set.of(BadgeCode.FIRST_POST, BadgeCode.POSTS_10))
                        && eligible.get(2L).equals(Set.of(BadgeCode.FIRST_COMMENT))));
        verify(awards, never()).awardIfMissing(1L, BadgeCode.FIRST_POST);
        verifyNoInteractions(users);
    }
}
