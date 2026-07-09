package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardVisitRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardVisitServiceTest {

    @Mock
    private BoardVisitRepository boardVisitRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BoardRepository boardRepository;

    @Test
    void getActivitySummaries_passesThirtyDayRecentCutoff() {
        BoardVisitService service = new BoardVisitService(boardVisitRepository, userRepository, boardRepository);
        User user = User.builder()
                .loginId("reader")
                .email("reader@test.com")
                .password("password")
                .displayName("reader")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(boardVisitRepository.findActivityByBoardIds(any(), anyLong(), anyBoolean(), any()))
                .thenReturn(List.of());

        service.getActivitySummaries(List.of(10L), user);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(boardVisitRepository).findActivityByBoardIds(
                eq(List.of(10L)),
                eq(1L),
                eq(false),
                cutoffCaptor.capture());
        long secondsFromExpected = Math.abs(ChronoUnit.SECONDS.between(
                DateTimeUtils.nowKST().minusDays(30),
                cutoffCaptor.getValue()));
        assertThat(secondsFromExpected).isLessThan(5L);
    }
}
