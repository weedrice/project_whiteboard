package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.AdminUserDetailQueryRepository.AdminUserDetailStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserDetailQueryRepositoryTest {

    @InjectMocks
    private AdminUserDetailQueryRepository repository;

    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private SanctionRepository sanctionRepository;
    @Mock private ReportRepository reportRepository;

    @Test
    @DisplayName("findStats returns delegated count and recent values")
    void findStats_returnsDelegatedValues() {
        User user = User.builder()
                .loginId("target")
                .email("target@test.com")
                .password("pw")
                .displayName("target")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        LoginHistory recentLogin = LoginHistory.builder().user(user).ipAddress("127.0.0.1").userAgent("agent").build();
        Sanction recentSanction = Sanction.builder().targetUser(user).type("BAN").remark("reason").build();

        when(postRepository.countByUserAndIsDeleted(user, false)).thenReturn(1L);
        when(commentRepository.countByUserAndIsDeleted(user, false)).thenReturn(2L);
        when(boardSubscriptionRepository.countByUser(user)).thenReturn(3L);
        when(loginHistoryRepository.findTopByUserAndIsSuccessTrueOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(recentLogin));
        when(sanctionRepository.countByTargetUser(user)).thenReturn(4L);
        when(sanctionRepository.findTopByTargetUserOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(recentSanction));
        when(reportRepository.countByTargetTypeAndTargetId("USER", 1L)).thenReturn(5L);
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus("USER", 1L, "PENDING")).thenReturn(6L);

        AdminUserDetailStats stats = repository.findStats(user);

        assertThat(stats.postCount()).isEqualTo(1L);
        assertThat(stats.commentCount()).isEqualTo(2L);
        assertThat(stats.subscriptionCount()).isEqualTo(3L);
        assertThat(stats.recentLogin()).isSameAs(recentLogin);
        assertThat(stats.sanctionCount()).isEqualTo(4L);
        assertThat(stats.recentSanction()).isSameAs(recentSanction);
        assertThat(stats.reportTotalCount()).isEqualTo(5L);
        assertThat(stats.reportPendingCount()).isEqualTo(6L);
    }
}
