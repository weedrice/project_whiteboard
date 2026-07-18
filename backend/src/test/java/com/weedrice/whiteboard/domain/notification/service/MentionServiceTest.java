package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MentionServiceTest {

    @Mock UserRepository users;
    @Mock UserBlockRepository blocks;
    @Mock ApplicationEventPublisher events;
    @Mock PostRepository posts;
    @Mock CommentRepository comments;
    @Mock AdminRepository admins;
    @Mock PostAccessPolicy postAccessPolicy;
    MentionService service;

    @BeforeEach
    void setUp() {
        service = new MentionService(users, blocks, events, posts, comments, admins, postAccessPolicy);
    }

    @Test
    void blankCandidateQueryIsEmptyAndBlockedIdsAreExcluded() {
        assertEquals(List.of(), service.findCandidates(1L, " "));
        assertEquals(List.of(), service.findCandidates(1L, "a"));
        assertEquals(List.of(), service.findCandidates(null, "candidate"));
        User candidate = user(2L, "Candidate", User.STATUS_ACTIVE);
        when(blocks.findBlockedUserIdsEitherDirectionByUserId(1L)).thenReturn(List.of(9L));
        when(users.findMentionCandidates(eq("can"), eq(false), eq(List.of(9L)), any(Pageable.class)))
                .thenReturn(List.of(candidate));

        assertEquals(2L, service.findCandidates(1L, " can ").getFirst().getUserId());
    }

    @Test
    void htmlMentionsAreDeduplicatedFilteredAndPublished() {
        User actor = user(1L, "Actor", User.STATUS_ACTIVE);
        User recipient = user(2L, "Recipient", User.STATUS_ACTIVE);
        User inactive = user(3L, "Inactive", "SUSPENDED");
        Post sourcePost = post(10L);
        when(posts.findByIdWithRelations(10L)).thenReturn(java.util.Optional.of(sourcePost));
        when(users.findAllById(any())).thenReturn(List.of(actor, recipient, inactive));
        when(blocks.findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L))).thenReturn(List.of());
        when(postAccessPolicy.isReadable(sourcePost, recipient, false, java.util.Set.of())).thenReturn(true);

        service.publishMentions(actor, null, NotificationSourceType.POST, 10L,
                "<span data-mention-user-id='2'></span><i data-mention-user-id='bad'></i>"
                        + "<b data-mention-user-id='2'></b><span data-mention-user-id='3'></span>");

        verify(events).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void invalidInputsAndBlockedRecipientsDoNotPublish() {
        User actor = user(1L, "Actor", User.STATUS_ACTIVE);
        service.publishMentions(null, null, NotificationSourceType.POST, 1L, "<span data-mention-user-id='2'>");
        service.publishMentions(actor, null, NotificationSourceType.POST, 1L, List.of());
        service.publishMentions(actor, null, NotificationSourceType.POST, 1L, Arrays.asList(-1L, null));

        User blocked = user(2L, "Blocked", User.STATUS_ACTIVE);
        Post sourcePost = post(1L);
        when(posts.findByIdWithRelations(1L)).thenReturn(java.util.Optional.of(sourcePost));
        when(users.findAllById(any())).thenReturn(List.of(blocked));
        when(blocks.findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L))).thenReturn(List.of(2L));
        service.publishMentions(actor, null, NotificationSourceType.POST, 1L, List.of(2L));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void unreadableSourceDoesNotPublishMention() {
        User actor = user(1L, "Actor", User.STATUS_ACTIVE);
        User recipient = user(2L, "Recipient", User.STATUS_ACTIVE);
        Post secretPost = post(10L);
        when(posts.findByIdWithRelations(10L)).thenReturn(java.util.Optional.of(secretPost));
        when(users.findAllById(any())).thenReturn(List.of(recipient));
        when(blocks.findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L))).thenReturn(List.of());
        when(postAccessPolicy.isReadable(secretPost, recipient, false, java.util.Set.of())).thenReturn(false);

        service.publishMentions(actor, null, NotificationSourceType.POST, 10L, List.of(2L));

        verify(events, never()).publishEvent(any());
    }

    private static User user(Long id, String name, String status) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        when(user.getDisplayName()).thenReturn(name);
        when(user.getStatus()).thenReturn(status);
        return user;
    }

    private static Post post(Long id) {
        Post post = mock(Post.class);
        when(post.getPostId()).thenReturn(id);
        return post;
    }
}
