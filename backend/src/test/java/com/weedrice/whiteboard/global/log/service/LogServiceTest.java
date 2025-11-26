package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.log.entity.Log;
import com.weedrice.whiteboard.global.log.repository.LogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @InjectMocks
    private LogService logService;

    @Test
    @DisplayName("로그 저장 성공")
    void saveLog_success() {
        // given
        Long userId = 1L;
        String actionType = "TEST_ACTION";
        String ipAddress = "127.0.0.1";
        String details = "Test Details";

        // when
        logService.saveLog(userId, actionType, ipAddress, details);

        // then
        verify(logRepository).save(any(Log.class));
    }
}
