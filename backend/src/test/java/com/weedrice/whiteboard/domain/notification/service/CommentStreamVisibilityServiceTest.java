package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommentStreamVisibilityServiceTest {
    private final CommentRepository comments = mock(CommentRepository.class);
    private final ContentAuthorVisibilityRepository authors = mock(ContentAuthorVisibilityRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final CommentStreamVisibilityService service = new CommentStreamVisibilityService(comments, authors, blocks);
    private final CommentStreamEvent event = CommentStreamEvent.builder()
            .action("CREATED").postId(10L).commentId(20L).actorUserId(7L).build();
    private Comment comment;

    @BeforeEach
    void fixture() {
        comment = Comment.builder().userId(7L).content("text").build();
        ReflectionTestUtils.setField(comment, "postId", 10L);
        when(comments.findById(20L)).thenReturn(Optional.of(comment));
        when(authors.findVisibleUserIds(Set.of(7L))).thenReturn(Set.of(7L));
    }

    @Test
    void excludesBothDirectionsOfBlockBeforeSendingAnyIdentity() {
        // Repository query returns either-direction matches in a single batch.
        when(blocks.findBlockedCandidateUserIdsEitherDirection(7L, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 3L));
        assertThat(service.visibleRecipients(event, List.of(1L, 2L, 3L))).containsExactly(2L);
    }

    @Test
    void rejectsBannedDeletedOrInactiveAuthorViaSharedPolicy() {
        when(authors.findVisibleUserIds(Set.of(7L))).thenReturn(Set.of());
        assertThat(service.visibleRecipients(event, List.of(1L))).isEmpty();
        verifyNoInteractions(blocks);
    }

    @Test
    void requiresActualAuthorVisibilityEvenWhenActorIsVisibleModerator() {
        ReflectionTestUtils.setField(comment, "userId", 8L);
        when(authors.findVisibleUserIds(Set.of(7L, 8L))).thenReturn(Set.of(7L));
        assertThat(service.visibleRecipients(event, List.of(1L))).isEmpty();
    }

    @Test
    void checksBlocksAgainstBothAuthorAndModerator() {
        ReflectionTestUtils.setField(comment, "userId", 8L);
        when(authors.findVisibleUserIds(Set.of(7L, 8L))).thenReturn(Set.of(7L, 8L));
        when(blocks.findBlockedCandidateUserIdsEitherDirection(8L, List.of(1L))).thenReturn(List.of(1L));
        assertThat(service.visibleRecipients(event, List.of(1L))).isEmpty();
    }

    @Test
    void rejectsMissingDeletedBlindedOrMovedComments() {
        when(comments.findById(20L)).thenReturn(Optional.empty());
        assertThat(service.visibleRecipients(event, List.of(1L))).isEmpty();
        when(comments.findById(20L)).thenReturn(Optional.of(comment));
        ReflectionTestUtils.setField(comment, "isDeleted", true);
        assertThat(service.visibleRecipients(event, List.of(1L))).isEmpty();
        ReflectionTestUtils.setField(comment, "isDeleted", false);
        ReflectionTestUtils.setField(comment, "isBlinded", true);
        assertThat(service.visibleRecipients(event, List.of(1L))).isEmpty();
        ReflectionTestUtils.setField(comment, "isBlinded", false);
        ReflectionTestUtils.setField(comment, "postId", 99L);
        assertThat(service.visibleRecipients(event, List.of(1L))).isEmpty();
        verifyNoInteractions(authors, blocks);
    }

    @Test
    void opensFreshReadTransactionAfterCommit() throws Exception {
        var annotation = CommentStreamVisibilityService.class
                .getMethod("visibleRecipients", CommentStreamEvent.class, Collection.class)
                .getAnnotation(Transactional.class);
        assertThat(annotation.readOnly()).isTrue();
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
