package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.dto.ErrorLogResponse;
import com.weedrice.whiteboard.global.log.dto.ErrorLogStatsResponse;
import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import com.weedrice.whiteboard.global.log.repository.ErrorLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorLogServiceTest {

    @Mock
    private ErrorLogRepository errorLogRepository;

    @InjectMocks
    private ErrorLogService errorLogService;

    // ===== saveErrorLog 테스트 =====

    @Test
    @DisplayName("에러 로그 저장 성공 - Entity 직접 저장")
    void saveErrorLog_entity() {
        // given
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .errorType("NullPointerException")
                .httpStatus(500)
                .message("Error")
                .requestUri("/api/v1/test")
                .requestMethod("GET")
                .ipAddress(" 127.0.0.1, 10.0.0.1 ")
                .build();

        when(errorLogRepository.save(any(ErrorLog.class))).thenReturn(errorLog);

        // when
        errorLogService.saveErrorLog(errorLog);

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("에러 로그 저장 성공 - 파라미터 기반 빌더")
    void saveErrorLog_withParams() {
        // given
        when(errorLogRepository.save(any(ErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        errorLogService.saveErrorLog(
                "INTERNAL_SERVER_ERROR", "NullPointerException", 500,
                "Error message", "/api/v1/test", "GET",
                1L, "192.168.1.1", "Mozilla/5.0", "stack trace");

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());

        ErrorLog saved = captor.getValue();
        assertThat(saved.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(saved.getErrorType()).isEqualTo("NullPointerException");
        assertThat(saved.getHttpStatus()).isEqualTo(500);
        assertThat(saved.getMessage()).isEqualTo("Error message");
        assertThat(saved.getRequestUri()).isEqualTo("/api/v1/test");
        assertThat(saved.getRequestMethod()).isEqualTo("GET");
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getStackTrace()).isEqualTo("stack trace");
    }

    @Test
    @DisplayName("에러 로그 저장 - 500자 초과 message 자동 절삭")
    void saveErrorLog_truncatesLongMessage() {
        // given
        String longMessage = "a".repeat(600);
        when(errorLogRepository.save(any(ErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        errorLogService.saveErrorLog(
                "ERROR", "Exception", 500,
                longMessage, "/api/test", "GET",
                null, "127.0.0.1", null, null);

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).hasSize(500);
    }

    @Test
    @DisplayName("에러 로그 저장 - 500자 초과 requestUri 자동 절삭")
    void saveErrorLog_truncatesLongRequestUri() {
        // given
        String longUri = "/api/" + "x".repeat(600);
        when(errorLogRepository.save(any(ErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        errorLogService.saveErrorLog(
                "ERROR", "Exception", 400,
                "msg", longUri, "POST",
                null, "127.0.0.1", null, null);

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestUri()).hasSize(500);
    }

    @Test
    @DisplayName("에러 로그 저장 - 500자 초과 userAgent 자동 절삭")
    void saveErrorLog_truncatesLongUserAgent() {
        // given
        String longAgent = "Mozilla/" + "x".repeat(600);
        when(errorLogRepository.save(any(ErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        errorLogService.saveErrorLog(
                "ERROR", "Exception", 500,
                "msg", "/api/test", "GET",
                null, "127.0.0.1", longAgent, null);

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(500);
    }

    @Test
    @DisplayName("에러 로그 저장 - IP 주소 정규화")
    void saveErrorLog_normalizesIpAddress() {
        // given
        when(errorLogRepository.save(any(ErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        errorLogService.saveErrorLog(
                "ERROR", "Exception", 500,
                "msg", "/api/test", "GET",
                null, " 203.0.113.10, 10.0.0.1 ", null, null);

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("에러 로그 저장 - 45자 초과 IP 자동 절삭")
    void saveErrorLog_truncatesLongIpAddress() {
        // given
        when(errorLogRepository.save(any(ErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        errorLogService.saveErrorLog(
                "ERROR", "Exception", 500,
                "msg", "/api/test", "GET",
                null, "1".repeat(46), null, null);

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).hasSize(45);
    }

    @Test
    @DisplayName("에러 로그 저장 - null 값 허용 필드")
    void saveErrorLog_withNullFields() {
        // given
        when(errorLogRepository.save(any(ErrorLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        errorLogService.saveErrorLog(
                "ERROR", "Exception", 500,
                null, null, "GET",
                null, "127.0.0.1", null, null);

        // then
        ArgumentCaptor<ErrorLog> captor = ArgumentCaptor.forClass(ErrorLog.class);
        verify(errorLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isNull();
        assertThat(captor.getValue().getRequestUri()).isNull();
        assertThat(captor.getValue().getUserAgent()).isNull();
    }

    // ===== getErrorLogs 테스트 =====

    @Test
    @DisplayName("에러 로그 목록 조회 성공 - 권한 검증 포함")
    void getErrorLogs_success() {
        // given
        ErrorLogSearchRequest condition = new ErrorLogSearchRequest();
        Pageable pageable = PageRequest.of(0, 20);
        ErrorLogResponse.ErrorLogSummary log1 = ErrorLogResponse.ErrorLogSummary.builder()
                .errorCode("ERROR1").errorType("Type1").httpStatus(500)
                .message("msg1").requestUri("/test1").requestMethod("GET")
                .ipAddress("127.0.0.1").build();
        ErrorLogResponse.ErrorLogSummary log2 = ErrorLogResponse.ErrorLogSummary.builder()
                .errorCode("ERROR2").errorType("Type2").httpStatus(400)
                .message("msg2").requestUri("/test2").requestMethod("POST")
                .ipAddress("10.0.0.1").build();
        Page<ErrorLogResponse.ErrorLogSummary> page = new PageImpl<>(Arrays.asList(log1, log2));
        when(errorLogRepository.searchErrorLogs(condition, pageable)).thenReturn(page);

        // when
        Page<ErrorLogResponse.ErrorLogSummary> result = errorLogService.getErrorLogs(condition, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getErrorCode()).isEqualTo("ERROR1");
        assertThat(result.getContent().get(1).getErrorCode()).isEqualTo("ERROR2");
    }

    // ===== getErrorLog 테스트 =====

    @Test
    @DisplayName("에러 로그 상세 조회 성공")
    void getErrorLog_success() {
        // given
        Long errorLogId = 1L;
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("INTERNAL_SERVER_ERROR").errorType("NullPointerException")
                .httpStatus(500).message("Error").requestUri("/test")
                .requestMethod("GET").ipAddress("127.0.0.1")
                .stackTrace("stack trace here").build();
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.of(errorLog));

        // when
        ErrorLog result = errorLogService.getErrorLog(errorLogId);

        // then
        assertThat(result.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(result.getStackTrace()).isEqualTo("stack trace here");
    }

    @Test
    @DisplayName("getErrorLogDetail returns detail response")
    void getErrorLogDetail_success() {
        Long errorLogId = 1L;
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("INTERNAL_SERVER_ERROR").errorType("NullPointerException")
                .httpStatus(500).message("Error").requestUri("/test")
                .requestMethod("GET").ipAddress("127.0.0.1")
                .stackTrace("stack trace here").build();
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.of(errorLog));

        ErrorLogResponse.ErrorLogDetail detail = errorLogService.getErrorLogDetail(errorLogId);

        assertThat(detail.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(detail.getStackTrace()).isEqualTo("stack trace here");
    }

    @Test
    @DisplayName("에러 로그 상세 조회 - 존재하지 않는 ID")
    void getErrorLog_notFound() {
        // given
        Long errorLogId = 999L;
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> errorLogService.getErrorLog(errorLogId))
                .isInstanceOf(BusinessException.class);
    }

    // ===== resolveErrorLog 테스트 =====

    @Test
    @DisplayName("에러 로그 확인 처리 성공 - memo 포함")
    void resolveErrorLog_withMemo() {
        // given
        Long errorLogId = 1L;
        Long adminUserId = 100L;
        String memo = "확인 완료";
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("ERROR").errorType("Type").httpStatus(500)
                .message("msg").requestUri("/test").requestMethod("GET")
                .ipAddress("127.0.0.1").build();
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.of(errorLog));

        // when
        errorLogService.resolveErrorLog(errorLogId, adminUserId, memo);

        // then
        assertThat(errorLog.getIsResolved()).isEqualTo("Y");
        assertThat(errorLog.getResolvedBy()).isEqualTo(adminUserId);
        assertThat(errorLog.getResolvedAt()).isNotNull();
        assertThat(errorLog.getResolvedMemo()).isEqualTo(memo);
    }

    @Test
    @DisplayName("에러 로그 확인 처리 - memo 앞뒤 공백 제거")
    void resolveErrorLog_trimsMemo() {
        // given
        Long errorLogId = 1L;
        Long adminUserId = 100L;
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("ERROR").errorType("Type").httpStatus(500)
                .message("msg").requestUri("/test").requestMethod("GET")
                .ipAddress("127.0.0.1").build();
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.of(errorLog));

        // when
        errorLogService.resolveErrorLog(errorLogId, adminUserId, " 확인 완료 ");

        // then
        assertThat(errorLog.getResolvedMemo()).isEqualTo("확인 완료");
    }

    @Test
    @DisplayName("에러 로그 확인 처리 - 공백 memo는 null로 정규화")
    void resolveErrorLog_blankMemoToNull() {
        // given
        Long errorLogId = 1L;
        Long adminUserId = 100L;
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("ERROR").errorType("Type").httpStatus(500)
                .message("msg").requestUri("/test").requestMethod("GET")
                .ipAddress("127.0.0.1").build();
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.of(errorLog));

        // when
        errorLogService.resolveErrorLog(errorLogId, adminUserId, "   ");

        // then
        assertThat(errorLog.getResolvedMemo()).isNull();
    }

    @Test
    @DisplayName("에러 로그 확인 처리 - 500자 초과 memo 거부")
    void resolveErrorLog_rejectsTooLongMemo() {
        // when & then
        assertThatThrownBy(() -> errorLogService.resolveErrorLog(1L, 100L, "a".repeat(501)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(errorLogRepository, never()).findById(any());
    }

    @Test
    @DisplayName("에러 로그 확인 처리 성공 - memo 없이")
    void resolveErrorLog_withoutMemo() {
        // given
        Long errorLogId = 2L;
        Long adminUserId = 100L;
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("ERROR").errorType("Type").httpStatus(400)
                .message("msg").requestUri("/test").requestMethod("POST")
                .ipAddress("10.0.0.1").build();
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.of(errorLog));

        // when
        errorLogService.resolveErrorLog(errorLogId, adminUserId, null);

        // then
        assertThat(errorLog.getIsResolved()).isEqualTo("Y");
        assertThat(errorLog.getResolvedBy()).isEqualTo(adminUserId);
        assertThat(errorLog.getResolvedMemo()).isNull();
    }

    @Test
    @DisplayName("에러 로그 확인 처리 - 존재하지 않는 ID")
    void resolveErrorLog_notFound() {
        // given
        Long errorLogId = 999L;
        when(errorLogRepository.findById(errorLogId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> errorLogService.resolveErrorLog(errorLogId, 1L, null))
                .isInstanceOf(BusinessException.class);
    }

    // ===== getErrorLogStats 테스트 =====

    @Test
    @DisplayName("에러 로그 통계 조회 성공")
    void getErrorLogStats_success() {
        // given
        when(errorLogRepository.count()).thenReturn(100L);
        when(errorLogRepository.countByIsResolved("N")).thenReturn(30L);
        when(errorLogRepository.countByIsResolved("Y")).thenReturn(70L);

        // when
        ErrorLogStatsResponse stats = errorLogService.getErrorLogStats();

        // then
        assertThat(stats.getTotalCount()).isEqualTo(100L);
        assertThat(stats.getUnresolvedCount()).isEqualTo(30L);
        assertThat(stats.getResolvedCount()).isEqualTo(70L);
    }

    @Test
    @DisplayName("에러 로그 통계 조회 - 빈 데이터")
    void getErrorLogStats_empty() {
        // given
        when(errorLogRepository.count()).thenReturn(0L);
        when(errorLogRepository.countByIsResolved("N")).thenReturn(0L);
        when(errorLogRepository.countByIsResolved("Y")).thenReturn(0L);

        // when
        ErrorLogStatsResponse stats = errorLogService.getErrorLogStats();

        // then
        assertThat(stats.getTotalCount()).isEqualTo(0L);
        assertThat(stats.getUnresolvedCount()).isEqualTo(0L);
        assertThat(stats.getResolvedCount()).isEqualTo(0L);
    }
}
