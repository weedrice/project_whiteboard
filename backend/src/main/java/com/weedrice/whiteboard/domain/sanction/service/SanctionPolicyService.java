package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SanctionPolicyService {

    private static final String TYPE_MUTE = "MUTE";

    private final SanctionRepository sanctionRepository;
    private final Clock clock;

    public boolean isUserBanned(User user) {
        return user != null && sanctionRepository.existsActiveBan(user, LocalDateTime.now(clock));
    }

    public boolean isUserMuted(User user) {
        return user != null && sanctionRepository.existsActiveTypeIn(user, Set.of(TYPE_MUTE), LocalDateTime.now(clock));
    }

    public void validateNotBanned(User user) {
        if (user == null || !"ACTIVE".equals(user.getStatus()) || isUserBanned(user)) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }

    public void validateNotMuted(User user) {
        if (user == null || isUserMuted(user)) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }
}
