package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ErrorLogRepositoryCustom {

    Page<ErrorLog> searchErrorLogs(ErrorLogSearchRequest condition, Pageable pageable);
}
