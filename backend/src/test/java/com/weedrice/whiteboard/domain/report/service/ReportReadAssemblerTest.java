package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportReadAssemblerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private ReportReadAssembler reportReadAssembler;

    @Test
    @DisplayName("toAdminResponsePage populates user target metadata")
    void toAdminResponsePage_populatesUserTargetMetadata() {
        User reporter = User.builder().displayName("Reporter").build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);

        Report report = Report.builder()
                .reporter(reporter)
                .targetType("USER")
                .targetId(30L)
                .reasonType("ABUSE")
                .build();
        ReflectionTestUtils.setField(report, "reportId", 2L);

        Page<Report> page = new PageImpl<>(List.of(report), PageRequest.of(0, 10), 1);
        when(userRepository.findReportTargetMetadataByUserIds(argThat(ids -> containsExactlyIds(ids, 30L))))
                .thenReturn(List.of(userMetadata(30L, 30L, "Target User", "target-user")));

        Page<ReportResponse> result = reportReadAssembler.toAdminResponsePage(page);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTargetUserId()).isEqualTo(30L);
        assertThat(result.getContent().get(0).getTargetDisplayName()).isEqualTo("Target User");
        assertThat(result.getContent().get(0).getTargetLoginId()).isEqualTo("target-user");
    }

    @Test
    @DisplayName("toAdminResponsePage includes target author metadata for post and comment")
    void toAdminResponsePage_includesTargetAuthorMetadata() {
        User reporter = User.builder().displayName("Reporter").build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);

        Report postReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(100L)
                .reasonType("SPAM")
                .build();
        Report commentReport = Report.builder()
                .reporter(reporter)
                .targetType("COMMENT")
                .targetId(200L)
                .reasonType("ABUSE")
                .build();
        ReflectionTestUtils.setField(postReport, "reportId", 3L);
        ReflectionTestUtils.setField(commentReport, "reportId", 4L);

        Page<Report> page = new PageImpl<>(List.of(postReport, commentReport), PageRequest.of(0, 10), 2);
        when(postRepository.findReportTargetMetadataByPostIds(List.of(100L)))
                .thenReturn(List.of(postMetadata(100L, 44L, "Post Author", "post-author")));
        when(commentRepository.findReportTargetMetadataByCommentIds(List.of(200L)))
                .thenReturn(List.of(commentMetadata(200L, 55L, "Comment Author", "comment-author")));
        Page<ReportResponse> result = reportReadAssembler.toAdminResponsePage(page);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getTargetUserId()).isEqualTo(44L);
        assertThat(result.getContent().get(0).getTargetDisplayName()).isEqualTo("Post Author");
        assertThat(result.getContent().get(0).getTargetLoginId()).isEqualTo("post-author");
        assertThat(result.getContent().get(1).getTargetUserId()).isEqualTo(55L);
        assertThat(result.getContent().get(1).getTargetDisplayName()).isEqualTo("Comment Author");
        assertThat(result.getContent().get(1).getTargetLoginId()).isEqualTo("comment-author");
    }

    @Test
    @DisplayName("toMyReportResponsePage populates target author display names")
    void toMyReportResponsePage_populatesTargetAuthorDisplayNames() {
        User reporter = User.builder().displayName("Reporter").build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);

        Report userReport = Report.builder()
                .reporter(reporter)
                .targetType("USER")
                .targetId(30L)
                .reasonType("ABUSE")
                .build();
        Report postReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(40L)
                .reasonType("SPAM")
                .build();
        Report commentReport = Report.builder()
                .reporter(reporter)
                .targetType("COMMENT")
                .targetId(50L)
                .reasonType("ABUSE")
                .build();
        ReflectionTestUtils.setField(userReport, "reportId", 5L);
        ReflectionTestUtils.setField(postReport, "reportId", 6L);
        ReflectionTestUtils.setField(commentReport, "reportId", 7L);

        Page<Report> page = new PageImpl<>(List.of(userReport, postReport, commentReport), PageRequest.of(0, 10), 3);
        when(postRepository.findReportTargetMetadataByPostIds(List.of(40L)))
                .thenReturn(List.of(postMetadata(40L, 44L, "Post Author", "post-author")));
        when(commentRepository.findReportTargetMetadataByCommentIds(List.of(50L)))
                .thenReturn(List.of(commentMetadata(50L, 55L, "Comment Author", "comment-author")));
        when(userRepository.findReportTargetMetadataByUserIds(argThat(ids -> containsExactlyIds(ids, 30L))))
                .thenReturn(List.of(userMetadata(30L, 30L, "Target User", "target-user")));

        Page<MyReportResponse> result = reportReadAssembler.toMyReportResponsePage(page);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getTargetDisplayName()).isEqualTo("Target User");
        assertThat(result.getContent().get(1).getTargetDisplayName()).isEqualTo("Post Author");
        assertThat(result.getContent().get(2).getTargetDisplayName()).isEqualTo("Comment Author");
    }

    private boolean containsExactlyIds(Iterable<Long> actualIds, Long... expectedIds) {
        if (actualIds == null) {
            return false;
        }
        Set<Long> actual = new HashSet<>();
        actualIds.forEach(actual::add);
        return actual.equals(Set.of(expectedIds));
    }

    private UserRepository.ReportTargetMetadataProjection userMetadata(
            Long targetId,
            Long targetUserId,
            String targetDisplayName,
            String targetLoginId) {
        return new UserRepository.ReportTargetMetadataProjection() {
            @Override
            public Long getTargetId() {
                return targetId;
            }

            @Override
            public Long getTargetUserId() {
                return targetUserId;
            }

            @Override
            public String getTargetDisplayName() {
                return targetDisplayName;
            }

            @Override
            public String getTargetLoginId() {
                return targetLoginId;
            }
        };
    }

    private PostRepository.ReportTargetMetadataProjection postMetadata(
            Long targetId,
            Long targetUserId,
            String targetDisplayName,
            String targetLoginId) {
        return new PostRepository.ReportTargetMetadataProjection() {
            @Override
            public Long getTargetId() {
                return targetId;
            }

            @Override
            public Long getTargetUserId() {
                return targetUserId;
            }

            @Override
            public String getTargetDisplayName() {
                return targetDisplayName;
            }

            @Override
            public String getTargetLoginId() {
                return targetLoginId;
            }
        };
    }

    private CommentRepository.ReportTargetMetadataProjection commentMetadata(
            Long targetId,
            Long targetUserId,
            String targetDisplayName,
            String targetLoginId) {
        return new CommentRepository.ReportTargetMetadataProjection() {
            @Override
            public Long getTargetId() {
                return targetId;
            }

            @Override
            public Long getTargetUserId() {
                return targetUserId;
            }

            @Override
            public String getTargetDisplayName() {
                return targetDisplayName;
            }

            @Override
            public String getTargetLoginId() {
                return targetLoginId;
            }
        };
    }
}
