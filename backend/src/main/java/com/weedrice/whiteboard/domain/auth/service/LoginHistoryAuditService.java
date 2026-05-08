package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginHistoryAuditService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId, String loginId, String ipAddress, String userAgent) {
        User user = entityManager.getReference(User.class, userId);
        loginHistoryRepository.save(LoginHistory.success(
                user,
                loginId,
                LoginClientMetadataNormalizer.normalizeIpAddress(ipAddress),
                LoginClientMetadataNormalizer.normalizeUserAgent(userAgent)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String loginId, String ipAddress, String userAgent, String failureReason) {
        loginHistoryRepository.save(LoginHistory.failure(
                loginId,
                LoginClientMetadataNormalizer.normalizeIpAddress(ipAddress),
                LoginClientMetadataNormalizer.normalizeUserAgent(userAgent),
                failureReason));
    }
}
