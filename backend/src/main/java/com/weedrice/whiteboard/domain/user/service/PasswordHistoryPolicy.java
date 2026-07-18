package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PasswordHistoryPolicy {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public void validateNotRecentlyUsed(User user, String rawPassword) {
        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
        }

        List<PasswordHistory> recentHistories = passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user);
        int checkedPreviousPasswords = 0;
        for (PasswordHistory history : recentHistories) {
            if (Objects.equals(user.getPassword(), history.getPasswordHash())) {
                continue;
            }
            if (passwordEncoder.matches(rawPassword, history.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
            }
            checkedPreviousPasswords++;
            if (checkedPreviousPasswords == 3) {
                break;
            }
        }
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public void record(User user, String passwordHash) {
        passwordHistoryRepository.save(PasswordHistory.builder()
                .user(user)
                .passwordHash(passwordHash)
                .build());
        passwordHistoryRepository.flush();
        passwordHistoryRepository.deleteOlderThanLatestFour(user.getUserId());
    }
}
