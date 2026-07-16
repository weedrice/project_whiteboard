package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentLikeCommand {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;

    public CommentLikeResult like(User user, Comment comment, DuplicatePolicy duplicatePolicy) {
        Long commentId = comment.getCommentId();
        boolean alreadyLiked = commentLikeRepository.insertIgnore(user.getUserId(), commentId) == 0;
        if (alreadyLiked && duplicatePolicy == DuplicatePolicy.THROW_ALREADY_LIKED) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        if (!alreadyLiked) {
            incrementCommentLikeCount(commentId);
        }

        return new CommentLikeResult(resolveCommentLikeCount(commentId), alreadyLiked);
    }

    private void incrementCommentLikeCount(Long commentId) {
        if (commentRepository.incrementLikeCount(commentId) == 0) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private int resolveCommentLikeCount(Long commentId) {
        Integer likeCount = commentRepository.findLikeCountByCommentId(commentId);
        if (likeCount == null) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return likeCount;
    }

    public enum DuplicatePolicy {
        THROW_ALREADY_LIKED,
        RETURN_ALREADY_LIKED
    }

    public record CommentLikeResult(int likeCount, boolean alreadyLiked) {
    }
}
