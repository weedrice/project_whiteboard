package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportAutoBlindServiceTest {

    @Mock ReportRepository reports;
    @Mock PostRepository posts;
    @Mock CommentRepository comments;
    @Mock ModerationAuditLogService audits;
    @Mock GlobalConfigService configs;
    ReportAutoBlindService service;

    @BeforeEach
    void setUp() {
        service = new ReportAutoBlindService(reports, posts, comments, audits, configs,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void ignoresUserAndCountsBelowThreshold() {
        service.applyIfThresholdReached("USER", 1L);
        when(reports.countByTargetTypeAndTargetIdAndStatus("POST", 2L, Report.STATUS_PENDING)).thenReturn(4L);
        when(configs.getConfig("REPORT_AUTO_BLIND_THRESHOLD")).thenReturn(null);
        service.applyIfThresholdReached("POST", 2L);
        verify(posts, never()).findByIdWithRelationsForBlindUpdate(2L);
    }

    @Test
    void blindsEligiblePostOnce() {
        Post post = mock(Post.class);
        Board board = mock(Board.class);
        when(reports.countByTargetTypeAndTargetIdAndStatus("POST", 3L, Report.STATUS_PENDING)).thenReturn(5L);
        when(posts.findByIdWithRelationsForBlindUpdate(3L)).thenReturn(Optional.of(post));
        when(post.getPostId()).thenReturn(3L);
        when(post.getBoard()).thenReturn(board);
        when(post.getIsDeleted()).thenReturn(false);
        when(post.getIsBlinded()).thenReturn(false);

        service.applyIfThresholdReached("POST", 3L);

        verify(post).blind("AUTO_REPORT", LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(audits).recordSystemAction(ModerationAuditLogService.ACTION_POST_AUTO_BLIND,
                ModerationAuditLogService.TARGET_TYPE_POST, 3L, board, "AUTO_REPORT");
    }

    @Test
    void blindsEligibleCommentAndSkipsAlreadyBlindedTarget() {
        Comment comment = mock(Comment.class);
        Post post = mock(Post.class);
        Board board = mock(Board.class);
        when(reports.countByTargetTypeAndTargetIdAndStatus("COMMENT", 4L, Report.STATUS_PENDING)).thenReturn(5L);
        when(comments.findByIdWithRelationsForBlindUpdate(4L)).thenReturn(Optional.of(comment));
        when(comment.getCommentId()).thenReturn(4L);
        when(comment.getPost()).thenReturn(post);
        when(post.getBoard()).thenReturn(board);
        when(comment.getIsDeleted()).thenReturn(false);
        when(comment.getIsBlinded()).thenReturn(false);

        service.applyIfThresholdReached("COMMENT", 4L);
        verify(comment).blind("AUTO_REPORT", LocalDateTime.of(2026, 1, 1, 0, 0));

        when(reports.countByTargetTypeAndTargetIdAndStatus("POST", 5L, Report.STATUS_PENDING)).thenReturn(5L);
        Post blinded = mock(Post.class);
        when(posts.findByIdWithRelationsForBlindUpdate(5L)).thenReturn(Optional.of(blinded));
        when(blinded.getIsBlinded()).thenReturn(true);
        service.applyIfThresholdReached("POST", 5L);
        verify(blinded, never()).blind(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
