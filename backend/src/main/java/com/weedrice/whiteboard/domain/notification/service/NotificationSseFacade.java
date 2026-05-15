package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Transactional(readOnly = true)
class NotificationSseFacade {

    private final UserRepository userRepository;
    private final NotificationStreamService streamService;

    NotificationSseFacade(UserRepository userRepository,
                          NotificationStreamService streamService) {
        this.userRepository = userRepository;
        this.streamService = streamService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter subscribe(Long userId) {
        validateUserExists(userId);
        return streamService.subscribe(userId);
    }

    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeat() {
        streamService.sendHeartbeat();
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
