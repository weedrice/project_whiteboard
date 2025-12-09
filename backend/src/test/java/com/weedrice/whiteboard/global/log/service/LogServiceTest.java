package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.log.entity.Log;
import com.weedrice.whiteboard.global.log.repository.LogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @InjectMocks
    private LogService logService;

    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        securityUtilsMockedStatic = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMockedStatic.close();
    }

    @Test
    @DisplayName("로그 저장 성공 (Entity)")
    void saveLog_entity() {
        Log log = Log.builder().userId(1L).actionType("TEST").build();
        when(logRepository.save(any(Log.class))).thenReturn(log);

        logService.saveLog(log);

        verify(logRepository).save(log);
    }

    @Test
    @DisplayName("로그 저장 성공 (Params)")
    void saveLog_params() {
        when(logRepository.save(any(Log.class))).thenAnswer(i -> i.getArgument(0));

        logService.saveLog(1L, "TEST", "127.0.0.1", "details");

        verify(logRepository).save(any(Log.class));
    }

    @Test
    @DisplayName("로그 조회 성공 - 권한 검증 포함")
    void getLogs_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Log> page = new PageImpl<>(Collections.emptyList());
        when(logRepository.findAll(pageable)).thenReturn(page);

        Page<Log> result = logService.getLogs(pageable);

        assertThat(result).isEqualTo(page);
        securityUtilsMockedStatic.verify(SecurityUtils::validateSuperAdminPermission);
    }
}