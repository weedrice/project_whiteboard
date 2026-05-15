package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.dto.ErrorLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ErrorLogRepositoryCustom {

    Page<ErrorLogResponse.ErrorLogSummary> searchErrorLogs(ErrorLogSearchRequest condition, Pageable pageable);
}
