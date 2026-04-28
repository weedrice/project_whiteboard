package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginHistoryAuditService {

    private static final String UNKNOWN_IP_ADDRESS = "unknown";

    private final LoginHistoryRepository loginHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String loginId, String ipAddress, String userAgent, String failureReason) {
        loginHistoryRepository.save(LoginHistory.failure(
                loginId,
                ipAddress != null ? ipAddress : UNKNOWN_IP_ADDRESS,
                userAgent,
                failureReason));
    }
}
