package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryPolicyTest {

    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("rejects reuse from the previous three passwords excluding current history")
    void validateNotRecentlyUsed_rejectsThreePreviousPasswordsExcludingCurrentHistory() {
        PasswordHistoryPolicy policy = new PasswordHistoryPolicy(passwordHistoryRepository, passwordEncoder);
        User user = User.builder().password("encodedCurrent").build();
        PasswordHistory current = PasswordHistory.builder().passwordHash("encodedCurrent").build();
        PasswordHistory previous1 = PasswordHistory.builder().passwordHash("encodedPrevious1").build();
        PasswordHistory previous2 = PasswordHistory.builder().passwordHash("encodedPrevious2").build();
        PasswordHistory previous3 = PasswordHistory.builder().passwordHash("encodedPrevious3").build();

        when(passwordEncoder.matches("rawPrevious3", "encodedCurrent")).thenReturn(false);
        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user))
                .thenReturn(List.of(current, previous1, previous2, previous3));
        when(passwordEncoder.matches("rawPrevious3", "encodedPrevious1")).thenReturn(false);
        when(passwordEncoder.matches("rawPrevious3", "encodedPrevious2")).thenReturn(false);
        when(passwordEncoder.matches("rawPrevious3", "encodedPrevious3")).thenReturn(true);

        assertThatThrownBy(() -> policy.validateNotRecentlyUsed(user, "rawPrevious3"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);
    }

    @Test
    @DisplayName("rejects current password reuse before history lookup")
    void validateNotRecentlyUsed_rejectsCurrentPasswordBeforeHistoryLookup() {
        PasswordHistoryPolicy policy = new PasswordHistoryPolicy(passwordHistoryRepository, passwordEncoder);
        User user = User.builder().password("encodedCurrent").build();

        when(passwordEncoder.matches("rawCurrent", "encodedCurrent")).thenReturn(true);

        assertThatThrownBy(() -> policy.validateNotRecentlyUsed(user, "rawCurrent"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);
        verify(passwordHistoryRepository, never()).findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user);
    }

    @Test
    @DisplayName("새 비밀번호 이력을 저장한 뒤 최신 네 건만 남긴다")
    void record_prunesHistoryAfterFlush() {
        PasswordHistoryPolicy policy = new PasswordHistoryPolicy(passwordHistoryRepository, passwordEncoder);
        User user = User.builder().password("encodedCurrent").build();
        ReflectionTestUtils.setField(user, "userId", 7L);

        policy.record(user, "encodedNext");

        verify(passwordHistoryRepository).save(org.mockito.ArgumentMatchers.any(PasswordHistory.class));
        verify(passwordHistoryRepository).flush();
        verify(passwordHistoryRepository).deleteOlderThanLatestFour(7L);
    }
}
