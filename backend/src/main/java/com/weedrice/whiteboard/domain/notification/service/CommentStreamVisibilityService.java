package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentStreamVisibilityService {
    private final CommentRepository commentRepository;
    private final ContentAuthorVisibilityRepository authorVisibilityRepository;
    private final UserBlockRepository userBlockRepository;

    // Dispatch runs after the source transaction commits; always read current visibility in a fresh transaction.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Set<Long> visibleRecipients(CommentStreamEvent event, Collection<Long> recipientIds) {
        if (event.getCommentId() == null || event.getActorUserId() == null || recipientIds.isEmpty()) {
            return Set.of();
        }
        Comment comment = commentRepository.findById(event.getCommentId()).orElse(null);
        if (comment == null || comment.getUserId() == null
                || !Objects.equals(comment.getPostId(), event.getPostId())
                || Boolean.TRUE.equals(comment.getIsDeleted()) || Boolean.TRUE.equals(comment.getIsBlinded())) {
            return Set.of();
        }
        Set<Long> authorIds = new HashSet<>(List.of(comment.getUserId(), event.getActorUserId()));
        if (!authorVisibilityRepository.findVisibleUserIds(authorIds).containsAll(authorIds)) {
            return Set.of();
        }
        Set<Long> visible = new HashSet<>(recipientIds);
        List<Long> candidates = List.copyOf(recipientIds);
        for (Long authorId : authorIds) {
            visible.removeAll(userBlockRepository.findBlockedCandidateUserIdsEitherDirection(authorId, candidates));
        }
        return Set.copyOf(visible);
    }
}
