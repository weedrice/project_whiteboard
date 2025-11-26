package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.log.entity.Log;
import com.weedrice.whiteboard.global.log.repository.LogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @InjectMocks
    private LogService logService;

    @Test
    @DisplayName("활동 로그 조회 성공")
    void getLogs_success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Log log = Log.builder().actionType("TEST").build();
        Page<Log> logPage = new PageImpl<>(Collections.singletonList(log), pageable, 1);

        when(logRepository.findAll(pageable)).thenReturn(logPage);

        // when
        Page<Log> result = logService.getLogs(pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getActionType()).isEqualTo("TEST");
    }
}
